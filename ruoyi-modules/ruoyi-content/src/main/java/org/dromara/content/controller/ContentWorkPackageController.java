package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.vo.CpWorkPackageVo;
import org.dromara.content.service.IContentWorkPackageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设计开工包 控制层。
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/workPackage")
public class ContentWorkPackageController {

    /**
     * 开工包服务
     */
    private final IContentWorkPackageService workPackageService;

    /**
     * 生成开工包（草稿）。不满足开工条件时返回可读的阻断说明。
     *
     * @param taskId 任务ID
     * @return 开工包ID
     */
    @SaCheckPermission(ContentConstants.PERM_PACKAGE_GENERATE)
    @RepeatSubmit
    @Log(title = "设计开工包", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public R<Long> generate(@NotNull(message = "任务ID不能为空") @RequestParam("taskId") Long taskId) {
        return R.ok(workPackageService.generate(taskId));
    }

    /**
     * 签发开工包。
     *
     * @param packageId 开工包ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_PACKAGE_ISSUE)
    @RepeatSubmit
    @Log(title = "设计开工包", businessType = BusinessType.UPDATE)
    @PostMapping("/{packageId}/issue")
    public R<Void> issue(@NotNull(message = "开工包ID不能为空") @PathVariable("packageId") Long packageId) {
        workPackageService.issue(packageId);
        return R.ok();
    }

    /**
     * 查询任务的最新开工包。
     *
     * @param taskId 任务ID
     * @return 开工包，未生成时 data 为 null
     */
    @SaCheckPermission(ContentConstants.PERM_PACKAGE_LIST)
    @GetMapping("/byTask/{taskId}")
    public R<CpWorkPackageVo> byTask(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(workPackageService.getByTask(taskId));
    }

}
