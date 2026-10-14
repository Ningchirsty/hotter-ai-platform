package org.dromara.aigov.tool.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具操作类型（设计 §6.5：只读查询、生成草稿、变更业务数据、外部发送）。
 *
 * <p><b>操作类型是"要不要人工确认"的唯一判据</b>：只读查询与生成草稿按策略可直接执行；
 * 变更业务数据与外部发送必须有明确的人工确认（设计 §7.5）。
 * 把它做成枚举而不是调用方自报的布尔，是为了让"这是一次写操作"成为不可绕过的属性——
 * 调用方说自己"只是查询"而实际发起了变更，从代码上就不可能。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigToolOperationTypeEnum {

    /** 只读查询 */
    READ_ONLY_QUERY("READ_ONLY_QUERY", "只读查询", false),

    /** 生成草稿（产物待复核，不改业务数据） */
    GENERATE_DRAFT("GENERATE_DRAFT", "生成草稿", false),

    /** 变更业务数据（有业务后果） */
    MUTATE_BUSINESS_DATA("MUTATE_BUSINESS_DATA", "变更业务数据", true),

    /** 外部发送（数据离开本地环境） */
    EXTERNAL_SEND("EXTERNAL_SEND", "外部发送", true);

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否必须人工确认
     */
    private final boolean requiresConfirmation;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigToolOperationTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigToolOperationTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
