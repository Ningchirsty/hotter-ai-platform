package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.studio.config.AigStudioCopilotProperties;
import org.dromara.aigov.studio.domain.AigStudioCopilotTurn;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioCopilotTurnVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.enums.AigStudioCopilotStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioCopilotParser;
import org.dromara.aigov.studio.helper.AigStudioCopilotPrompts;
import org.dromara.aigov.studio.mapper.AigStudioCopilotTurnMapper;
import org.dromara.aigov.studio.service.IAigStudioCopilotService;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话式 Copilot 实现（附件 07 增量 6）。
 *
 * <h3>执行顺序（每一段都有理由）</h3>
 * <ol>
 *     <li><b>开关 → 入参 → 草稿（责任人/未归档/有 providerCapability）</b>：这些都在花钱之前拒掉。
 *         尤其责任人：让非责任人点一下就能烧一次调用，是把配额的事故面白送出去；</li>
 *     <li><b>先把这一轮落成 RUNNING</b>：崩溃/超时都留下"可能已经调用过"的痕迹
 *         （与测试调用同一条纪律）；</li>
 *     <li><b>调网关</b>，并在失败时把这一轮收尾成 FAILED，绝不留下永远 RUNNING 的行；</li>
 *     <li><b>解析 → 落提案</b>：解析只做形状，白名单与类型由提案服务/规划器判定；
 *         被规划器拒绝时记 REJECTED（<b>这不是失败，是"模型越界了"</b>——要能看见）；</li>
 *     <li><b>收尾这一轮</b>：结论、原因、提案ID、traceId、模型与是否外发。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigStudioCopilotServiceImpl implements IAigStudioCopilotService {

    private final AigStudioCopilotTurnMapper turnMapper;

    private final IAigStudioDraftService draftService;

    private final IAigStudioProposalService proposalService;

    private final IAigInvokeService invokeService;

    private final AigStudioCopilotProperties properties;

    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioCopilotTurnVo chat(Long draftId, AigStudioCopilotChatBo bo, Long actorId) {
        if (!properties.isEnabled()) {
            throw new ServiceException("对话式 Copilot 当前是关闭的（aigov.studio.copilot.enabled=false）："
                + "它会发起真实计费的模型调用，因此默认关闭，由部署方显式开启");
        }
        if (bo == null) {
            throw new ServiceException("对话入参不能为空");
        }
        String message = bo.getMessage() == null ? null : bo.getMessage().trim();
        if (StringUtils.isBlank(message)) {
            throw new ServiceException("对话内容不能为空");
        }
        if (message.length() > properties.getMaxMessageChars()) {
            throw new ServiceException("对话内容过长（" + message.length() + " > "
                + properties.getMaxMessageChars() + " 字符）：请说清要改哪一处");
        }

        AigStudioDraftDetailVo draft = draftService.getDraft(draftId);
        requireOwner(draft, actorId);
        if (AigStudioDraftStatusEnum.ARCHIVED.getCode().equals(draft.getStatus())) {
            throw new ServiceException("草稿已归档，不能再对话：draftId=" + draftId);
        }
        AigStudioDraftContent content = parseContent(draft.getContentJson());
        if (StringUtils.isBlank(content.getProviderCapability())) {
            throw new ServiceException("草稿未声明所需能力（providerCapability）：没有它无法路由，"
                + "也就无法发起对话");
        }

        // ① 证据先落：崩在调用中间也留下"可能已经调用过"的痕迹
        AigStudioCopilotTurn turn = new AigStudioCopilotTurn();
        turn.setDraftId(draftId);
        turn.setRevision(draft.getLatestRevision());
        turn.setContentHash(draft.getContentHash());
        turn.setUserMessage(StringUtils.substring(message, 0, 2000));
        turn.setDataLevel(bo.getDataLevel());
        turn.setStatus(AigStudioCopilotStatusEnum.RUNNING.getCode());
        turnMapper.insert(turn);
        if (turn.getTurnId() == null) {
            throw new ServiceException("对话证据未能落库：未取回主键");
        }

        // ② 走网关：策略校验/配额/审计在同一层，训练台不开第二条模型通道
        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(content.getProviderCapability());
        invokeBo.setDataLevel(bo.getDataLevel());
        invokeBo.setPrompt(AigStudioCopilotPrompts.build(draft.getContentJson(), message));
        invokeBo.setMaxCost(bo.getMaxCost());
        // ⚠️ 刻意不设 agentVersionId：设了点几次对话就会把该版本的 CANARY 计数/失败率抬上去，
        // 那等于用对话伪造灰度证据（与训练台测试调用同一纪律）

        AigInvokeVo invoked;
        String invokeFailure = null;
        try {
            invoked = invokeService.invoke(invokeBo);
        } catch (Exception e) {
            log.warn("对话式 Copilot 调用异常, draftId={}, turnId={}: {}",
                draftId, turn.getTurnId(), e.getMessage());
            invoked = null;
            invokeFailure = StringUtils.substring("调用异常：" + e.getMessage(), 0, 500);
        }

        String output = invoked == null ? null : invoked.getOutput();
        if (StringUtils.isBlank(output)) {
            String reason = invokeFailure != null ? invokeFailure
                : StringUtils.blankToDefault(invoked == null ? "调用未返回结果" : invoked.getReason(),
                    "模型调用未产出结果");
            return finish(turn, AigStudioCopilotStatusEnum.FAILED, reason, null, invoked, null);
        }

        // ③ 解析（只做形状）→ 落提案（白名单与类型在这一步判定，草稿仍未被改动）
        AigStudioCopilotParser.Parsed parsed;
        try {
            parsed = AigStudioCopilotParser.parse(output);
        } catch (IllegalArgumentException e) {
            return finish(turn, AigStudioCopilotStatusEnum.FAILED,
                StringUtils.substring(e.getMessage(), 0, 500), null, invoked, output);
        }

        AigStudioProposalCreateBo createBo = new AigStudioProposalCreateBo();
        createBo.setBaseRevision(draft.getLatestRevision());
        createBo.setOperations(parsed.operations());
        createBo.setSummary(parsed.summary());
        createBo.setSource(AigStudioProposalSourceEnum.COPILOT.getCode());

        Long proposalId;
        try {
            // ★先"只校验、不落库"地判一次：模型越界（越权路径/值类型不符/没有变化）要在这里变成 REJECTED。
            //   ⚠️ 不能靠"catch create 抛的业务异常"来判 REJECTED：create 是 @Transactional，
            //   它抛异常会把 chat 所在的共享事务标记为 rollback-only ⇒ 外层提交时抛
            //   UnexpectedRollbackException，表现为「发生未知异常，请联系管理员」，
            //   而且**整轮对话（含拒绝结论）被回滚掉**——恰好丢掉"模型越界"这个最该被看见的信号。
            //   2026-10-12 生产实测 3/3 复现；根因与修法见 docs/aigov/13-生产核查记录…md §十八。
            proposalService.validateForCreate(draftId, createBo, actorId);
        } catch (ServiceException e) {
            // 越权路径 / 值类型不符 / 没有产生变化：模型越界了，不是系统失败
            return finish(turn, AigStudioCopilotStatusEnum.REJECTED,
                StringUtils.substring(e.getMessage(), 0, 500), null, invoked, output);
        }
        try {
            AigStudioChangeProposalVo proposal = proposalService.create(draftId, createBo, actorId);
            proposalId = proposal.getProposalId();
        } catch (ServiceException e) {
            // 兜底：校验通过后 create 仍可能拒绝——只可能是校验与落库之间草稿又被改了（并发）。
            return finish(turn, AigStudioCopilotStatusEnum.REJECTED,
                StringUtils.substring(e.getMessage(), 0, 500), null, invoked, output);
        }

        log.info("对话式 Copilot 已产出提案, draftId={}, turnId={}, proposalId={}, traceId={}",
            draftId, turn.getTurnId(), proposalId, invoked.getTraceId());
        return finish(turn, AigStudioCopilotStatusEnum.SUCCEEDED, null, proposalId, invoked, output);
    }

    @Override
    public List<AigStudioCopilotTurnVo> turns(Long draftId) {
        // 先确认草稿存在（不存在的草稿返回空列表会让人以为"没聊过"，而不是"草稿ID错了"）
        draftService.getDraft(draftId);
        List<AigStudioCopilotTurn> rows = turnMapper.selectList(
            new LambdaQueryWrapper<AigStudioCopilotTurn>()
                .eq(AigStudioCopilotTurn::getDraftId, draftId)
                .orderByDesc(AigStudioCopilotTurn::getTurnId)
                .last("limit " + properties.getTurnHistorySize()));
        List<AigStudioCopilotTurnVo> vos = new ArrayList<>();
        for (AigStudioCopilotTurn row : rows) {
            vos.add(toVo(row));
        }
        return vos;
    }

    /* ------------------------------------------------------------------ 内部 */

    /**
     * 收尾这一轮：写结论、原因、提案ID 与调用事实。
     *
     * @param turn       已落库的轮次
     * @param status     结论
     * @param reason     拒绝/失败原因（成功为 null）
     * @param proposalId 产出的提案ID（可空）
     * @param invoked    网关返回（可空）
     * @param output     模型原文（可空）
     * @return 视图
     */
    private AigStudioCopilotTurnVo finish(AigStudioCopilotTurn turn, AigStudioCopilotStatusEnum status,
                                          String reason, Long proposalId, AigInvokeVo invoked,
                                          String output) {
        AigStudioCopilotTurn update = new AigStudioCopilotTurn();
        update.setTurnId(turn.getTurnId());
        update.setStatus(status.getCode());
        update.setFailureReason(reason);
        update.setProposalId(proposalId);
        update.setModelOutput(output == null ? null
            : StringUtils.substring(output, 0, properties.getOutputPreviewChars()));
        if (invoked != null) {
            update.setTraceId(invoked.getTraceId());
            update.setModelKey(invoked.getModelKey());
            update.setDeploymentType(invoked.getDeploymentType());
            update.setLatencyMs(invoked.getLatencyMs());
            update.setExternalCall(invoked.getExternalCall() == null ? null
                : (invoked.getExternalCall() ? "Y" : "N"));
        }
        turnMapper.updateById(update);

        turn.setStatus(update.getStatus());
        turn.setFailureReason(update.getFailureReason());
        turn.setProposalId(update.getProposalId());
        turn.setModelOutput(update.getModelOutput());
        turn.setTraceId(update.getTraceId());
        turn.setModelKey(update.getModelKey());
        turn.setDeploymentType(update.getDeploymentType());
        turn.setLatencyMs(update.getLatencyMs());
        turn.setExternalCall(update.getExternalCall());
        return toVo(turn);
    }

    /**
     * 实体 → 视图。
     *
     * @param turn 实体
     * @return 视图
     */
    private AigStudioCopilotTurnVo toVo(AigStudioCopilotTurn turn) {
        AigStudioCopilotTurnVo vo = new AigStudioCopilotTurnVo();
        vo.setTurnId(turn.getTurnId());
        vo.setDraftId(turn.getDraftId());
        vo.setRevision(turn.getRevision());
        vo.setContentHash(turn.getContentHash());
        vo.setUserMessage(turn.getUserMessage());
        vo.setDataLevel(turn.getDataLevel());
        vo.setModelOutput(turn.getModelOutput());
        vo.setStatus(turn.getStatus());
        vo.setFailureReason(turn.getFailureReason());
        vo.setProposalId(turn.getProposalId());
        vo.setTraceId(turn.getTraceId());
        vo.setModelKey(turn.getModelKey());
        vo.setDeploymentType(turn.getDeploymentType());
        vo.setExternalCall(turn.getExternalCall());
        vo.setLatencyMs(turn.getLatencyMs());
        vo.setCreateTime(turn.getCreateTime());
        vo.setUpdateTime(turn.getUpdateTime());
        return vo;
    }

    /**
     * 只有草稿责任人能发起对话（这一轮要花钱）。
     *
     * @param draft   草稿详情
     * @param actorId 操作用户
     */
    private void requireOwner(AigStudioDraftDetailVo draft, Long actorId) {
        if (actorId == null) {
            throw new ServiceException("训练台需要一个明确的操作用户");
        }
        if (!actorId.equals(draft.getOwnerId())) {
            throw new ServiceException("只有草稿责任人可以和培训助手对话：draftId=" + draft.getDraftId()
                + "，责任人=" + draft.getOwnerId());
        }
    }

    /**
     * 解析草稿内容。
     *
     * @param contentJson 内容 JSON
     * @return 内容模型
     */
    private AigStudioDraftContent parseContent(String contentJson) {
        try {
            AigStudioDraftContent content = jsonMapper.readValue(contentJson, AigStudioDraftContent.class);
            if (content == null) {
                throw new ServiceException("草稿内容为空，无法对话");
            }
            return content;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("草稿内容无法解析：" + e.getMessage());
        }
    }

}
