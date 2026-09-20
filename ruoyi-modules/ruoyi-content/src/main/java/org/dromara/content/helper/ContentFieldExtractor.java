package org.dromara.content.helper;

import lombok.Data;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.helper.ExtractedDocument.LabelValue;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 事实候选字段抽取器：把 {@link ExtractedDocument} 的标签-值对映射为带字段编码的候选值。
 *
 * <p><b>输出一律是候选，不是事实</b>：调用方必须把它们以 {@code PENDING} 落库，
 * 只有人工确认后才能成为产品事实（设计文档 §2.3 / §3.4 红线）。</p>
 *
 * @author content
 */
@Component
public class ContentFieldExtractor {

    /**
     * 标签精确命中别名表时的置信度
     */
    private static final BigDecimal CONFIDENCE_EXACT = new BigDecimal("0.85");

    /**
     * 从抽取结果中识别候选字段。
     *
     * @param doc 抽取结果
     * @return 候选字段列表（可能为空，不返回 null）
     */
    public List<Candidate> extract(ExtractedDocument doc) {
        List<Candidate> out = new ArrayList<>();
        if (doc == null) {
            return out;
        }
        for (LabelValue pair : doc.getPairs()) {
            String fieldCode = ContentFieldAlias.resolve(pair.label());
            if (StringUtils.isBlank(fieldCode)) {
                continue;
            }
            Candidate c = new Candidate();
            c.setFieldCode(fieldCode);
            c.setFieldName(ContentFieldAlias.fieldName(fieldCode));
            c.setLabel(pair.label());
            c.setValue(truncate(pair.value()));
            c.setLocator(pair.locator());
            c.setExcerpt(buildExcerpt(pair));
            c.setConfidence(CONFIDENCE_EXACT);
            out.add(c);
        }
        return out;
    }

    /**
     * 生成证据摘录：标签 + 值，截断到列宽内。
     *
     * @param pair 标签-值对
     * @return 摘录
     */
    private String buildExcerpt(LabelValue pair) {
        return truncate(pair.label() + "：" + pair.value());
    }

    /**
     * 截断到证据列宽（source_excerpt varchar(500)）。
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() > 200) {
            // 证据只需让人看懂来自哪里，无需全文
            return t.substring(0, 200);
        }
        return t;
    }

    /**
     * 候选字段。
     *
     * @author content
     */
    @Data
    public static class Candidate implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 字段编码（与 cp_gate_rule.field_code 对齐）
         */
        private String fieldCode;

        /**
         * 字段中文名
         */
        private String fieldName;

        /**
         * 资料中的标签原文
         */
        private String label;

        /**
         * 候选值
         */
        private String value;

        /**
         * 来源定位
         */
        private String locator;

        /**
         * 证据摘录
         */
        private String excerpt;

        /**
         * 置信度
         */
        private BigDecimal confidence;

    }

}
