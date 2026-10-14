package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务步骤状态（设计 §8.2）。
 *
 * <p>刻意与任务状态分开：任务可以是 {@code SUCCEEDED} 而某一步 {@code FAILED}
 * （部分成功），合成一个状态会掩盖"哪一步没成"。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskStepStatusEnum {

    /** 已编排、尚未执行 */
    PLANNED("PLANNED", "已编排"),

    /** 执行中 */
    RUNNING("RUNNING", "执行中"),

    /** 成功 */
    SUCCEEDED("SUCCEEDED", "已成功"),

    /** 失败 */
    FAILED("FAILED", "已失败"),

    /** 跳过（条件不满足等） */
    SKIPPED("SKIPPED", "已跳过");

    private final String code;

    private final String desc;

    /**
     * 是否终态（不再迁移）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED;
    }

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigTaskStepStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskStepStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
