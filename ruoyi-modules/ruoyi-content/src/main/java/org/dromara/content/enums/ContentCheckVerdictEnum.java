package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 成品一致性检查结论枚举。
 *
 * <p><b>为什么必须有「无法判定」</b>：一致性检查是机器判断，存在两类天然不可判的情形——
 * 图片不可解码、或两张图根本对不上（例如参考图是局部细节、成品图是整版长图）。
 * 把它们强行归入「一致」或「不一致」都是在制造假结论。设计文档 §0.1 的红线要求
 * 「AI 不得自动确认」，因此宁可如实说「判不了，请人工看」，也不给一个看起来确定的答案。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentCheckVerdictEnum {

    /**
     * 一致（未发现差异）
     */
    CONSISTENT("CONSISTENT", "一致"),
    /**
     * 不一致（发现差异，详见 findings）
     */
    INCONSISTENT("INCONSISTENT", "不一致"),
    /**
     * 无法判定（证据不足或图片不可用，需人工复核）
     */
    UNCERTAIN("UNCERTAIN", "无法判定");

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
    public static ContentCheckVerdictEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentCheckVerdictEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
