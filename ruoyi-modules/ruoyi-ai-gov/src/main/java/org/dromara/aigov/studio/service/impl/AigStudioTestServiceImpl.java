package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.studio.config.AigStudioTestProperties;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioExecutionLink;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioTestRunBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioTestRunVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioTestStatusEnum;
import org.dromara.aigov.studio.helper.AigStudioPromptBuilder;
import org.dromara.aigov.studio.mapper.AigStudioExecutionLinkMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.aigov.studio.service.IAigStudioTestService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 训练台测试调用实现（增量 S5）。
 *
 * <p><b>证据先落、调用后补</b>：进网关之前先插一条 {@code RUNNING} 的关联行，
 * 调用回来再更新成 {@code SUCCEEDED}/{@code FAILED}。这样即使进程崩在调用中间，
 * 库里也留着"这次可能真的调用了"的痕迹——比"崩了就当没调过"诚实
 * （费用是可能已经产生的）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigStudioTestServiceImpl implements IAigStudioTestService {

    private final IAigStudioDraftService draftService;
    private final AigStudioRevisionMapper revisionMapper;
    private final AigStudioExecutionLinkMapper linkMapper;
    private final IAigInvokeService invokeService;
    private final AigStudioTestProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioTestRunVo runTest(Long draftId, AigStudioTestRunBo bo, Long actorId) {
        if (!properties.isEnabled()) {
            throw new ServiceException("训练台测试调用当前是关闭的（aigov.studio.test.enabled=false）："
                + "打开它意味着训练台能发起真实计费的模型调用，需由部署方明确开启");
        }
        if (bo == null) {
            throw new ServiceException("测试入参不能为空");
        }
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(bo.getDataLevel());
        if (dataLevel == null) {
            throw new ServiceException("非法的数据等级：" + bo.getDataLevel());
        }
        if (StringUtils.isBlank(bo.getInput())) {
            throw new ServiceException("测试输入不能为空");
        }
        if (bo.getInput().length() > properties.getMaxInputChars()) {
            throw new ServiceException("测试输入过长（" + bo.getInput().length() + " > "
                + properties.getMaxInputChars() + " 字符）：请截取一段有代表性的输入");
        }

        AigStudioDraftDetailVo draft = draftService.getDraft(draftId);
        requireOwner(draft, actorId);
        if (AigStudioDraftStatusEnum.ARCHIVED.getCode().equals(draft.getStatus())) {
            throw new ServiceException("草稿已归档，不能再跑测试：draftId=" + draftId);
        }
        AigStudioDraftContent content = parseContent(draft.getContentJson());
        if (StringUtils.isBlank(content.getProviderCapability())) {
            throw new ServiceException("草稿未声明所需能力（providerCapability）：没有它无法路由");
        }

        // 把测试钉到"具体的不可变修订"上：列表只给了修订号，这里要拿到 revision_id
        AigStudioRevision revision = revisionMapper.selectOne(Wrappers.<AigStudioRevision>lambdaQuery()
            .eq(AigStudioRevision::getDraftId, draftId)
            .eq(AigStudioRevision::getRevisionNo, draft.getLatestRevision())
            .last("limit 1"));
        if (revision == null) {
            throw new ServiceException("草稿没有对应修订记录，无法把测试钉到内容上：draftId=" + draftId
                + "，revision=" + draft.getLatestRevision());
        }

        // ① 证据先落（RUNNING）：崩在调用中间也留下"可能已经调用过"的痕迹
        AigStudioExecutionLink link = new AigStudioExecutionLink();
        link.setDraftId(draftId);
        link.setRevisionId(revision.getRevisionId());
        link.setContentHash(draft.getContentHash());
        // 记下"这段内容对应哪条版本"是**自有证据**；刻意不把它传给网关（见下）
        link.setAgentVersionId(draft.getAgentVersionId());
        link.setTestStatus(AigStudioTestStatusEnum.RUNNING.getCode());
        linkMapper.insert(link);
        if (link.getLinkId() == null) {
            throw new ServiceException("测试证据未能落库：未取回主键");
        }

        // ② 走网关：策略校验/配额/审计都在那一层，训练台不开第二条模型通道
        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(content.getProviderCapability());
        invokeBo.setDataLevel(dataLevel.getCode());
        invokeBo.setPrompt(buildPrompt(content, bo.getInput()));
        invokeBo.setPayload(bo.getPayload());
        invokeBo.setMaxCost(bo.getMaxCost());
        // ⚠️ 刻意不设 agentVersionId：设了的话，训练台点几次测试就会把该版本的
        // CANARY 调用计数/失败率抬上去——那等于用测试伪造灰度证据。
        // 版本号只记在我们自己的证据表里（上面 link.setAgentVersionId）。

        AigInvokeVo invoked;
        String failure = null;
        try {
            invoked = invokeService.invoke(invokeBo);
        } catch (Exception e) {
            // 非预期异常：仍然要把证据收尾成 FAILED，不能留一条永远 RUNNING 的行
            log.warn("训练台测试调用异常, draftId={}, linkId={}: {}", draftId, link.getLinkId(), e.getMessage());
            invoked = null;
            failure = StringUtils.substring("调用异常：" + e.getMessage(), 0, 500);
        }

        String output = invoked == null ? null : invoked.getOutput();
        boolean success = invoked != null && StringUtils.isNotBlank(output)
            && StringUtils.isBlank(invoked.getErrorCode());
        if (!success && failure == null) {
            failure = StringUtils.substring(invoked == null ? "调用未返回结果"
                : StringUtils.blankToDefault(invoked.getReason(), "模型调用未产出结果"), 0, 500);
        }

        // ③ 收尾证据
        AigStudioExecutionLink update = new AigStudioExecutionLink();
        update.setLinkId(link.getLinkId());
        update.setTestStatus(success
            ? AigStudioTestStatusEnum.SUCCEEDED.getCode() : AigStudioTestStatusEnum.FAILED.getCode());
        update.setResultDigest(StringUtils.isBlank(output) ? null : DigestUtil.sha256Hex(output));
        update.setErrorMessage(success ? null : failure);
        if (invoked != null) {
            update.setTraceId(invoked.getTraceId());
        }
        linkMapper.updateById(update);

        log.info("训练台测试完成, draftId={}, linkId={}, revision={}, success={}, traceId={}",
            draftId, link.getLinkId(), draft.getLatestRevision(), success,
            invoked == null ? null : invoked.getTraceId());
        return toVo(link.getLinkId(), draft, invoked, success, failure, output);
    }

    @Override
    public AigStudioTestRunVo getTestRun(Long linkId) {
        if (linkId == null) {
            throw new ServiceException("证据链ID不能为空");
        }
        AigStudioExecutionLink link = linkMapper.selectById(linkId);
        if (link == null) {
            throw new ServiceException("测试证据不存在：" + linkId);
        }
        AigStudioTestRunVo vo = new AigStudioTestRunVo();
        vo.setLinkId(link.getLinkId());
        vo.setDraftId(link.getDraftId());
        vo.setContentHash(link.getContentHash());
        vo.setTestStatus(link.getTestStatus());
        vo.setResultDigest(link.getResultDigest());
        vo.setTraceId(link.getTraceId());
        vo.setReason(link.getErrorMessage());
        vo.setOutputTruncated(false);
        return vo;
    }

    /**
     * 拼这一次实际发出去的 prompt：分节模板 + 本次测试输入。
     *
     * @param content 草稿内容
     * @param input   测试输入
     * @return prompt
     */
    private String buildPrompt(AigStudioDraftContent content, String input) {
        return AigStudioPromptBuilder.build(content) + "\n\n## 测试输入\n" + input;
    }

    /**
     * 解析草稿内容（已在预检过；这里失败视为数据异常）。
     *
     * @param contentJson 内容 JSON
     * @return 内容模型
     */
    private AigStudioDraftContent parseContent(String contentJson) {
        try {
            AigStudioDraftContent content = jsonMapper.readValue(contentJson, AigStudioDraftContent.class);
            if (content == null) {
                throw new ServiceException("草稿内容为空，无法测试");
            }
            return content;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("草稿内容无法解析，无法测试：" + e.getMessage());
        }
    }

    /**
     * 只有草稿责任人能跑它的测试（测试会花钱，必须能追到人）。
     *
     * @param draft   草稿详情
     * @param actorId 操作用户ID
     */
    private void requireOwner(AigStudioDraftDetailVo draft, Long actorId) {
        if (actorId == null) {
            throw new ServiceException("训练台测试需要明确的操作用户");
        }
        if (!actorId.equals(draft.getOwnerId())) {
            throw new ServiceException("只有草稿责任人可以跑它的测试：draftId=" + draft.getDraftId()
                + "，责任人=" + draft.getOwnerId());
        }
    }

    /**
     * 组装返回体（输出按配置截断，并如实标出"被截断了"）。
     *
     * @param linkId  证据链ID
     * @param draft   草稿详情
     * @param invoked 网关返回（可能为空）
     * @param success 是否成功
     * @param failure 失败原因
     * @param output  完整输出
     * @return 结果视图
     */
    private AigStudioTestRunVo toVo(Long linkId, AigStudioDraftDetailVo draft, AigInvokeVo invoked,
                                    boolean success, String failure, String output) {
        AigStudioTestRunVo vo = new AigStudioTestRunVo();
        vo.setLinkId(linkId);
        vo.setDraftId(draft.getDraftId());
        vo.setRevision(draft.getLatestRevision());
        vo.setContentHash(draft.getContentHash());
        vo.setTestStatus(success
            ? AigStudioTestStatusEnum.SUCCEEDED.getCode() : AigStudioTestStatusEnum.FAILED.getCode());
        vo.setResultDigest(StringUtils.isBlank(output) ? null : DigestUtil.sha256Hex(output));
        vo.setReason(failure);
        if (invoked == null) {
            vo.setOutputTruncated(false);
            return vo;
        }
        String preview = StringUtils.substring(output, 0, properties.getOutputPreviewChars());
        vo.setOutput(preview);
        vo.setOutputTruncated(output != null && output.length() > properties.getOutputPreviewChars());
        vo.setTraceId(invoked.getTraceId());
        vo.setModelKey(invoked.getModelKey());
        vo.setDeploymentType(invoked.getDeploymentType());
        vo.setExternalCall(invoked.getExternalCall());
        vo.setLatencyMs(invoked.getLatencyMs());
        vo.setErrorCode(invoked.getErrorCode());
        vo.setPolicyHits(policyHits(invoked));
        if (StringUtils.isBlank(vo.getReason())) {
            vo.setReason(invoked.getReason());
        }
        return vo;
    }

    /**
     * 把策略命中拼成人读一行（页面要能回答"为什么走了这个模型/为什么被拒"）。
     *
     * @param invoked 网关返回
     * @return 说明文本
     */
    private String policyHits(AigInvokeVo invoked) {
        StringBuilder text = new StringBuilder();
        if (StringUtils.isNotBlank(invoked.getDecision())) {
            text.append("决策=").append(invoked.getDecision());
        }
        if (StringUtils.isNotBlank(invoked.getReasonCode())) {
            text.append(text.length() > 0 ? "；" : "").append("原因码=").append(invoked.getReasonCode());
        }
        if (invoked.getPolicyHits() != null && !invoked.getPolicyHits().isEmpty()) {
            text.append(text.length() > 0 ? "；" : "").append(String.join("；", invoked.getPolicyHits()));
        }
        return text.toString();
    }

}
