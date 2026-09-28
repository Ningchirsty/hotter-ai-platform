package org.dromara.creative.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * 文案与要点块来源（{@code dp_copy_block.source}）。
 *
 * <p><b>为什么要区分来源</b>：FACT 来源的块是「从已确认事实派生」的，改动它等于改事实口径；
 * MANUAL 是人写的；MODEL 是模型起草的。页面上三者必须能分辨，否则「这句话是谁说的」
 * 又变成靠猜——这正是接错信息最容易出事的地方。</p>
 *
 * @author creative
 */
@Getter
@AllArgsConstructor
public enum DpCopyBlockSourceEnum {

    /**
     * 人工录入
     */
    MANUAL("MANUAL", "人工录入"),
    /**
     * 模型起草（须人确认后才用于排版）
     */
    MODEL("MODEL", "模型起草"),
    /**
     * 由已确认事实派生（source_ref 记事实编码）
     */
    FACT("FACT", "事实派生");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找（大小写不敏感）。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static DpCopyBlockSourceEnum find(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (DpCopyBlockSourceEnum item : values()) {
            if (item.code.equals(normalized)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 合法取值清单（报错文案里用）。
     *
     * @return 形如 MANUAL/MODEL/FACT
     */
    public static String codes() {
        StringBuilder sb = new StringBuilder();
        for (DpCopyBlockSourceEnum item : values()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(item.code);
        }
        return sb.toString();
    }

}
