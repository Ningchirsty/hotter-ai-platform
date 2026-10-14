package org.dromara.aigov.knowledge.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识处理/生效状态（设计 §6.4：{@code UPLOADED → SCANNING → PARSING → CHUNKING → INDEXING → READY}，
 * 失败 {@code FAILED}，不再使用的版本 {@code RETIRED}）。
 *
 * <p><b>为什么把顺序写进枚举</b>：知识处理是"只有前一步成了才谈得上后一步"的管道，
 * 跳过一步不会报错，只会产出一个"看起来 READY、其实没解析"的版本。
 * 用 {@link #next()} 表达唯一下一步，调用方就无法跳步。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigKnowledgeStatusEnum {

    /** 已上传（尚未做任何处理） */
    UPLOADED("UPLOADED", "已上传"),

    /** 安全扫描中 */
    SCANNING("SCANNING", "安全扫描中"),

    /** 解析中（PDF/DOCX 取文本） */
    PARSING("PARSING", "解析中"),

    /** 分段中 */
    CHUNKING("CHUNKING", "分段中"),

    /** 索引中 */
    INDEXING("INDEXING", "索引中"),

    /** 就绪（可被检索） */
    READY("READY", "已就绪"),

    /** 失败（可重试） */
    FAILED("FAILED", "已失败"),

    /** 退役（不再被新任务检索，历史结果仍可追溯） */
    RETIRED("RETIRED", "已退役");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 唯一下一步；终态返回 {@code null}。
     *
     * @return 下一步；{@link #READY}/{@link #FAILED}/{@link #RETIRED} 返回 null
     */
    public AigKnowledgeStatusEnum next() {
        return switch (this) {
            case UPLOADED -> SCANNING;
            case SCANNING -> PARSING;
            case PARSING -> CHUNKING;
            case CHUNKING -> INDEXING;
            case INDEXING -> READY;
            default -> null;
        };
    }

    /**
     * 是否终态（不再自动推进）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == READY || this == FAILED || this == RETIRED;
    }

    /**
     * 是否可被检索（只有就绪版本可被新任务检索）。
     *
     * @return 就绪返回 true
     */
    public boolean retrievable() {
        return this == READY;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigKnowledgeStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigKnowledgeStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
