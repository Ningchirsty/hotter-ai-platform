package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftRollbackBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSubmitBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftSubmitVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioRevisionVo;
import org.dromara.aigov.studio.domain.vo.AigStudioValidateVo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent Studio 训练台草稿接口（专题 C §C9）。
 *
 * <h3>这一层只做"草稿"，不做发布</h3>
 * <p>训练台里的「部署」不是直改状态：正式发布只能经
 * {@code /aigov/agent/release/advance} 那台状态机（五道门槛）。
 * 本控制器里<b>没有任何推进发布状态的入口</b>——第二条写通道一旦存在，
 * 门槛就形同虚设。</p>
 *
 * <h3>操作者从登录态取，不从请求体取</h3>
 * <p>责任人/修订人是"谁在操作"的事实，不能由调用方自报（否则可以冒名改别人的草稿）。
 * 取不到登录用户时让服务层报错，而不是当成系统身份放行。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio/drafts")
public class AigStudioDraftController extends BaseController {

    private final IAigStudioDraftService draftService;
    private final AigStudioActorProvider actorProvider;

    /**
     * 分页查询训练草稿。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_LIST)
    @GetMapping("/list")
    public R<PageResult<AigStudioDraftVo>> list(AigStudioDraftQueryBo bo, PageQuery pageQuery) {
        return R.ok(draftService.queryPage(bo, pageQuery));
    }

    /**
     * 读取草稿详情（含是否有未提交改动）。
     *
     * @param draftId 草稿ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_QUERY)
    @GetMapping("/{draftId:\\d+}")
    public R<AigStudioDraftDetailVo> get(@NotNull(message = "草稿ID不能为空")
                                         @PathVariable Long draftId) {
        return R.ok(draftService.getDraft(draftId));
    }

    /**
     * 新建训练草稿（同时产生第 1 个修订）。
     *
     * @param bo 入参
     * @return 草稿ID
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_CREATE)
    @RepeatSubmit
    @PostMapping
    public R<Long> create(@RequestBody @Validated AigStudioDraftCreateBo bo) {
        return R.ok(draftService.createDraft(bo, requireActor()));
    }

    /**
     * 保存草稿（CAS：必须带 expectedRevision）。
     *
     * @param draftId 草稿ID
     * @param bo      入参（路径参数覆盖请求体里的 ID）
     * @return 保存后的详情
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_EDIT)
    @PutMapping("/{draftId:\\d+}")
    public R<AigStudioDraftDetailVo> update(@NotNull(message = "草稿ID不能为空")
                                            @PathVariable Long draftId,
                                            @RequestBody @Validated AigStudioDraftSaveBo bo) {
        // 以路径为准：请求体里的 draftId 与路径不一致时，若信请求体就会出现"改 A 却提交到 B"的越权面
        bo.setDraftId(draftId);
        return R.ok(draftService.saveDraft(bo, requireActor()));
    }

    /**
     * 修订历史（最新在前）。
     *
     * @param draftId 草稿ID
     * @return 修订列表
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_QUERY)
    @GetMapping("/{draftId:\\d+}/revisions")
    public R<List<AigStudioRevisionVo>> revisions(@NotNull(message = "草稿ID不能为空")
                                                  @PathVariable Long draftId) {
        return R.ok(draftService.listRevisions(draftId));
    }

    /**
     * 读取某个修订的内容快照（Diff / 回滚确认用）。
     *
     * @param revisionId 修订ID
     * @return 修订
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_QUERY)
    @GetMapping("/revision/{revisionId:\\d+}")
    public R<AigStudioRevisionVo> revision(@NotNull(message = "修订ID不能为空")
                                           @PathVariable Long revisionId) {
        return R.ok(draftService.getRevision(revisionId));
    }

    /**
     * 预检草稿当前内容（只读校验，不产生版本）。
     *
     * @param draftId 草稿ID
     * @return 预检结论
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_VALIDATE)
    @PostMapping("/{draftId:\\d+}/validate")
    public R<AigStudioValidateVo> validate(@NotNull(message = "草稿ID不能为空")
                                           @PathVariable Long draftId) {
        return R.ok(draftService.validateDraft(draftId));
    }

    /**
     * 回滚到某个历史修订（产生新修订，不改历史）。
     *
     * @param draftId 草稿ID
     * @param bo      入参
     * @return 回滚后的详情
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_EDIT)
    @PostMapping("/{draftId:\\d+}/rollback")
    public R<AigStudioDraftDetailVo> rollback(@NotNull(message = "草稿ID不能为空")
                                              @PathVariable Long draftId,
                                              @RequestBody @Validated AigStudioDraftRollbackBo bo) {
        return R.ok(draftService.rollback(draftId, bo.getTargetRevisionNo(),
            bo.getExpectedRevision(), requireActor()));
    }

    /**
     * 提交草稿：固化成一条 <b>DRAFT</b> Agent 版本（不推进发布状态）。
     *
     * @param draftId 草稿ID
     * @param bo      入参（版本号可空）
     * @return 提交结果
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_SUBMIT)
    @RepeatSubmit
    @PostMapping("/{draftId:\\d+}/submit")
    public R<AigStudioDraftSubmitVo> submit(@NotNull(message = "草稿ID不能为空")
                                            @PathVariable Long draftId,
                                            @RequestBody(required = false) AigStudioDraftSubmitBo bo) {
        return R.ok(draftService.submitDraft(draftId, bo, requireActor()));
    }

    /**
     * 归档草稿（终态）。
     *
     * @param draftId 草稿ID
     * @return 空
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_DRAFT_EDIT)
    @PostMapping("/{draftId:\\d+}/archive")
    public R<Void> archive(@NotNull(message = "草稿ID不能为空") @PathVariable Long draftId) {
        draftService.archive(draftId, requireActor());
        return R.ok();
    }

    /**
     * 取当前操作者；取不到就拒绝。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("训练台需要登录用户：无法确定这次编辑是谁做的");
        }
        return actorId;
    }

}
