package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容任务状态枚举。
 *
 * <p><b>阶段1A 可达的状态只有前五个</b>（{@link #stage1a} = true）。
 * 其余状态来自设计文档 §15 的完整状态机，此处仅占位保留，使后续阶段不必改动已有取值；
 * 阶段1A 的代码**不会**把任务推进到 {@code stage1a=false} 的状态。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentTaskStatusEnum {

    // ---------------- 阶段1A 可达 ----------------

    /**
     * 草稿：任务已建，资料尚未解析
     */
    DRAFT("DRAFT", "草稿", true),
    /**
     * 解析中：异步解析作业进行中
     */
    PARSING("PARSING", "解析中", true),
    /**
     * 待确认/待补料：存在未解决的强制阻断项
     */
    PENDING_CONFIRM("PENDING_CONFIRM", "待确认/待补料", true),
    /**
     * 条件开工：强制项已满足，仍有条件项待补齐或确定替代方案
     */
    CONDITIONAL_READY("CONDITIONAL_READY", "条件开工", true),
    /**
     * 可开工：强制项与条件项均已满足
     */
    READY("READY", "可开工", true),

    // ---------------- 阶段1B 及以后（占位，阶段1A 不可达） ----------------

    /**
     * 策划中
     */
    PLANNING("PLANNING", "策划中", false),
    /**
     * 制作中
     */
    PRODUCING("PRODUCING", "制作中", false),
    /**
     * AI 初检中
     */
    AI_CHECKING("AI_CHECKING", "AI初检中", false),
    /**
     * 内部复核中
     */
    REVIEWING("REVIEWING", "内部复核中", false),
    /**
     * 品牌/业务审稿中
     */
    BRAND_REVIEW("BRAND_REVIEW", "品牌/业务审稿中", false),
    /**
     * 修改中
     */
    REVISING("REVISING", "修改中", false),
    /**
     * 已确认
     */
    CONFIRMED("CONFIRMED", "已确认", false),
    /**
     * 已交付
     */
    DELIVERED("DELIVERED", "已交付", false),
    /**
     * 已归档
     */
    ARCHIVED("ARCHIVED", "已归档", false);

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 阶段1A 是否可达
     */
    private final boolean stage1a;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentTaskStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentTaskStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
