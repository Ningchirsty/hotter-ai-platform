package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;

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
     * @param contentJson 原始内容
     * @return 规范化后的 JSON（也是真正入库的那串字节）
     */
    private String canonicalize(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            throw new ServiceException("草稿内容不能为空");
        }
        try {
            return AigStudioContentHasher.canonicalize(contentJson);
        } catch (IllegalArgumentException e) {
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
