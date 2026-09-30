package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.vo.DeliveryVo;
import org.dromara.creative.service.ICreativeDeliveryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交付渲染 控制层（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>一个入口，渲染器由配置决定</b>：详情页（render_mode=LONGPAGE）走长图排版，
 * 商品主图（MULTI_IMAGE）走多图打包——调用方不需要知道区别。想验证"不匹配的渲染器会被拒"
 * 时可以显式带 {@code ?renderer=} 。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}/delivery")
public class CreativeDeliveryController {

    private final ICreativeDeliveryService deliveryService;

    /**
     * 交付视图（历史版本 + 渲染器能力清单）。
     *
     * @param taskId 项目ID
     * @return 交付视图
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping
    public R<DeliveryVo> view(@NotNull(message = "项目ID不能为空")
                              @PathVariable("taskId") Long taskId) {
        return R.ok(deliveryService.view(taskId));
    }

    /**
     * 渲染一次交付产物（按交付类型的渲染模式自动选渲染器）。
     *
     * @param taskId   项目ID
     * @param renderer 指定渲染器（可空；排障/验收用）
     * @return 交付视图
     */
    @SaCheckPermission(CreativeConstants.PERM_LAYOUT_RENDER)
    @Log(title = "交付渲染", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/render")
    public R<DeliveryVo> render(@NotNull(message = "项目ID不能为空")
                                @PathVariable("taskId") Long taskId,
                                @RequestParam(value = "renderer", required = false) String renderer) {
        return R.ok(deliveryService.renderWith(taskId, renderer));
    }

    /**
     * 下载某版本的交付产物（单张长图给 PNG；多图交付给 ZIP，内含 manifest.json）。
     *
     * @param taskId    项目ID
     * @param versionId 交付清单ID
     * @return 产物字节
     */
    @SaCheckPermission(CreativeConstants.PERM_REVIEW_LIST)
    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<byte[]> download(@NotNull(message = "项目ID不能为空")
                                           @PathVariable("taskId") Long taskId,
                                           @NotNull(message = "版本ID不能为空")
                                           @PathVariable("versionId") Long versionId) {
        ICreativeDeliveryService.Download payload = deliveryService.download(taskId, versionId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(payload.contentType()))
            .header("Content-Disposition", "attachment; filename=\"" + payload.fileName() + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(payload.bytes());
    }
}
