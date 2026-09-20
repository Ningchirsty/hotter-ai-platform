package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 闸门等级枚举。
 * <p>对应设计文档 §8.1 的三级闸门，是任务能否流转的**唯一判定依据**
 * （判定算法见 SPEC §4.1）。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentGateLevelEnum {

    /**
     * 强制阻断：不确认将导致产品错误、合规风险或无法制作 → 禁止进入下一状态
     */
    BLOCK("BLOCK", "强制阻断"),
    /**
     * 条件流转：当前可开始，但必须补齐或确定替代方案
     */
    CONDITION("CONDITION", "条件流转"),
    /**
     * 非阻断提醒：不影响事实正确性，只影响创意或效率
     */
    NOTICE("NOTICE", "非阻断提醒");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否阻断流转
     *
     * @return BLOCK 返回 true
     */
    public boolean blocking() {
        return this == BLOCK;
    }

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentGateLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentGateLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
