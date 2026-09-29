package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付类型 dp_delivery_type（V0.2 B1 场景配置层）。
 *
 * <p>它是"有哪些场景"的权威：`delivery_type` 与 {@code cp_task.deliverable_type} **同词表**
 * （所以种子里用的是既有的 `ECOM_DETAIL`，而不是文档 §40 写的 `ECOM_DETAIL_PAGE`——
 * 后者放在 {@link #aliasCodes} 里做别名，避免出现第三套叫法）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_delivery_type")
public class DpDeliveryType extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 业务大类（如 02_ECOMMERCE）
     */
    private String categoryCode;

    /**
     * 交付类型编码（与 cp_task.deliverable_type 同词表）
     */
    private String deliveryType;

    /**
     * 别名编码（逗号分隔；兼容文档/历史叫法，如 ECOM_DETAIL_PAGE）
     */
    private String aliasCodes;

    /**
     * 展示名
     */
    private String deliveryName;

    /**
     * 媒介类型（LONG_PAGE/POSTER/CAROUSEL/ARTICLE/VIDEO/PRINT）
     */
    private String mediaType;

    /**
     * 渲染模式（LONGPAGE/POSTER/ARTICLE/PRINT）
     */
    private String renderMode;

    /**
     * 默认场景档案（dp_scenario_profile.id）
     */
    private Long defaultProfileId;

    /**
     * 是否启用（0启用 1停用，与 dp_layout_template.enabled 同口径）
     */
    private String enabled;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
