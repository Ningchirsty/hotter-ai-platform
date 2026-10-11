package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.domain.vo.AigStudioCopilotTurnVo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioCopilotService;
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
 * 训练台「对话式 Copilot」接口（附件 07 增量 6）。
 *
 * <h3>两个入口，两种权限</h3>
 * <ul>
 *     <li>发起对话：{@link AigConstants#PERM_STUDIO_COPILOT_CHAT}——<b>它花钱</b>
 *         （真实模型调用），因此与"看提案/处理提案"分开授权；</li>
 *     <li>读会话历史：{@link AigConstants#PERM_STUDIO_COPILOT_USE}——与提案面板同一权限。</li>
 * </ul>
 *
 * <h3>这一层没有"直接采纳"</h3>
 * <p>对话只产提案。采纳仍在 {@code /copilot/proposals/{id}/apply}，由人显式发起——
 * 一旦让"对话顺便把草稿改了"，"模型改的"与"人改的"就又混在一起了。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/studio")
public class AigStudioCopilotController extends BaseController {

    private final IAigStudioCopilotService copilotService;
    private final AigStudioActorProvider actorProvider;

    /**
     * 与培训助手对话一轮（会真实调用模型；草稿不会被改动）。
     *
     * @param draftId 草稿ID
     * @param bo      入参
     * @return 本轮结论（含产出的提案ID，或被拒绝/失败的原因）
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_CHAT)
    @RepeatSubmit
    @PostMapping("/drafts/{draftId:\\d+}/copilot/chat")
    public R<AigStudioCopilotTurnVo> chat(@NotNull(message = "草稿ID不能为空")
                                          @PathVariable Long draftId,
                                          @RequestBody @Validated AigStudioCopilotChatBo bo) {
        return R.ok(copilotService.chat(draftId, bo, requireActor()));
    }

    /**
     * 会话历史（最近若干条，最新在前）。
     *
     * @param draftId 草稿ID
     * @return 轮次清单
     */
    @SaCheckPermission(AigConstants.PERM_STUDIO_COPILOT_USE)
    @GetMapping("/drafts/{draftId:\\d+}/copilot/turns")
    public R<List<AigStudioCopilotTurnVo>> turns(@NotNull(message = "草稿ID不能为空")
                                                 @PathVariable Long draftId) {
        return R.ok(copilotService.turns(draftId));
    }

    /**
     * 取当前操作者；取不到就拒绝（这一轮要花钱，必须能追到人）。
     *
     * @return 用户ID
     */
    private Long requireActor() {
        Long actorId = actorProvider.currentUserId();
        if (actorId == null) {
            throw new ServiceException("对话式 Copilot 需要登录用户：无法确定这次对话是谁发起的");
        }
        return actorId;
    }

}
