package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 互动确认卡类型枚举。
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentCardTypeEnum {

    /**
     * 缺料：闸门规则要求的事实字段没有任何候选值
     */
    MISSING("MISSING", "缺料"),
    /**
     * 冲突：同一字段存在多个不等的取值，需人工裁定
     */
    CONFLICT("CONFLICT", "冲突"),
    /**
     * 审批：需要授权或批准的事项
     */
    APPROVAL("APPROVAL", "审批"),
    /**
     * 补料：需要补充资料
     */
    SUPPLEMENT("SUPPLEMENT", "补料"),
    /**
     * 例外：非常规情况的决策
     */
    EXCEPTION("EXCEPTION", "例外");

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
    public static ContentCardTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentCardTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
