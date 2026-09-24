package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentProductBo;
import org.dromara.content.domain.vo.CpProductVo;
import org.dromara.content.service.IContentProductService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 轻量产品/SKU 控制层。
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/product")
public class ContentProductController {

    /**
     * 产品服务
     */
    private final IContentProductService productService;

    /**
     * 产品分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_LIST)
    @GetMapping("/list")
    public R<PageResult<CpProductVo>> list(@Validated({Default.class, QueryGroup.class}) ContentProductBo bo,
                                           PageQuery pageQuery) {
        return R.ok(productService.queryPage(bo, pageQuery));
    }

    /**
     * 产品下拉选项。
     *
     * @return 产品列表
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_LIST)
    @GetMapping("/options")
    public R<List<CpProductVo>> options() {
        return R.ok(productService.options());
    }

    /**
     * 产品详情。
     *
     * @param productId 产品ID
     * @return 产品
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_QUERY)
    @GetMapping("/{productId}")
    public R<CpProductVo> getInfo(@NotNull(message = "产品ID不能为空") @PathVariable("productId") Long productId) {
        return R.ok(productService.getDetail(productId));
    }

    /**
     * 新增产品。
     *
     * @param bo 产品参数
     * @return 产品ID
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_ADD)
    @RepeatSubmit
    @Log(title = "产品与SKU", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody ContentProductBo bo) {
        return R.ok(productService.create(bo));
    }

    /**
     * 修改产品。
     *
     * @param bo 产品参数
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_EDIT)
    @RepeatSubmit
    @Log(title = "产品与SKU", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody ContentProductBo bo) {
        productService.update(bo);
        return R.ok();
    }

    /**
     * 删除产品。
     *
     * @param productId 产品ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_REMOVE)
    @Log(title = "产品与SKU", businessType = BusinessType.DELETE)
    @DeleteMapping("/{productId}")
    public R<Void> remove(@NotNull(message = "产品ID不能为空") @PathVariable("productId") Long productId) {
        productService.remove(productId);
        return R.ok();
    }

    // ------------------------------------------------------------------
    // 产品图（R4）
    // ------------------------------------------------------------------

    /**
     * 把一个任务附件设为该产品的产品图。
     *
     * @param productId 产品ID
     * @param body      请求体，字段 {@code fileId}
     * @return 更新后的产品
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_EDIT)
    @RepeatSubmit
    @Log(title = "产品图", businessType = BusinessType.UPDATE)
    @PostMapping("/{productId}/image")
    public R<CpProductVo> bindImage(@NotNull(message = "产品ID不能为空") @PathVariable("productId") Long productId,
                                    @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("fileId");
        if (raw == null || StringUtils.isBlank(String.valueOf(raw))) {
            throw new ServiceException("请指定要设为产品图的附件（fileId）");
        }
        Long fileId;
        try {
            fileId = Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("附件ID格式不正确：" + raw);
        }
        return R.ok(productService.bindImageFromFile(productId, fileId));
    }

    /**
     * 产品图信息（不含字节）。
     *
     * @param productId 产品ID
     * @return 产品图信息
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_QUERY)
    @GetMapping("/{productId}/image")
    public R<IContentProductService.ProductImage> imageInfo(
        @NotNull(message = "产品ID不能为空") @PathVariable("productId") Long productId) {
        return R.ok(productService.imageOf(productId));
    }

    /**
     * 产品图内容（后端代理预览；对象存储是私有桶，前端不能直连）。
     *
     * @param productId 产品ID
     * @return 图片字节
     */
    @SaCheckPermission(ContentConstants.PERM_PRODUCT_QUERY)
    @GetMapping("/{productId}/image/content")
    public ResponseEntity<byte[]> imageContent(
        @NotNull(message = "产品ID不能为空") @PathVariable("productId") Long productId) {
        IContentProductService.ProductImage info = productService.imageOf(productId);
        byte[] bytes = productService.imageBytes(productId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentTypeOf(info.fileExt())))
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(bytes);
    }

    /**
     * 按扩展名推断图片 MIME（未知一律 image/png，配合 nosniff 不会被执行）。
     *
     * @param ext 扩展名
     * @return MIME
     */
    private static String contentTypeOf(String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return switch (e) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

}
