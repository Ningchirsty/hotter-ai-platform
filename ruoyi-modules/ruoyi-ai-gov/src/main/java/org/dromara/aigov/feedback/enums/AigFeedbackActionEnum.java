package org.dromara.aigov.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈处理动作（设计 §6.9）。
 *
 * <p><b>动作都是"进入改进流程"，没有一个是"直接改线上版本"</b>：设计 §6.9 明确
 * "采纳反馈不直接改线上版本"。补知识/改规则/加用例分别落到知识中心、草稿、评测用例，
 * 再经既有发布门槛才会生效。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigFeedbackActionEnum {

    /** 补知识（进入知识中心待处理） */
    ADD_KNOWLEDGE("ADD_KNOWLEDGE", "补知识"),

    /** 改规则（进入草稿/规则修订） */
    CHANGE_RULE("CHANGE_RULE", "改规则"),

    /** 加入用例（进入评测用例集） */
    ADD_CASE("ADD_CASE", "加入用例"),

    /** 关闭（无需动作 / 重复反馈） */
    CLOSE("CLOSE", "关闭");

    /** 编码 */
    private final String code;

    /** 描述 */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigFeedbackActionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigFeedbackActionEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
