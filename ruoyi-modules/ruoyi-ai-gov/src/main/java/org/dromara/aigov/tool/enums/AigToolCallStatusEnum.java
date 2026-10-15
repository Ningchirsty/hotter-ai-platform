package org.dromara.aigov.tool.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具调用状态（设计 §7.5、§7.6）。
 *
 * <p>独有 {@link #OUTCOME_UNKNOWN}：写操作超时但结果未知时，既不能记成功也不能记失败——
 * 先查询操作凭证再决定（设计 §7.6"发生超时但结果未知的写操作先查询凭证，不立即重试"）。
 * 把它压成"失败"会导致重复写入；压成"成功"会伪造事实。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigToolCallStatusEnum {

    /** 执行中 */
    RUNNING("RUNNING", "执行中"),

    /** 成功（可能有操作凭证） */
    SUCCEEDED("SUCCEEDED", "已成功"),

    /** 失败 */
    FAILED("FAILED", "已失败"),

    /** 需要人工确认（尚未执行） */
    NEEDS_CONFIRMATION("NEEDS_CONFIRMATION", "等待人工确认"),

    /** 结果未知（写操作超时/断连，必须先查凭证） */
    OUTCOME_UNKNOWN("OUTCOME_UNKNOWN", "结果未知待核查");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否终态。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == OUTCOME_UNKNOWN;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigToolCallStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigToolCallStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
