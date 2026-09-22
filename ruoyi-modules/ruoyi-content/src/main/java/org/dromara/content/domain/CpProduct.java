package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 轻量产品/SKU对象 cp_product
 *
 * <p>设计文档 §2.3 明确「当前不建产品主数据平台」，但事实快照、素材元数据与
 * 「包装版本更新影响哪些页面」的追溯都需要稳定的产品/SKU 标识，故建轻量表。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_product")
public class CpProduct extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品ID
     */
    @TableId(value = "product_id")
    private Long productId;

    /**
     * 产品编码（对业务唯一）
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * SKU编码（可空=产品级）
     */
    private String skuCode;

    /**
     * SKU名称
     */
    private String skuName;

    /**
     * 品类（首期试点：积木花）
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
     * 产品图引用（对象存储键或 URL；业务库不存文件本体）
     */
    private String productImage;

    /**
     * 主推说明（原表该列常填售卖形态/口径说明，故为文本而非布尔）
     */
    private String mainPush;

    /**
     * 产品经理
     */
    private String productManager;

    /**
     * 尺寸规格（如 257.60*149.30；多形态用换行分隔）
     */
    private String sizeSpec;

    /**
     * 价格（元）
     */
    private java.math.BigDecimal price;

    /**
     * 结构/工艺（如 UV+喷漆、喷漆+镀铬）
     */
    private String craft;

    /**
     * 设计灵感
     */
    private String designInspiration;

    /**
     * 产品版本（用于影响面追溯）
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
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
