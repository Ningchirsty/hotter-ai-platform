package org.dromara.content.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.content.domain.CpProduct;

import java.io.Serial;
import java.io.Serializable;

/**
 * 轻量产品/SKU业务对象 cp_product
 *
 * @author content
 */
@Data
@AutoMapper(target = CpProduct.class, reverseConvertGenerate = false)
public class ContentProductBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    @NotBlank(message = "产品编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "产品编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String productCode;

    /**
     * 产品名称
     */
    @NotBlank(message = "产品名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 255, message = "产品名称长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String productName;

    /**
     * SKU编码
     */
    @Size(max = 64, message = "SKU编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String skuCode;

    /**
     * SKU名称
     */
    @Size(max = 255, message = "SKU名称长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String skuName;

    /**
     * 品类
     */
    @Size(max = 64, message = "品类长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String category;

    /**
     * 产品版本
     */
    @Size(max = 32, message = "产品版本长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String version;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    // ---------------- 查询条件 ----------------

    /**
     * 关键字（产品编码/名称/SKU）
     */
    @Size(max = 100, message = "关键字长度不能超过 100", groups = {QueryGroup.class})
    private String keyword;

    /**
     * 分页参数容器（平台惯例：时间范围走 params）
     */
    private java.util.Map<String, Object> params;

}
