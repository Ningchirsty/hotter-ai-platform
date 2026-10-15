package org.dromara.aigov.knowledge.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识文档来源类型（设计 §6.4：首期支持批准的 PDF、DOCX、TXT、Markdown）。
 *
 * <p><b>为什么是封闭集合</b>：不同来源类型的解析能力不同——把不支持的扩展名当成"能解析"
 * 会在解析阶段才失败，而失败发生在异步处理后，用户早就离开了界面。这里在登记时就按扩展名判定。</p>
 *
 * <p><b>XLSX 不在首期</b>：产品主数据与规格表应优先走结构化业务接口或明确映射导入
 * （设计 §6.4），把规格/报价表切成分段文本会丢结构、且容易把报价当散文检索出来。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigKnowledgeSourceTypeEnum {

    /** PDF */
    PDF("PDF", "PDF", new String[] {"pdf"}),

    /** Word 文档 */
    DOCX("DOCX", "Word 文档", new String[] {"docx"}),

    /** 纯文本 */
    TXT("TXT", "纯文本", new String[] {"txt"}),

    /** Markdown */
    MARKDOWN("MARKDOWN", "Markdown", new String[] {"md", "markdown"});

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 可接受的扩展名（小写，不含点）
     */
    private final String[] extensions;

    /**
     * 按文件名扩展名识别来源类型。
     *
     * @param fileName 文件名（可含路径）
     * @return 枚举；无法识别返回 null
     */
    public static AigKnowledgeSourceTypeEnum fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String lower = fileName.trim().toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return null;
        }
        String ext = lower.substring(dot + 1);
        for (AigKnowledgeSourceTypeEnum item : values()) {
            for (String candidate : item.extensions) {
                if (candidate.equals(ext)) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigKnowledgeSourceTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigKnowledgeSourceTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
