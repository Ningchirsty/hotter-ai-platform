package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;
import org.dromara.aigov.studio.domain.bo.AigStudioActionDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioChildAgentDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioSkillDraftBo;
import org.dromara.aigov.studio.domain.vo.AigStudioActionDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioArtifactGenerateVo;
import org.dromara.aigov.studio.domain.vo.AigStudioChildAgentDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioSkillDraftVo;
import org.dromara.aigov.studio.enums.AigStudioArtifactKindEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioArtifactDraftGenerator;
import org.dromara.aigov.studio.mapper.AigStudioActionDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioChildAgentDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioSkillDraftMapper;
import org.dromara.aigov.studio.service.IAigStudioArtifactDraftService;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 草案生成器实现（附件 07 增量 7／ST-005/006/007）。
 *
 * <h3>三处刻意的实现选择</h3>
 * <ol>
 *     <li><b>创建草案不碰草稿</b>：草案表是"种子"，不是草稿状态。生成是另一个入口，
 *         且它走训练台唯一的写修订入口（{@code applyRevision}）——写修订的三件事
 *         （无变化不产生修订 / 修订号 +1 / CAS 冲突）只有一处实现；</li>
 *     <li><b>预览与生成同源</b>：两者都调 {@link #build}；不一致的表现是"人看着 A、
 *         库里写进 B"，那是没有任何报错的一类错；</li>
 *     <li><b>生成不产生"没有新修订"的成功</b>：若目标草稿内容与生成物一致，
 *         走 {@code applyRevision} 会返回 {@code revisionCreated=false}；这里把它当错误报出来，
 *         而不是返回一个"成功但什么都没发生"的结果——否则人会以为已经生成过了。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigStudioArtifactDraftServiceImpl implements IAigStudioArtifactDraftService {

    /**
     * 启用（Y）
     */
    private static final String YES = "Y";

    private final AigStudioSkillDraftMapper skillDraftMapper;

    private final AigStudioChildAgentDraftMapper childAgentDraftMapper;

    private final AigStudioActionDraftMapper actionDraftMapper;

    private final IAigStudioDraftService draftService;

    /* ------------------------------------------------------------------ 创建 */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSkillDraft(AigStudioSkillDraftBo bo, Long actorId) {
        requireActor(actorId);
        if (bo == null) {
            throw new ServiceException("技能草案入参不能为空");
        }
        String code = requireCode(bo.getDraftCode());
        if (skillDraftMapper.selectCount(new LambdaQueryWrapper<AigStudioSkillDraft>()
            .eq(AigStudioSkillDraft::getDraftCode, code)) > 0) {
            throw new ServiceException("技能草案编码已存在：" + code + "（编码是页面上认出草案的唯一方式，不能重复）");
        }
        AigStudioSkillDraft entity = new AigStudioSkillDraft();
        entity.setDraftCode(code);
        entity.setDraftName(StringUtils.substring(
            StringUtils.blankToDefault(bo.getDraftName(), code), 0, 128));
        entity.setSkillCode(bo.getSkillCode() == null ? null : bo.getSkillCode().trim());
        entity.setSkillName(StringUtils.substring(bo.getSkillName(), 0, 128));
        entity.setCapabilities(bo.getCapabilities());
        entity.setDescription(bo.getDescription());
        entity.setUsageNotes(bo.getUsageNotes());
        entity.setAgentCategory(bo.getAgentCategory() == null ? null : bo.getAgentCategory().trim());
        entity.setProviderCapability(bo.getProviderCapability() == null
            ? null : bo.getProviderCapability().trim());
        entity.setAllowedTools(bo.getAllowedTools());
        entity.setOwnerId(actorId);
        entity.setOrgId(bo.getOrgId());
        entity.setEnabled(enabledOf(bo.getEnabled()));
        entity.setDelFlag("0");
        entity.setRemark(bo.getRemark());
        skillDraftMapper.insert(entity);
        log.info("新增技能草案, skillDraftId={}, draftCode={}, agentCategory={}",
            entity.getSkillDraftId(), code, entity.getAgentCategory());
        return entity.getSkillDraftId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChildAgentDraft(AigStudioChildAgentDraftBo bo, Long actorId) {
        requireActor(actorId);
        if (bo == null) {
            throw new ServiceException("子 Agent 草案入参不能为空");
        }
        String code = requireCode(bo.getDraftCode());
        if (childAgentDraftMapper.selectCount(new LambdaQueryWrapper<AigStudioChildAgentDraft>()
            .eq(AigStudioChildAgentDraft::getDraftCode, code)) > 0) {
            throw new ServiceException("子 Agent 草案编码已存在：" + code);
        }
        AigStudioChildAgentDraft entity = new AigStudioChildAgentDraft();
        entity.setDraftCode(code);
        entity.setDraftName(StringUtils.substring(
            StringUtils.blankToDefault(bo.getDraftName(), code), 0, 128));
        entity.setAgentCode(bo.getAgentCode() == null ? null : bo.getAgentCode().trim());
        entity.setAgentName(bo.getAgentName());
        entity.setAgentCategory(bo.getAgentCategory() == null ? null : bo.getAgentCategory().trim());
        entity.setParentAgentCode(bo.getParentAgentCode());
        entity.setRoleDescription(bo.getRoleDescription());
        entity.setObjective(bo.getObjective());
        entity.setProhibitions(bo.getProhibitions());
        entity.setOutputSummary(bo.getOutputSummary());
        entity.setPromptSectionsJson(bo.getPromptSectionsJson());
        entity.setProviderCapability(bo.getProviderCapability() == null
            ? null : bo.getProviderCapability().trim());
        entity.setAllowExternal(trimToNull(bo.getAllowExternal()));
        entity.setAllowedTools(bo.getAllowedTools());
        entity.setKnowledgeScope(bo.getKnowledgeScope());
        entity.setOwnerId(actorId);
        entity.setOrgId(bo.getOrgId());
        entity.setEnabled(enabledOf(bo.getEnabled()));
        entity.setDelFlag("0");
        entity.setRemark(bo.getRemark());
        childAgentDraftMapper.insert(entity);
        log.info("新增子 Agent 草案, childAgentDraftId={}, draftCode={}, agentCategory={}",
            entity.getChildAgentDraftId(), code, entity.getAgentCategory());
        return entity.getChildAgentDraftId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActionDraft(AigStudioActionDraftBo bo, Long actorId) {
        requireActor(actorId);
        if (bo == null) {
            throw new ServiceException("快捷指令草案入参不能为空");
        }
        String code = requireCode(bo.getDraftCode());
        if (actionDraftMapper.selectCount(new LambdaQueryWrapper<AigStudioActionDraft>()
            .eq(AigStudioActionDraft::getDraftCode, code)) > 0) {
            throw new ServiceException("快捷指令草案编码已存在：" + code);
        }
        AigStudioActionDraft entity = new AigStudioActionDraft();
        entity.setDraftCode(code);
        entity.setDraftName(StringUtils.substring(
            StringUtils.blankToDefault(bo.getDraftName(), code), 0, 128));
        entity.setActionCode(trimToNull(bo.getActionCode()));
        entity.setActionName(bo.getActionName());
        entity.setLaunchMode(trimToNull(bo.getLaunchMode()));
        entity.setTargetType(trimToNull(bo.getTargetType()));
        entity.setTargetRef(trimToNull(bo.getTargetRef()));
        entity.setAgentCategory(bo.getAgentCategory() == null ? null : bo.getAgentCategory().trim());
        entity.setObjective(bo.getObjective());
        entity.setOutputContract(bo.getOutputContract());
        entity.setProviderCapability(bo.getProviderCapability() == null
            ? null : bo.getProviderCapability().trim());
        entity.setAllowedTools(bo.getAllowedTools());
        entity.setOwnerId(actorId);
        entity.setOrgId(bo.getOrgId());
        entity.setEnabled(enabledOf(bo.getEnabled()));
        entity.setDelFlag("0");
        entity.setRemark(bo.getRemark());
        actionDraftMapper.insert(entity);
        log.info("新增快捷指令草案, actionDraftId={}, draftCode={}, launchMode={}",
            entity.getActionDraftId(), code, entity.getLaunchMode());
        return entity.getActionDraftId();
    }

    /* ------------------------------------------------------------------ 清单 */

    @Override
    public PageResult<AigStudioSkillDraftVo> querySkillDraftPage(AigStudioArtifactDraftQueryBo bo,
                                                                PageQuery pageQuery) {
        AigStudioArtifactDraftQueryBo query = bo == null ? new AigStudioArtifactDraftQueryBo() : bo;
        Page<AigStudioSkillDraftVo> page = skillDraftMapper.selectVoPage(pageOf(pageQuery),
            new LambdaQueryWrapper<AigStudioSkillDraft>()
                .like(StringUtils.isNotBlank(query.getDraftCode()),
                    AigStudioSkillDraft::getDraftCode, query.getDraftCode())
                .like(StringUtils.isNotBlank(query.getDraftName()),
                    AigStudioSkillDraft::getDraftName, query.getDraftName())
                .eq(StringUtils.isNotBlank(query.getEnabled()),
                    AigStudioSkillDraft::getEnabled, query.getEnabled())
                .eq(query.getOrgId() != null, AigStudioSkillDraft::getOrgId, query.getOrgId())
                .orderByDesc(AigStudioSkillDraft::getCreateTime));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<AigStudioChildAgentDraftVo> queryChildAgentDraftPage(
        AigStudioArtifactDraftQueryBo bo, PageQuery pageQuery) {
        AigStudioArtifactDraftQueryBo query = bo == null ? new AigStudioArtifactDraftQueryBo() : bo;
        Page<AigStudioChildAgentDraftVo> page = childAgentDraftMapper.selectVoPage(pageOf(pageQuery),
            new LambdaQueryWrapper<AigStudioChildAgentDraft>()
                .like(StringUtils.isNotBlank(query.getDraftCode()),
                    AigStudioChildAgentDraft::getDraftCode, query.getDraftCode())
                .like(StringUtils.isNotBlank(query.getDraftName()),
                    AigStudioChildAgentDraft::getDraftName, query.getDraftName())
                .eq(StringUtils.isNotBlank(query.getEnabled()),
                    AigStudioChildAgentDraft::getEnabled, query.getEnabled())
                .eq(query.getOrgId() != null, AigStudioChildAgentDraft::getOrgId, query.getOrgId())
                .orderByDesc(AigStudioChildAgentDraft::getCreateTime));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<AigStudioActionDraftVo> queryActionDraftPage(AigStudioArtifactDraftQueryBo bo,
                                                                   PageQuery pageQuery) {
        AigStudioArtifactDraftQueryBo query = bo == null ? new AigStudioArtifactDraftQueryBo() : bo;
        Page<AigStudioActionDraftVo> page = actionDraftMapper.selectVoPage(pageOf(pageQuery),
            new LambdaQueryWrapper<AigStudioActionDraft>()
                .like(StringUtils.isNotBlank(query.getDraftCode()),
                    AigStudioActionDraft::getDraftCode, query.getDraftCode())
                .like(StringUtils.isNotBlank(query.getDraftName()),
                    AigStudioActionDraft::getDraftName, query.getDraftName())
                .eq(StringUtils.isNotBlank(query.getEnabled()),
                    AigStudioActionDraft::getEnabled, query.getEnabled())
                .eq(query.getOrgId() != null, AigStudioActionDraft::getOrgId, query.getOrgId())
                .orderByDesc(AigStudioActionDraft::getCreateTime));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /* ------------------------------------------------------------------ 生成 */

    @Override
    public AigStudioArtifactGenerateVo preview(AigStudioArtifactKindEnum kind, Long draftId) {
        return build(kind, draftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigStudioArtifactGenerateVo generate(AigStudioArtifactKindEnum kind, Long draftId,
                                                Long targetDraftId, Integer expectedRevision,
                                                Long actorId) {
        if (targetDraftId == null) {
            throw new ServiceException("必须指定要落到哪个训练草稿（targetDraftId）");
        }
        AigStudioArtifactGenerateVo generated = build(kind, draftId);
        AigStudioDraftDetailVo updated = draftService.applyRevision(targetDraftId, expectedRevision,
            generated.getContent(), AigStudioRevisionSourceEnum.DRAFT_GENERATED,
            "由" + kind.getDesc() + "「" + generated.getSourceDraftCode() + "」生成", actorId);
        if (!Boolean.TRUE.equals(updated.getRevisionCreated())) {
            // 内容与目标草稿当前内容一致：不返回"成功但什么都没发生"，那会让人以为已经生成过了
            throw new ServiceException("目标草稿的内容与生成结果一致，未产生新修订："
                + "无需再生成（如需覆盖，请先修改草案或目标草稿任一方的差异字段）");
        }
        generated.setTargetDraftId(targetDraftId);
        generated.setRevision(updated.getLatestRevision());
        generated.setContentHash(updated.getContentHash());
        generated.setRevisionCreated(true);
        log.info("草案已生成到训练草稿, kind={}, sourceDraftId={}, targetDraftId={}, revision={}",
            kind.getCode(), draftId, targetDraftId, updated.getLatestRevision());
        return generated;
    }

    /* ------------------------------------------------------------------ 内部 */

    /**
     * 生成（预览与落库共用的唯一生成逻辑）。
     *
     * @param kind    草案类型
     * @param draftId 草案ID
     * @return 生成结果（不含目标草稿与修订）
     */
    private AigStudioArtifactGenerateVo build(AigStudioArtifactKindEnum kind, Long draftId) {
        if (kind == null) {
            throw new ServiceException("未知的草案类型；可选：" + AigStudioArtifactKindEnum.describe());
        }
        if (draftId == null) {
            throw new ServiceException("草案ID不能为空");
        }
        AigStudioArtifactDraftGenerator.Result result;
        String draftCode;
        try {
            // requireDraft 已保证草案存在；这里按类型分派
            switch (kind) {
                case SKILL -> {
                    AigStudioSkillDraft draft = requireSkillDraft(draftId);
                    draftCode = draft.getDraftCode();
                    result = AigStudioArtifactDraftGenerator.forSkill(draft);
                }
                case CHILD_AGENT -> {
                    AigStudioChildAgentDraft draft = requireChildAgentDraft(draftId);
                    draftCode = draft.getDraftCode();
                    result = AigStudioArtifactDraftGenerator.forChildAgent(draft);
                }
                case ACTION -> {
                    AigStudioActionDraft draft = requireActionDraft(draftId);
                    draftCode = draft.getDraftCode();
                    result = AigStudioArtifactDraftGenerator.forAction(draft);
                }
                default -> throw new ServiceException("未知的草案类型：" + kind.getCode());
            }
        } catch (IllegalArgumentException e) {
            // 生成规则拒绝（缺类别/能力、分节 JSON 非法等）统一转成业务异常
            throw new ServiceException("草案无法生成：" + e.getMessage());
        }

        AigStudioArtifactGenerateVo vo = new AigStudioArtifactGenerateVo();
        vo.setKind(kind.getCode());
        vo.setSourceDraftId(draftId);
        vo.setSourceDraftCode(draftCode);
        vo.setContent(result.content());
        vo.setNotes(result.notes() == null ? List.of() : result.notes());
        return vo;
    }

    /**
     * 读一份启用中的技能草案。
     *
     * @param draftId 草案ID
     * @return 草案
     */
    private AigStudioSkillDraft requireSkillDraft(Long draftId) {
        AigStudioSkillDraft draft = skillDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new ServiceException("技能草案不存在：" + draftId);
        }
        requireEnabled(YES.equals(draft.getEnabled()), "技能草案", draftId);
        return draft;
    }

    /**
     * 读一份启用中的子 Agent 草案。
     *
     * @param draftId 草案ID
     * @return 草案
     */
    private AigStudioChildAgentDraft requireChildAgentDraft(Long draftId) {
        AigStudioChildAgentDraft draft = childAgentDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new ServiceException("子 Agent 草案不存在：" + draftId);
        }
        requireEnabled(YES.equals(draft.getEnabled()), "子 Agent 草案", draftId);
        return draft;
    }

    /**
     * 读一份启用中的快捷指令草案。
     *
     * @param draftId 草案ID
     * @return 草案
     */
    private AigStudioActionDraft requireActionDraft(Long draftId) {
        AigStudioActionDraft draft = actionDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new ServiceException("快捷指令草案不存在：" + draftId);
        }
        requireEnabled(YES.equals(draft.getEnabled()), "快捷指令草案", draftId);
        return draft;
    }

    /**
     * 停用的草案不能用于生成（停用正是为了"别再拿它生成"）。
     *
     * @param enabled 是否启用
     * @param label   草案类型中文名
     * @param draftId 草案ID
     */
    private void requireEnabled(boolean enabled, String label, Long draftId) {
        if (!enabled) {
            throw new ServiceException(label + "已停用，不能用于生成：" + draftId);
        }
    }

    /**
     * 校验草案编码。
     *
     * @param draftCode 编码
     * @return 规范化后的编码
     */
    private String requireCode(String draftCode) {
        if (StringUtils.isBlank(draftCode)) {
            throw new ServiceException("草案编码不能为空");
        }
        return draftCode.trim();
    }

    /**
     * 必须有明确的操作人（草案要记责任人）。
     *
     * @param actorId 操作用户ID
     */
    private void requireActor(Long actorId) {
        if (actorId == null) {
            throw new ServiceException("草案需要明确的操作用户：无法确定责任人");
        }
    }

    /**
     * 启用标志缺省值。
     *
     * @param enabled 入参
     * @return Y/N
     */
    private static String enabledOf(String enabled) {
        return StringUtils.isBlank(enabled) ? YES : enabled.trim().toUpperCase();
    }

    /**
     * 去空白；空白返回 null（避免落空串进可空列）。
     *
     * @param value 值
     * @return 结果
     */
    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 分页参数兜底。
     *
     * @param pageQuery 分页参数
     * @return 分页参数（非空）
     */
    private static <T> Page<T> pageOf(PageQuery pageQuery) {
        return (pageQuery == null ? new PageQuery() : pageQuery).build();
    }

}
