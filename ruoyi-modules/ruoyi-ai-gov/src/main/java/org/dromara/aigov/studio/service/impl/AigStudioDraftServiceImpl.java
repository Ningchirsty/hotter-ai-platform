package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.enums.AigAgentCategoryEnum;
import org.dromara.aigov.agent.enums.AigReleaseChannelEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSubmitBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftSubmitVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioMetadataVo;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;
import org.dromara.aigov.studio.domain.vo.AigStudioValidateVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioCapabilityFacts;
import org.dromara.aigov.studio.helper.AigStudioCapabilityFactsResolver;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.helper.AigStudioPrecheck;
import org.dromara.aigov.studio.helper.AigStudioPromptBuilder;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练草稿读写实现（增量 S2）。
 *
 * <p><b>本类刻意不注入模型调用、任务、沙箱任何东西</b>：草稿服务只要"能顺手调一次模型"，
 * "打开训练台"就会变成一次计费调用。测试与门槛留给 S5，且届时走既有网关策略与机器身份。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigStudioDraftServiceImpl implements IAigStudioDraftService {

    private final AigStudioDraftMapper draftMapper;
    private final AigStudioRevisionMapper revisionMapper;
    private final AigAgentMapper agentMapper;
    private final AigAgentVersionMapper agentVersionMapper;
    private final AigStudioCapabilityFactsResolver capabilityFactsResolver;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(AigStudioDraftCreateBo bo, Long actorId) {
        if (bo == null) {
            throw new ServiceException("创建训练草稿的入参不能为空");
        }
        if (StringUtils.isBlank(bo.getAgentCode())) {
            throw new ServiceException("训练对象编码（agentCode）不能为空");
        }
        if (actorId == null) {
            throw new ServiceException("创建训练草稿需要明确的操作用户");
        }
        String canonical = bo.getContentJson() == null || bo.getContentJson().isBlank()
            ? defaultSkeletonJson()
            : canonicalize(bo.getContentJson());
        String hash = AigStudioContentHasher.hash(canonical);

        AigStudioDraft draft = new AigStudioDraft();
        draft.setAgentId(bo.getAgentId());
        draft.setAgentCode(bo.getAgentCode().trim());
        draft.setOrgId(bo.getOrgId());
        draft.setOwnerId(actorId);
        draft.setLatestRevision(1);
        draft.setContentJson(canonical);
        draft.setContentHash(hash);
        draft.setStatus(AigStudioDraftStatusEnum.EDITING.getCode());
        draft.setRemark(bo.getRemark());
        draftMapper.insert(draft);
        if (draft.getDraftId() == null) {
            // 与任务创建同一取舍：拿不回主键说明写入没成功，不能假装成功继续往下写修订
            throw new ServiceException("训练草稿保存失败：未取回主键");
        }

        revisionMapper.insert(newRevision(draft.getDraftId(), 1, canonical, hash, actorId,
            AigStudioRevisionSourceEnum.MANUAL,
            StringUtils.blankToDefault(bo.getSummary(), "创建草稿（初始版本）")));
        log.info("训练草稿已创建, draftId={}, agentCode={}, ownerId={}",
            draft.getDraftId(), draft.getAgentCode(), actorId);
        return draft.getDraftId();
    }

    @Override
    public AigStudioDraftDetailVo getDraft(Long draftId) {
        return toDetail(requireDraft(draftId), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioDraftDetailVo saveDraft(AigStudioDraftSaveBo bo, Long actorId) {
        if (bo == null || bo.getDraftId() == null) {
            throw new ServiceException("草稿ID不能为空");
        }
        if (bo.getExpectedRevision() == null) {
            throw new ServiceException("期望修订号不能为空：编辑必须带版本，否则并发修改会被静默覆盖");
        }
        AigStudioDraft draft = requireDraft(bo.getDraftId());
        requireOwner(draft, actorId);
        requireEditable(draft);
        requireFresh(draft, bo.getExpectedRevision());

        String canonical = canonicalize(bo.getContentJson());
        String hash = AigStudioContentHasher.hash(canonical);
        if (hash.equals(draft.getContentHash())) {
            // 内容没变：不产生新修订、不动修订号。否则每次点保存都多一条历史，
            // 版本记录会被"其实什么都没改"的噪声淹掉
            log.debug("草稿内容未变化，不产生新修订, draftId={}, revision={}",
                draft.getDraftId(), draft.getLatestRevision());
            return toDetail(draft, false);
        }

        int next = draft.getLatestRevision() + 1;
        applyContent(draft, canonical, hash, next);
        revisionMapper.insert(newRevision(draft.getDraftId(), next, canonical, hash, actorId,
            AigStudioRevisionSourceEnum.MANUAL,
            StringUtils.blankToDefault(bo.getSummary(), "人工编辑")));
        log.info("训练草稿已保存, draftId={}, revision={}", draft.getDraftId(), next);
        return toDetail(requireDraft(draft.getDraftId()), true);
    }

    @Override
    public List<AigStudioRevisionVo> listRevisions(Long draftId) {
        requireDraft(draftId);
        return revisionMapper.selectVoList(Wrappers.<AigStudioRevision>lambdaQuery()
            .eq(AigStudioRevision::getDraftId, draftId)
            .orderByDesc(AigStudioRevision::getRevisionNo));
    }

    @Override
    public AigStudioRevisionVo getRevision(Long revisionId) {
        if (revisionId == null) {
            throw new ServiceException("修订ID不能为空");
        }
        AigStudioRevisionVo vo = revisionMapper.selectVoById(revisionId);
        if (vo == null) {
            throw new ServiceException("修订不存在：" + revisionId);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioDraftDetailVo rollback(Long draftId, Integer targetRevisionNo,
                                           Integer expectedRevision, Long actorId) {
        if (targetRevisionNo == null) {
            throw new ServiceException("目标修订号不能为空");
        }
        if (expectedRevision == null) {
            throw new ServiceException("期望修订号不能为空：回滚同样必须带版本（否则会覆盖别人的修改）");
        }
        AigStudioDraft draft = requireDraft(draftId);
        requireOwner(draft, actorId);
        requireEditable(draft);
        requireFresh(draft, expectedRevision);

        AigStudioRevision target = revisionMapper.selectOne(Wrappers.<AigStudioRevision>lambdaQuery()
            .eq(AigStudioRevision::getDraftId, draft.getDraftId())
            .eq(AigStudioRevision::getRevisionNo, targetRevisionNo)
            .last("limit 1"));
        if (target == null) {
            throw new ServiceException("目标修订不存在：draftId=" + draft.getDraftId()
                + "，revisionNo=" + targetRevisionNo);
        }
        if (target.getContentHash().equals(draft.getContentHash())) {
            // 已经是那份内容：没有可回滚的东西，保持"不产生噪声修订"的不变式
            return toDetail(draft, false);
        }

        int next = draft.getLatestRevision() + 1;
        applyContent(draft, target.getContentSnapshotJson(), target.getContentHash(), next);
        revisionMapper.insert(newRevision(draft.getDraftId(), next,
            target.getContentSnapshotJson(), target.getContentHash(), actorId,
            AigStudioRevisionSourceEnum.ROLLBACK, "回滚到修订 #" + targetRevisionNo));
        log.info("训练草稿已回滚, draftId={}, from={}, toRevision={}, newRevision={}",
            draft.getDraftId(), expectedRevision, targetRevisionNo, next);
        return toDetail(requireDraft(draft.getDraftId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioDraftDetailVo applyRevision(Long draftId, Integer expectedRevision,
                                                AigStudioDraftContent content,
                                                AigStudioRevisionSourceEnum source,
                                                String summary, Long actorId) {
        if (content == null) {
            throw new ServiceException("要落成修订的内容不能为空");
        }
        if (expectedRevision == null) {
            throw new ServiceException("期望修订号不能为空：写修订必须带版本（否则会覆盖别人的修改）");
        }
        AigStudioDraft draft = requireDraft(draftId);
        requireOwner(draft, actorId);
        requireEditable(draft);
        requireFresh(draft, expectedRevision);

        String json;
        try {
            json = jsonMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new ServiceException("内容序列化失败：" + e.getMessage());
        }
        String canonical = canonicalize(json);
        String hash = AigStudioContentHasher.hash(canonical);
        if (hash.equals(draft.getContentHash())) {
            // 与当前内容相同：不产生噪声修订（与 save/rollback 同一条不变式）
            return toDetail(draft, false);
        }
        int next = draft.getLatestRevision() + 1;
        applyContent(draft, canonical, hash, next);
        AigStudioRevisionSourceEnum effective =
            source == null ? AigStudioRevisionSourceEnum.MANUAL : source;
        revisionMapper.insert(newRevision(draft.getDraftId(), next, canonical, hash, actorId,
            effective, StringUtils.blankToDefault(summary, "变更提案采纳")));
        log.info("训练草稿已按来源落成新修订, draftId={}, revision={}, source={}",
            draft.getDraftId(), next, effective.getCode());
        return toDetail(requireDraft(draft.getDraftId()), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long draftId, Long actorId) {
        AigStudioDraft draft = requireDraft(draftId);
        requireOwner(draft, actorId);
        requireEditable(draft);
        AigStudioDraft update = new AigStudioDraft();
        update.setStatus(AigStudioDraftStatusEnum.ARCHIVED.getCode());
        int rows = draftMapper.update(update, Wrappers.<AigStudioDraft>lambdaUpdate()
            .eq(AigStudioDraft::getDraftId, draft.getDraftId())
            .in(AigStudioDraft::getStatus, AigStudioDraftStatusEnum.EDITING.getCode(),
                AigStudioDraftStatusEnum.SUBMITTED.getCode()));
        if (rows == 0) {
            // 与读取到的不一致：说明并发的另一次归档/状态变更已经发生
            throw new ServiceException("草稿状态已被并发修改，归档未生效：draftId=" + draftId);
        }
        log.info("训练草稿已归档, draftId={}", draftId);
    }

    @Override
    public PageResult<AigStudioDraftVo> queryPage(AigStudioDraftQueryBo bo, PageQuery pageQuery) {
        AigStudioDraftQueryBo query = bo == null ? new AigStudioDraftQueryBo() : bo;
        LambdaQueryWrapper<AigStudioDraft> wrapper = Wrappers.<AigStudioDraft>lambdaQuery()
            .like(StringUtils.isNotBlank(query.getAgentCode()),
                AigStudioDraft::getAgentCode, query.getAgentCode())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigStudioDraft::getStatus, query.getStatus())
            .eq(query.getOrgId() != null, AigStudioDraft::getOrgId, query.getOrgId())
            .eq(query.getOwnerId() != null, AigStudioDraft::getOwnerId, query.getOwnerId())
            // 最近改动的在前：训练台第一眼要看的是"我昨天改到哪了"
            .orderByDesc(AigStudioDraft::getUpdateTime)
            .orderByDesc(AigStudioDraft::getDraftId);
        // 注意类型：不能把 selectVoPage 内联进 PageResult.build(...)——返回类型是自由类型变量
        Page<AigStudioDraftVo> voPage = draftMapper.selectVoPage(pageQuery.build(), wrapper);
        // 标签与"未提交改动"由服务端在**分页映射这一处**填（VO 里预置了字段就必须有人填，
        // 否则列表页两列永远为空——本项目出现过多次同类问题）
        for (AigStudioDraftVo vo : voPage.getRecords()) {
            AigStudioDraftStatusEnum status = AigStudioDraftStatusEnum.find(vo.getStatus());
            vo.setStatusLabel(status == null ? vo.getStatus() : status.getDesc());
            vo.setUnpublishedChanges(vo.getLastPublishedHash() == null
                || !vo.getLastPublishedHash().equals(vo.getContentHash()));
        }
        return PageResult.build(voPage.getRecords(), voPage.getTotal());
    }

    @Override
    public AigStudioValidateVo validateDraft(Long draftId) {
        AigStudioDraft draft = requireDraft(draftId);
        // D07：分级预检（阻断/提醒 + 字段定位 + 建议动作），且与提交共用同一套判据。
        // 能力事实先查成快照再传进去：预检是纯函数，判定与 I/O 分开（口径来自能力概览，见 facts 类注释）。
        AigStudioPrecheck.Result result = AigStudioPrecheck.evaluate(jsonMapper, draft.getContentJson(),
            factsFor(draft.getContentJson()));
        AigStudioValidateVo vo = new AigStudioValidateVo();
        vo.setDraftId(draft.getDraftId());
        vo.setRevision(draft.getLatestRevision());
        vo.setContentHash(draft.getContentHash());
        vo.setBlockers(result.blockers());
        vo.setWarnings(result.warnings());
        // problems 保留给旧调用方，恒等于阻断项说明（提醒不放进 problems，避免被当成错误）
        vo.setProblems(result.blockerMessages());
        vo.setPassed(result.passed());
        return vo;
    }

    @Override
    public AigStudioMetadataVo metadata() {
        return AigStudioMetadataVo.build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioDraftSubmitVo submitDraft(Long draftId, AigStudioDraftSubmitBo bo, Long actorId) {
        AigStudioDraft draft = requireDraft(draftId);
        requireOwner(draft, actorId);
        requireEditable(draft);

        // 预检必须先过：不允许"先提交、后面再补"——那会让一份不合格内容进入发布流程。
        // D07：与 validateDraft 共用 AigStudioPrecheck，因此"预检说通过"与"提交被接受"
        // 不可能分叉；类别校验也已随这次前移从提交阶段并进同一套判据。
        AigStudioPrecheck.Result precheck = AigStudioPrecheck.evaluate(jsonMapper, draft.getContentJson(),
            factsFor(draft.getContentJson()));
        if (!precheck.passed()) {
            throw new ServiceException("草稿未通过预检，不能提交："
                + String.join("；", precheck.blockerMessages()));
        }
        AigStudioDraftContent content = parseContent(draft.getContentJson());

        // 类别在预检里已作为阻断项校验过；这里只做防御性兜底（并发写入等）
        AigAgentCategoryEnum category = AigAgentCategoryEnum.find(content.getAgentCategory());
        if (category == null) {
            throw new ServiceException("草稿未声明合法的 Agent 类别（agentCategory=" + content.getAgentCategory()
                + "）：当前只接受 " + AigStudioPrecheck.categoryCodes() + "。非创作类 Agent 需要的新类别属于独立变更，"
                + "不在本增量内——请先扩展类别枚举与契约。");
        }

        boolean agentCreated = false;
        Long agentId = draft.getAgentId();
        if (agentId == null) {
            // 从零创建：编码必须未被占用。若已存在，明确拒绝而不是"顺手绑上去"——
            // 那会把别人的 Agent 变成这份草稿的产物
            AigAgent existing = agentMapper.selectOne(Wrappers.<AigAgent>lambdaQuery()
                .eq(AigAgent::getAgentCode, draft.getAgentCode()).last("limit 1"));
            if (existing != null) {
                throw new ServiceException("已存在同编码的 Agent（" + draft.getAgentCode()
                    + "）：请改为「基于它创建草稿」再提交，而不是新建一个同编码对象");
            }
            AigAgent agent = new AigAgent();
            agent.setAgentCode(draft.getAgentCode());
            agent.setAgentName(StringUtils.blankToDefault(content.getAgentName(), draft.getAgentCode()));
            agent.setCategory(category.getCode());
            agent.setOwnerId(draft.getOwnerId());
            agent.setBuiltin("N");
            agent.setDescription(StringUtils.substring(content.getRoleDescription(), 0, 500));
            agent.setStatus("0");
            agentMapper.insert(agent);
            if (agent.getAgentId() == null) {
                throw new ServiceException("创建 Agent 定义失败：未取回主键");
            }
            agentId = agent.getAgentId();
            agentCreated = true;
        }

        String version = resolveVersion(bo, draft, agentId);

        AigAgentVersion agentVersion = new AigAgentVersion();
        agentVersion.setAgentId(agentId);
        agentVersion.setVersion(version);
        // 只到 DRAFT 为止：发布推进仍归既有状态机（五道门槛）
        agentVersion.setReleaseStatus(AigReleaseStatusEnum.DRAFT.getCode());
        // 显式给通道，不依赖 DDL 默认值（默认值一变，行为就跟着变，而这里是有语义的）
        agentVersion.setReleaseChannel(AigReleaseChannelEnum.TESTING.getCode());
        agentVersion.setInputSchema(content.getInputSchema());
        agentVersion.setOutputSchema(content.getOutputSchema());
        agentVersion.setPromptTemplate(AigStudioPromptBuilder.build(content));
        // 结构化留档：把提交时那一版内容原文存进既有 config_json，
        // 事后要回答「这条版本当时是什么内容」不必靠拼凑
        agentVersion.setConfigJson(draft.getContentJson());
        agentVersion.setAllowedTools(joinList(content.getAllowedTools()));
        agentVersion.setForbiddenTools(joinList(content.getForbiddenTools()));
        agentVersion.setProviderCapability(content.getProviderCapability());
        agentVersion.setAllowExternal(StringUtils.blankToDefault(content.getAllowExternal(), "N"));
        agentVersion.setKnowledgeScopeJson(joinList(content.getKnowledgeScope()));
        agentVersion.setStatus("0");
        agentVersion.setRemark(StringUtils.substring(
            StringUtils.blankToDefault(bo == null ? null : bo.getRemark(),
                "由训练台草稿 #" + draft.getDraftId() + " 修订 #" + draft.getLatestRevision() + " 提交"), 0, 500));
        agentVersionMapper.insert(agentVersion);
        if (agentVersion.getAgentVersionId() == null) {
            throw new ServiceException("提交失败：Agent 版本未取回主键");
        }

        // 回写草稿：绑定 Agent（若本次新建）+ 版本 + 已提交哈希 + 状态。
        // 仍带 CAS（where latest_revision = 读到的值）：提交期间别人改了内容，就不能算"这版提交成功"
        AigStudioDraft update = new AigStudioDraft();
        update.setAgentId(agentId);
        update.setAgentVersionId(agentVersion.getAgentVersionId());
        update.setLastPublishedHash(draft.getContentHash());
        update.setStatus(AigStudioDraftStatusEnum.SUBMITTED.getCode());
        int rows = draftMapper.update(update, Wrappers.<AigStudioDraft>lambdaUpdate()
            .eq(AigStudioDraft::getDraftId, draft.getDraftId())
            .eq(AigStudioDraft::getLatestRevision, draft.getLatestRevision()));
        if (rows == 0) {
            throw conflict(draft, draft.getLatestRevision());
        }

        AigStudioDraftSubmitVo vo = new AigStudioDraftSubmitVo();
        vo.setDraftId(draft.getDraftId());
        vo.setRevision(draft.getLatestRevision());
        vo.setContentHash(draft.getContentHash());
        vo.setAgentId(agentId);
        vo.setAgentCode(draft.getAgentCode());
        vo.setAgentCreated(agentCreated);
        vo.setAgentVersionId(agentVersion.getAgentVersionId());
        vo.setVersion(version);
        vo.setReleaseStatus(agentVersion.getReleaseStatus());
        log.info("训练草稿已提交, draftId={}, revision={}, agentId={}, agentVersionId={}, version={}, agentCreated={}",
            draft.getDraftId(), draft.getLatestRevision(), agentId, agentVersion.getAgentVersionId(),
            version, agentCreated);
        return vo;
    }

    /**
     * 解析版本号：给了就用给的；没给按 {@code 0.1.<修订号>} 生成。
     *
     * <p>生成号被占用时**报错要求显式指定**，不自动跳到下一个——"悄悄换一个版本号"
     * 会让调用方以为发布的还是它要的那个版本。</p>
     *
     * @param bo      入参（可空）
     * @param draft   草稿
     * @param agentId Agent 定义ID
     * @return 版本号
     */
    private String resolveVersion(AigStudioDraftSubmitBo bo, AigStudioDraft draft, Long agentId) {
        String version = bo == null ? null : (bo.getVersion() == null ? null : bo.getVersion().trim());
        if (StringUtils.isBlank(version)) {
            version = "0.1." + draft.getLatestRevision();
        }
        AigAgentVersion existing = agentVersionMapper.selectOne(Wrappers.<AigAgentVersion>lambdaQuery()
            .eq(AigAgentVersion::getAgentId, agentId)
            .eq(AigAgentVersion::getVersion, version)
            .last("limit 1"));
        if (existing != null) {
            throw new ServiceException("版本号已被占用（agentId=" + agentId + "，version=" + version
                + "）：请显式指定一个新版本号");
        }
        return version;
    }

    /**
     * 把八个 Prompt 分节拼成可读的模板文本。
     *
     * <p><b>已被 {@link AigStudioPromptBuilder} 取代（D09）</b>：这里曾经有第二份分节拼装实现，
     * 与测试/执行用的那份是"两份实现拼同一段文本"，一旦漂移就会出现
     * "测试跑的是 A、提交上去的是 B"。现在只有一份实现，调用点在
     * {@code agentVersion.setPromptTemplate(AigStudioPromptBuilder.build(content))}。</p>
     */

    /**
     * 取草稿声明的能力事实（供预检判定）。
     *
     * <p><b>内容连 JSON 都不合法时返回 null</b>：那种情况预检会给出 {@code CONTENT_NOT_JSON}，
     * 这里再抛一次错只会把"具体哪里不合法"换成一句笼统的服务异常。</p>
     *
     * @param contentJson 内容原文
     * @return 能力事实；解析失败或未声明能力返回 null（表示"本次不判能力事实"）
     */
    private AigStudioCapabilityFacts factsFor(String contentJson) {
        try {
            AigStudioDraftContent content = jsonMapper.readValue(contentJson, AigStudioDraftContent.class);
            return content == null ? null : capabilityFactsResolver.resolve(content.getProviderCapability());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析草稿内容（已过预检，因此这里解析失败只可能是并发写入损坏，按内部错误抛）。
     *
     * @param contentJson 内容
     * @return 内容模型
     */
    private AigStudioDraftContent parseContent(String contentJson) {
        try {
            AigStudioDraftContent content = jsonMapper.readValue(contentJson, AigStudioDraftContent.class);
            if (content == null) {
                throw new ServiceException("草稿内容为空，不能提交");
            }
            return content;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("草稿内容无法解析，不能提交：" + e.getMessage());
        }
    }

    /**
     * 逗号连接（空/全空返回 null，避免落一个空串进"清单"列）。
     *
     * @param items 列表
     * @return 连接结果
     */
    private String joinList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : items) {
            if (StringUtils.isNotBlank(item)) {
                cleaned.add(item.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 把内容写回草稿行（<b>带 CAS</b>：{@code where latest_revision = 读到的那个值}）。
     *
     * @param draft    读到的草稿（提供 draftId 与当前修订号）
     * @param content  规范化内容
     * @param hash     内容哈希
     * @param next     新修订号
     */
    private void applyContent(AigStudioDraft draft, String content, String hash, int next) {
        AigStudioDraft update = new AigStudioDraft();
        update.setContentJson(content);
        update.setContentHash(hash);
        update.setLatestRevision(next);
        update.setStatus(AigStudioDraftStatusEnum.EDITING.getCode());
        int rows = draftMapper.update(update, Wrappers.<AigStudioDraft>lambdaUpdate()
            .eq(AigStudioDraft::getDraftId, draft.getDraftId())
            .eq(AigStudioDraft::getLatestRevision, draft.getLatestRevision()));
        if (rows == 0) {
            // 走到这里说明"读之后、写之前"有人改过它：必须报错，不能覆盖
            throw conflict(draft, draft.getLatestRevision());
        }
    }

    private AigStudioDraft requireDraft(Long draftId) {
        if (draftId == null) {
            throw new ServiceException("草稿ID不能为空");
        }
        AigStudioDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            throw new ServiceException("训练草稿不存在：" + draftId);
        }
        return draft;
    }

    private void requireOwner(AigStudioDraft draft, Long actorId) {
        if (actorId == null) {
            throw new ServiceException("训练草稿的写操作需要明确的操作用户");
        }
        if (!actorId.equals(draft.getOwnerId())) {
            throw new ServiceException("只有草稿责任人可以修改它：draftId=" + draft.getDraftId()
                + "，责任人=" + draft.getOwnerId());
        }
    }

    private void requireEditable(AigStudioDraft draft) {
        if (AigStudioDraftStatusEnum.ARCHIVED.getCode().equals(draft.getStatus())) {
            throw new ServiceException("草稿已归档，不能再修改：draftId=" + draft.getDraftId()
                + "（要再训练请新建草稿）");
        }
    }

    private void requireFresh(AigStudioDraft draft, Integer expectedRevision) {
        if (!expectedRevision.equals(draft.getLatestRevision())) {
            throw conflict(draft, expectedRevision);
        }
    }

    /**
     * 造一条修订（不可变快照）。
     */
    private AigStudioRevision newRevision(Long draftId, int revisionNo, String content, String hash,
                                         Long authorId, AigStudioRevisionSourceEnum source, String summary) {
        AigStudioRevision revision = new AigStudioRevision();
        revision.setDraftId(draftId);
        revision.setRevisionNo(revisionNo);
        revision.setContentSnapshotJson(content);
        revision.setContentHash(hash);
        revision.setAuthorId(authorId);
        revision.setSource(source.getCode());
        revision.setSummary(StringUtils.substring(summary, 0, 200));
        return revision;
    }

    /**
     * 冲突异常（消息里必须给出"你手上的版本"和"库里的版本"，否则用户不知道该怎么办）。
     */
    private ServiceException conflict(AigStudioDraft draft, Integer expectedRevision) {
        return new ServiceException("草稿已被他人修改，本次编辑未生效：draftId=" + draft.getDraftId()
            + "，你手上的修订号=" + expectedRevision + "，库中当前=" + draft.getLatestRevision()
            + "。请刷新后重新编辑（本系统不会静默覆盖别人的修改）。");
    }

    /**
     * 规范化内容；不是合法 JSON 时报错。
     *
     * <p><b>为什么必须先解析成内容对象、再序列化后规范化</b>（不能直接规范化调用方给的那串）：
     * 内容的哈希有两条路径，必须给出同一个结果——
     * ①<b>写入</b>：本方法算 {@code contentHash} 并落库；
     * ②<b>改动</b>：{@code AigStudioProposalServiceImpl#hashOf} 把 {@code contentJson} 解析成对象、
     * 改完再序列化后算哈希。</p>
     *
     * <p>如果这里只规范化"原串"，两条路径就会分叉：内容模型里带非 null 默认值的字段
     * （如 {@code promptSections = new LinkedHashMap<>()}）在 ② 里一定会被序列化出来
     * （{@code "promptSections":{}}），而调用方**省略**该字段时 ① 的串里没有它
     * ⇒ 哈希不等 ⇒ 「提案没有产生任何内容变化」这道守卫**永远不会触发**。
     * 生产实测（2026-10-12）：把 {@code /objective} 改成与草稿**完全相同**的值，提案仍被接受并落库。
     * 走同一条路径后，"存什么"与"算什么"才是一回事
     * （由 {@code AigStudioContentHasherTest#pojoRoundTripCanonicalFormIsIdempotent} 钉住）。</p>
     *
     * @param contentJson 原始内容
     * @return 规范化后的 JSON（也是真正入库的那串字节）
     */
    private String canonicalize(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            throw new ServiceException("草稿内容不能为空");
        }
        try {
            return AigStudioContentHasher.canonicalize(
                jsonMapper.writeValueAsString(jsonMapper.readValue(contentJson, AigStudioDraftContent.class)));
        } catch (Exception e) {
            // 覆盖两类：不是合法 JSON / 不是内容对象（形状问题），以及规范化时的非法输入
            throw new ServiceException("草稿内容格式不正确：" + e.getMessage());
        }
    }

    /**
     * 默认骨架：八个标准 Prompt 分节都在，但正文为空——页面据此渲染"待填写"，
     * 而不是让用户从一个连字段都没有的空对象开始。
     *
     * @return 骨架 JSON
     */
    private String defaultSkeletonJson() {
        AigStudioDraftContent content = new AigStudioDraftContent();
        LinkedHashMap<String, String> sections = new LinkedHashMap<>();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            sections.put(key, "");
        }
        content.setPromptSections(sections);
        content.setAllowExternal("N");
        return AigStudioContentHasher.canonicalize(jsonMapper.writeValueAsString(content));
    }

    /**
     * 组装详情视图（含服务端判定的"是否有未提交改动"）。
     *
     * @param draft           草稿
     * @param revisionCreated 本次调用是否产生新修订
     * @return 详情
     */
    private AigStudioDraftDetailVo toDetail(AigStudioDraft draft, boolean revisionCreated) {
        AigStudioDraftDetailVo vo = new AigStudioDraftDetailVo();
        vo.setDraftId(draft.getDraftId());
        vo.setAgentId(draft.getAgentId());
        vo.setAgentCode(draft.getAgentCode());
        vo.setOrgId(draft.getOrgId());
        vo.setOwnerId(draft.getOwnerId());
        vo.setLatestRevision(draft.getLatestRevision());
        vo.setContentJson(draft.getContentJson());
        vo.setContentHash(draft.getContentHash());
        vo.setLastPublishedHash(draft.getLastPublishedHash());
        vo.setAgentVersionId(draft.getAgentVersionId());
        vo.setStatus(draft.getStatus());
        AigStudioDraftStatusEnum status = AigStudioDraftStatusEnum.find(draft.getStatus());
        vo.setStatusLabel(status == null ? draft.getStatus() : status.getDesc());
        // 从未提交过 = 一切都还没发布；提交过则比较哈希
        vo.setUnpublishedChanges(draft.getLastPublishedHash() == null
            || !draft.getLastPublishedHash().equals(draft.getContentHash()));
        vo.setRevisionCreated(revisionCreated);
        vo.setRemark(draft.getRemark());
        vo.setCreateTime(draft.getCreateTime());
        vo.setUpdateTime(draft.getUpdateTime());
        return vo;
    }

}
