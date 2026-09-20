package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资料文件类型枚举。
 * <p>{@link #isParseable()} 决定该类型能否进入本地解析：阶段1A 只支持
 * Excel（XSSF/HSSF）、Word（docx，XWPF）、PDF（PDFBox）。
 * 图片需 OCR、旧版 .doc 需 poi-scratchpad，本阶段一律跳过并给出可读原因——
 * 「明确告知跳过」比「悄悄返回空」更符合设计文档 §1 的交互原则。</p>
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentFileKindEnum {

    /**
     * Excel（xls/xlsx）
     */
    EXCEL("EXCEL", "Excel", true),
    /**
     * Word（docx；旧版 doc 另有处理）
     */
    WORD("WORD", "Word", true),
    /**
     * PDF
     */
    PDF("PDF", "PDF", true),
    /**
     * 图片（阶段1A 不提取文字，需后续 OCR）
     */
    IMAGE("IMAGE", "图片", false),
    /**
     * 视频
     */
    VIDEO("VIDEO", "视频", false),
    /**
     * 设计稿（psd/ai 等）
     */
    DESIGN("DESIGN", "设计稿", false),
    /**
     * 其他
     */
    OTHER("OTHER", "其他", false);

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 是否支持本地文本抽取
     */
    private final boolean parseable;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentFileKindEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentFileKindEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按扩展名推断文件类型。
     *
     * @param ext 扩展名（大小写不敏感，可带点）
     * @return 文件类型，无法判定返回 {@link #OTHER}
     */
    public static ContentFileKindEnum ofExt(String ext) {
        if (ext == null) {
            return OTHER;
        }
        String e = ext.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.startsWith(".")) {
            e = e.substring(1);
        }
        return switch (e) {
            case "xls", "xlsx", "csv" -> EXCEL;
            case "doc", "docx" -> WORD;
            case "pdf" -> PDF;
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff" -> IMAGE;
            case "mp4", "mov", "avi", "mkv", "webm" -> VIDEO;
            case "psd", "ai", "sketch", "fig", "xd" -> DESIGN;
            default -> OTHER;
        };
    }

}
