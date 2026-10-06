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
import org.springframework.web.multipart.MultipartFile;

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

    // ------------------------------------------------------------------
    // 上传资料并识别（v1 人工测试反馈 详情页与审核 1.3）
    //
    // 权限用 `creative:project:upload`（设计师本来就有，语义是"给这个项目补资料"）：
    // 内容侧的 `content:task:edit` 是品牌侧写权限，内测里"同一账号同时拥有品牌侧与设计侧
    // 全部权限"本身就是被反馈过的问题（S7），不能为了这个功能把它加回来。
    // ------------------------------------------------------------------

    /**
     * 视觉门里「上传资料并识别」的现状（资料列表 + 各自解析状态 + 待确认/已确认条数）。
     *
     * @param taskId 项目ID
     * @return 现状
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping("/materials")
    public R<ICreativeGateService.GateMaterials> materials(@NotNull(message = "项目ID不能为空")
                                                           @PathVariable("taskId") Long taskId) {
        return R.ok(gateService.materials(taskId));
    }

    /**
     * 在视觉门里上传一份资料（文档或图片，≤ 20MB），供随后的「识别」使用。
     *
     * @param taskId 项目ID
     * @param file   文件
     * @return 刚上传的那一份（含初始解析状态）
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_UPLOAD)
    @Log(title = "视觉门资料上传", businessType = BusinessType.INSERT)
    @PostMapping("/materials")
    public R<ICreativeGateService.GateMaterial> uploadMaterial(@NotNull(message = "项目ID不能为空")
                                                               @PathVariable("taskId") Long taskId,
                                                               @RequestParam("file") MultipartFile file) {
        return R.ok(gateService.uploadMaterial(taskId, file));
    }

    /**
     * 触发识别（异步）：走内容侧的文档解析链路，识别结果一律以待确认落库。
     *
     * @param taskId 项目ID
     * @return 异步作业ID
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_UPLOAD)
    @Log(title = "视觉门资料识别", businessType = BusinessType.UPDATE)
    @PostMapping("/materials/parse")
    public R<Long> parseMaterials(@NotNull(message = "项目ID不能为空")
                                  @PathVariable("taskId") Long taskId) {
        return R.ok(gateService.triggerMaterialParse(taskId));
    }

}
