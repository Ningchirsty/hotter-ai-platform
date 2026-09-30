package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容交付类型枚举。
 * <p>对应设计文档 §4「业务范围」。阶段1A 只对 {@link #ECOM_DETAIL} 与 {@link #EXHIBITION}
 * 灌了闸门规则种子，其余类型可后续在「闸门规则」页补配。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentDeliverableTypeEnum {

    /**
     * 电商详情图（首期试点品类：积木花）
     */
    ECOM_DETAIL("ECOM_DETAIL", "电商详情图"),
    /**
     * 主图 / SKU 图
     */
    MAIN_IMAGE("MAIN_IMAGE", "主图/SKU图"),
    /**
     * 品牌海报（V0.2 R46/R50：视觉工厂的第三个配置驱动交付类型）。
     *
     * <p><b>为什么必须登记在这里</b>：视觉工厂那套「交付类型 → 场景档案 → 步骤 → 工作台装配」
     * 全是配置（<code>dp_delivery_type</code> 等），但**建项目这一步会过内容模块**，
     * 而内容模块的交付类型是这份硬编码枚举（{@code ContentTaskServiceImpl#create} 会校验）。
     * R46 加配置时漏了这一项，真机干跑时建项目直接被拒："交付类型非法：BRAND_POSTER"——
     * 配置再全也进不了门。加新交付类型时**两边都要加**（运维侧的「配置 ↔ 代码全覆盖核对」
     * 脚本会对账 dp_delivery_type 与这份枚举，漏一边就报红）。</p>
     */
    BRAND_POSTER("BRAND_POSTER", "品牌海报"),
    /**
     * 展会宣传图
     */
    EXHIBITION("EXHIBITION", "展会宣传图"),
    /**
     * 说明书（不得虚构步骤）
     */
    MANUAL("MANUAL", "说明书"),
    /**
     * 包装
     */
    PACKAGE("PACKAGE", "包装"),
    /**
     * 视频内容（阶段1A 不处理）
     */
    VIDEO("VIDEO", "视频内容");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentDeliverableTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentDeliverableTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
