package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioActionDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactGenerateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioChildAgentDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioSkillDraftBo;
import org.dromara.aigov.studio.domain.vo.AigStudioActionDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioArtifactGenerateVo;
import org.dromara.aigov.studio.domain.vo.AigStudioChildAgentDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioSkillDraftVo;
import org.dromara.aigov.studio.enums.AigStudioArtifactKindEnum;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioArtifactDraftService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 草案生成器接口（附件 07 增量 7／ST-005/006/007）。
 *
 * <h3>三个写入口，两种权限</h3>
 * <ul>
 *     <li>新增草案：{@link AigConstants#PERM_STUDIO_ARTIFACT_DRAFT}——草案不改任何草稿，
 *         所以它不需要"编辑草稿"的权限；</li>
 *     <li>生成到训练草稿：{@link AigConstants#PERM_STUDIO_DRAFT_EDIT}——生成是<b>写草稿</b>
 *         （产生一条新修订），与人工保存/回滚同一条通道、同一个权限。</li>
 * </ul>
 *
 * <h3>草案类型走路径片段而不是请求体</h3>
 * <p>{@code /{kind}/...} 用 {@link AigStudioArtifactKindEnum#findByPath} 解析；
 * 认不出的片段直接报错并列出可选值，而不是悄悄退化成某一类。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio/artifact-drafts")
public class AigStudioArtifactDraftController extends BaseController {

    private final IAigStudioArtifactDraftService artifactDraftService;
    private final AigStudioActorProvider actorProvider;

    /* ------------------------------------------------------------------ 新增草案 */

    /**
     * 新增技能草案。
     *
     * @param bo 入参
     * @return 草案ID
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @RepeatSubmit
    @PostMapping("/skill")
    public R<Long> createSkillDraft(@RequestBody @Validated AigStudioSkillDraftBo bo) {
        return R.ok(artifactDraftService.createSkillDraft(bo, requireActor()));
    }

    /**
     * 新增子 Agent 草案。
     *
     * @param bo 入参
     * @return 草案ID
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @RepeatSubmit
    @PostMapping("/child-agent")
    public R<Long> createChildAgentDraft(@RequestBody @Validated AigStudioChildAgentDraftBo bo) {
        return R.ok(artifactDraftService.createChildAgentDraft(bo, requireActor()));
    }

    /**
     * 新增快捷指令草案。
     *
     * @param bo 入参
     * @return 草案ID
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @RepeatSubmit
    @PostMapping("/action")
    public R<Long> createActionDraft(@RequestBody @Validated AigStudioActionDraftBo bo) {
        return R.ok(artifactDraftService.createActionDraft(bo, requireActor()));
    }

    /* ------------------------------------------------------------------ 清单 */

    /**
     * 技能草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @GetMapping("/skill/list")
    public R<PageResult<AigStudioSkillDraftVo>> skillDraftList(
        @Validated({Default.class, QueryGroup.class}) AigStudioArtifactDraftQueryBo bo,
        PageQuery pageQuery) {
        return R.ok(artifactDraftService.querySkillDraftPage(bo, pageQuery));
    }

    /**
     * 子 Agent 草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @GetMapping("/child-agent/list")
    public R<PageResult<AigStudioChildAgentDraftVo>> childAgentDraftList(
        @Validated({Default.class, QueryGroup.class}) AigStudioArtifactDraftQueryBo bo,
        PageQuery pageQuery) {
        return R.ok(artifactDraftService.queryChildAgentDraftPage(bo, pageQuery));
    }

    /**
     * 快捷指令草案清单。
     *
     * @param bo        查询条件
     * @param pageQuery 分页
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @GetMapping("/action/list")
    public R<PageResult<AigStudioActionDraftVo>> actionDraftList(
        @Validated({Default.class, QueryGroup.class}) AigStudioArtifactDraftQueryBo bo,
        PageQuery pageQuery) {
        return R.ok(artifactDraftService.queryActionDraftPage(bo, pageQuery));
    }

    /* ------------------------------------------------------------------ 生成 */

    /**
     * 预览生成物（只读；不写任何表）。
     *
     * @param kind    草案类型路径片段（skill / child-agent / action）
     * @param draftId 草案ID
     * @return 生成结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_ARTIFACT_DRAFT)
    @GetMapping("/{kind}/{draftId:\\d+}/preview")
    public R<AigStudioArtifactGenerateVo> preview(@PathVariable String kind,
                                                  @PathVariable @NotNull(message = "草案ID不能为空")
                                                  Long draftId) {
        return R.ok(artifactDraftService.preview(requireKind(kind), draftId));
    }

    /**
     * 把生成物落成目标训练草稿的一条新修订（写草稿，因此要「编辑草稿」权限）。
     *
     * @param kind    草案类型路径片段
     * @param draftId 草案ID
     * @param bo      入参（目标草稿 + 期望修订号）
     * @return 生成结果（含目标草稿与修订号）
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_EDIT)
    @RepeatSubmit
    @PostMapping("/{kind}/{draftId:\\d+}/generate")
    public R<AigStudioArtifactGenerateVo> generate(@PathVariable String kind,
                                                   @PathVariable @NotNull(message = "草案ID不能为空")
                                                   Long draftId,
                                                   @RequestBody @Validated AigStudioArtifactGenerateBo bo) {
        return R.ok(artifactDraftService.generate(requireKind(kind), draftId, bo.getTargetDraftId(),
            bo.getExpectedRevision(), requireActor()));
    }

    /**
     * 解析草案类型路径片段。
     *
     * @param kind 路径片段
     * @return 枚举
     */
    private AigStudioArtifactKindEnum requireKind(String kind) {
        AigStudioArtifactKindEnum found = AigStudioArtifactKindEnum.findByPath(kind);
        if (found == null) {
            throw new ServiceException("未知的草案类型：" + kind
                + "；可选：" + describeKinds());
        }
        return found;
    }

    /**
     * 可选草案类型（路径片段）。
     *
     * @return 形如 {@code skill / child-agent / action}
     */
    private String describeKinds() {
        List<String> segments = new java.util.ArrayList<>();
        for (AigStudioArtifactKindEnum item : AigStudioArtifactKindEnum.values()) {
            segments.add(item.getPathSegment());
        }
        return String.join(" / ", segments);
    }

    /**
     * 取当前操作者；取不到就拒绝。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("草案生成器需要登录用户：无法确定草案责任人/操作人");
        }
        return actorId;
    }

}
