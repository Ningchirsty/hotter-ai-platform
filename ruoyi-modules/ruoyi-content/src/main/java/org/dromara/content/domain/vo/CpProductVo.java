package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpProduct;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 轻量产品/SKU视图对象 cp_product
 *
 * @author content
 */
@Data
@AutoMapper(target = CpProduct.class)
public class CpProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * SKU名称
     */
    private String skuName;

    /**
     * 品类
     */
    private String category;

    /**
     * 品牌（如 趣往）
     */
    private String brand;

    /**
     * 二级分类（如 解构花园-静态花）
     */
    private String subCategory;

    /**
     * 产品图引用（对象存储键或 URL）
     */
    private String productImage;

    /**
     * 产品图来源任务（cp_task.task_id）
     */
    private Long productImageTaskId;

    /**
     * 产品图设定时间
     */
    private java.time.LocalDateTime productImageSetAt;

    /**
     * 产品图设定人（sys_user.user_id）
     */
    private Long productImageSetBy;

    /**
     * 产品图是否已配置：对象存储键是私有桶键，前端不能直连，
     * 只能通过后端代理预览接口读（见 {@code /content/product/{id}/image/content}）。
     */
    private Boolean productImageConfigured;

    /**
     * 主推说明
     */
    private String mainPush;

    /**
     * 产品经理
     */
    private String productManager;

    /**
     * 尺寸规格
     */
    private String sizeSpec;

    /**
     * 价格（元）
     */
    private java.math.BigDecimal price;

    /**
     * 结构/工艺
     */
    private String craft;

    /**
     * 设计灵感
     */
    private String designInspiration;

    /**
     * 产品版本
     */
    private String version;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
