package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.vo.DpDetailPageVo;
import org.dromara.creative.service.ICreativeLayoutService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 详情页排版与终审 控制层（R3.3）。
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}/detail-page")
public class CreativeLayoutController {

    private final ICreativeLayoutService layoutService;

    /**
     * 详情页与版本列表。
     *
     * @param taskId 项目ID
     * @return 详情页（未排版时 currentVersion=0）
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping
    public R<DpDetailPageVo> detail(@NotNull(message = "项目ID不能为空")
                                    @PathVariable("taskId") Long taskId) {
        return R.ok(layoutService.detail(taskId));
    }

    /**
     * 渲染一版机排版 V0.8（750×N 长图）。
     *
     * @param taskId 项目ID
     * @return 详情页（含新版本）
     */
    @SaCheckPermission(CreativeConstants.PERM_LAYOUT_RENDER)
    @Log(title = "详情页机排版", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/render")
    public R<DpDetailPageVo> render(@NotNull(message = "项目ID不能为空")
                                    @PathVariable("taskId") Long taskId) {
        return R.ok(layoutService.render(taskId));
    }

    /**
     * 终审：通过或打回某个版本。
     *
     * @param taskId    项目ID
     * @param versionId 版本ID
     * @param approve   是否通过
     * @param comment   意见
     * @return 更新后的版本
     */
    @SaCheckPermission(CreativeConstants.PERM_FINAL_REVIEW)
    @Log(title = "详情页终审", businessType = BusinessType.UPDATE)
    @PostMapping("/versions/{versionId}/review")
    public R<DpDetailPageVo.DpDetailPageVersionVo> review(
        @NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
        @NotNull(message = "版本ID不能为空") @PathVariable("versionId") Long versionId,
        @RequestParam("approve") boolean approve,
        @RequestParam(value = "comment", required = false) String comment) {
        return R.ok(layoutService.review(taskId, versionId, approve, comment));
    }

    /**
     * 上传人工精修后的最终版 V1.0。
     *
     * @param taskId  项目ID
     * @param file    精修长图
     * @param comment 说明
     * @return 详情页
     */
    @SaCheckPermission(CreativeConstants.PERM_FINAL_SUBMIT)
    @Log(title = "详情页最终版上传", businessType = BusinessType.INSERT)
    @PostMapping("/final")
    public R<DpDetailPageVo> uploadFinal(@NotNull(message = "项目ID不能为空")
                                         @PathVariable("taskId") Long taskId,
                                         @RequestPart("file") MultipartFile file,
                                         @RequestParam(value = "comment", required = false) String comment) {
        return R.ok(layoutService.uploadFinal(taskId, file, comment));
    }

    /**
     * 版本长图预览（后端代理，不暴露对象存储直链）。
     *
     * @param taskId    项目ID
     * @param versionId 版本ID
     * @return 长图字节
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping("/versions/{versionId}/preview")
    public ResponseEntity<byte[]> preview(@NotNull(message = "项目ID不能为空")
                                          @PathVariable("taskId") Long taskId,
                                          @NotNull(message = "版本ID不能为空")
                                          @PathVariable("versionId") Long versionId) {
        byte[] bytes = layoutService.preview(taskId, versionId);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(bytes);
    }

}
