package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 产品事实快照的确认状态枚举。
 *
 * <p><b>红线</b>：解析产生的候选值一律以 {@link #PENDING} 落库。系统**不存在**
 * 「AI 直接写入既定事实」的路径——只有人工确认动作才会把状态推进到
 * {@link #CONFIRMED}（设计文档 §2.3 / §3.4）。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentFactConfirmStatusEnum {

    /**
     * 待确认：解析或人工录入的候选值，尚未确认
     */
    PENDING("PENDING", "待确认"),
    /**
     * 已确认：人工确认，可作为产品事实使用
     */
    CONFIRMED("CONFIRMED", "已确认"),
    /**
     * 冲突：同一字段存在多个不等取值，待裁定
     */
    CONFLICT("CONFLICT", "冲突"),
    /**
     * 已否决：人工判定该取值无效
     */
    REJECTED("REJECTED", "已否决");

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
    public static ContentFactConfirmStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentFactConfirmStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
