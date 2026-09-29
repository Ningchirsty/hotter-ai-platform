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
 * 输出规格 dp_output_spec（V0.2 B1）：把"尺寸"配置化。
 *
 * <p>种子里的 `TAOBAO_DETAIL` 宽 750 正是 R10 之前**写死在模板里**的页宽——
 * 也就是说本轮先把"现状"登记成配置，B2 再让排版从这里取页宽（F1 已经把管道打通）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_output_spec")
public class DpOutputSpec extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 规格编码（如 TAOBAO_DETAIL）
     */
    private String specCode;

    /**
     * 所属交付类型
     */
    private String deliveryType;

    /**
     * 渠道（TAOBAO/TMALL/XHS/WECHAT/OFFLINE…）
     */
    private String channel;

    /**
     * 宽（单位见 unit）
     */
    private Integer width;

    /**
     * 高（height_mode=FIXED 时才有值）
     */
    private Integer height;

    /**
     * 高度模式（FIXED/AUTO）
     */
    private String heightMode;

    /**
     * 比例（如 3:4；与宽高二选一表达）
     */
    private String ratio;

    /**
     * 单位（px/mm/cm）
     */
    private String unit;

    /**
     * 印刷 DPI（屏幕规格为空）
     */
    private Integer dpi;

    /**
     * 色彩模式（RGB/CMYK）
     */
    private String colorMode;

    /**
     * 安全区（结构化）
     */
    private String safeAreaJson;

    /**
     * 出血（结构化）
     */
    private String bleedJson;

    /**
     * 文件格式要求（结构化）
     */
    private String fileFormatJson;

    /**
     * 源图倍率（导出时按此放大，1=按规格原尺寸）
     */
    private Integer sourceScale;

    /**
     * 是否该交付类型的默认规格（1是 0否）
     */
    private String isDefault;

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
