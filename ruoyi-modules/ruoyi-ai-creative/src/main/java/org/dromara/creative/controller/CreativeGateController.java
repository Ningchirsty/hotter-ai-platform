package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.service.ICreativeGateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视觉门 控制层。
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}/visual-gate")
public class CreativeGateController {

    private final ICreativeGateService gateService;

    /**
     * 门禁评估（准入项 + 审批卡状态）。
     *
     * @param taskId 项目ID
     * @return 评估结果
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping
    public R<ICreativeGateService.GateEvaluation> evaluate(@NotNull(message = "项目ID不能为空")
                                                           @PathVariable("taskId") Long taskId) {
        return R.ok(gateService.evaluate(taskId));
    }

    /**
     * 提交视觉门审核（硬性项未满足时拒绝并列出原因）。
     *
     * @param taskId 项目ID
     * @return 提交后的评估结果
     */
    @SaCheckPermission(CreativeConstants.PERM_GATE_SUBMIT)
    @Log(title = "视觉门提交", businessType = BusinessType.INSERT)
    @PostMapping("/submit")
    public R<ICreativeGateService.GateEvaluation> submit(@NotNull(message = "项目ID不能为空")
                                                         @PathVariable("taskId") Long taskId) {
        return R.ok(gateService.submit(taskId));
    }

    /**
     * 处理视觉门（人工确认或打回）。
     *
     * @param taskId  项目ID
     * @param option  CONFIRM / BLOCK
     * @param comment 意见
     * @return 处理后的评估结果
     */
    @SaCheckPermission(CreativeConstants.PERM_GATE_REVIEW)
    @Log(title = "视觉门审核", businessType = BusinessType.UPDATE)
    @PostMapping("/review")
    public R<ICreativeGateService.GateEvaluation> review(@NotNull(message = "项目ID不能为空")
                                                         @PathVariable("taskId") Long taskId,
                                                         @RequestParam("option") String option,
                                                         @RequestParam(value = "comment", required = false)
                                                         String comment) {
        return R.ok(gateService.review(taskId, option, comment));
    }

}
