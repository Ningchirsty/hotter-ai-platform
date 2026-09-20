package org.dromara.content.helper;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档抽取结果。
 *
 * <p>抽取只负责「把文档读成标签-值对」，<b>不判断字段含义、不落库</b>。
 * 字段编码映射由 {@link ContentFieldAlias} + {@code ContentFieldExtractor} 完成，
 * 这样「怎么读文档」与「怎么认字段」是两件可独立验证的事。</p>
 *
 * @author content
 */
@Data
public class ExtractedDocument {

    /**
     * 是否成功完成文本/结构化抽取。
     * <p>false 时 {@link #skipReason} 必须有值——跳过不能是静默的。</p>
     */
    private boolean extracted;

    /**
     * 未抽取的原因（用户可读）。extracted=false 时必填。
     */
    private String skipReason;

    /**
     * 抽取到的标签-值对
     */
    private final List<LabelValue> pairs = new ArrayList<>();

    /**
     * 展平后的纯文本（仅用于生成证据摘录与排障，不落业务库正文）
     */
    private String text = "";

    /**
     * 标签-值对。
     *
     * @param label   标签原文（如「产品名称」）
     * @param value   值原文
     * @param locator 来源定位（如「Sheet1 第3行」），互动卡要显示它
     */
    public record LabelValue(String label, String value, String locator) {
    }

    /**
     * 追加一个标签-值对（标签或值为空则忽略）。
     *
     * @param label   标签
     * @param value   值
     * @param locator 定位
     */
    public void addPair(String label, String value, String locator) {
        if (label == null || label.isBlank() || value == null || value.isBlank()) {
            return;
        }
        pairs.add(new LabelValue(label.trim(), value.trim(), locator));
    }

}
