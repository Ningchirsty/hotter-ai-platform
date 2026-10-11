package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalDecideBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
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
 * Agent Studio 变更提案接口（增量 21／附件 07 增量 6）。
 *
 * <h3>这一层只让"培训助手提案"，不让它改草稿</h3>
 * <p>创建提案的接口<b>不会</b>改草稿内容——采纳是另一个接口，且同样由草稿责任人发起。
 * 正式发布仍只能经 {@code /aigov/agent/release/advance}（五道门槛）。</p>
 *
 * <h3>操作者从登录态取，不从请求体取</h3>
 * <p>"谁提的、谁批的"是事实，不能由调用方自报。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio")
public class AigStudioProposalController extends BaseController {

    private final IAigStudioProposalService proposalService;
    private final AigStudioActorProvider actorProvider;

    /**
     * 对某个草稿提出一条变更提案（只留档，不改草稿）。
     *
     * @param draftId 草稿ID
     * @param bo      入参
     * @return 提案视图（含改动预览）
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_USE)
    @RepeatSubmit
    @PostMapping("/drafts/{draftId:\\d+}/copilot/proposals")
    public R<AigStudioChangeProposalVo> create(@NotNull(message = "草稿ID不能为空")
                                               @PathVariable Long draftId,
                                               @RequestBody @Validated AigStudioProposalCreateBo bo) {
        return R.ok(proposalService.create(draftId, bo, requireActor()));
    }

    /**
     * 列出某个草稿的变更提案（最新在前）。
     *
     * @param draftId 草稿ID
     * @return 提案列表
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_USE)
    @GetMapping("/drafts/{draftId:\\d+}/copilot/proposals")
    public R<List<AigStudioChangeProposalVo>> list(@NotNull(message = "草稿ID不能为空")
                                                   @PathVariable Long draftId) {
        return R.ok(proposalService.list(draftId));
    }

    /**
     * 采纳提案（产生一条 {@code COPILOT_ACCEPTED} 新修订）。
     *
     * @param proposalId 提案ID
     * @param bo         入参（必须带 expectedRevision）
     * @return 落库后的草稿详情
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_USE)
    @RepeatSubmit
    @PostMapping("/copilot/proposals/{proposalId:\\d+}/apply")
    public R<AigStudioDraftDetailVo> apply(@NotNull(message = "提案ID不能为空")
                                           @PathVariable Long proposalId,
                                           @RequestBody @Validated AigStudioProposalDecideBo bo) {
        return R.ok(proposalService.apply(proposalId, bo, requireActor()));
    }

    /**
     * 拒绝提案（内容不变）。
     *
     * @param proposalId 提案ID
     * @param bo         入参（原因可空）
     * @return 拒绝后的提案视图
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_USE)
    @RepeatSubmit
    @PostMapping("/copilot/proposals/{proposalId:\\d+}/reject")
    public R<AigStudioChangeProposalVo> reject(@NotNull(message = "提案ID不能为空")
                                               @PathVariable Long proposalId,
                                               @RequestBody(required = false) AigStudioProposalDecideBo bo) {
        return R.ok(proposalService.reject(proposalId, bo, requireActor()));
    }

    /**
     * 取当前操作者；取不到就拒绝。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("训练台需要登录用户：无法确定这次提案是谁提的/谁批的");
        }
        return actorId;
    }

}
