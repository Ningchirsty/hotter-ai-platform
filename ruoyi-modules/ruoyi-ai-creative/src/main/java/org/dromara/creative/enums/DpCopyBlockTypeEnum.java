package org.dromara.creative.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文案与要点块类型（{@code dp_copy_block.block_type}）。
 *
 * <p><b>为什么是这四类</b>：电商详情页上「文字」的实际用途只有四种——
 * 卖点（要有优先级）、正文分段（要能排序分段）、参数行（键值成对）、必显信息（合规与品牌要求，
 * 必须出现）。把它们混在一个自由文本字段里，排序、分段、逐条校验立刻全部丢失，
 * 闸门也就无从判定「禁用词是否声明过」。</p>
 *
 * @author creative
 */
@Getter
@AllArgsConstructor
public enum DpCopyBlockTypeEnum {

    /**
     * 卖点（sort_no 即优先级，页面从上到下）
     */
    SELLING_POINT("SELLING_POINT", "卖点"),
    /**
     * 正文分段（详情页长文案的分段）
     */
    BODY_SECTION("BODY_SECTION", "正文分段"),
    /**
     * 参数行（title=参数名，content=参数值）
     */
    SPEC_ROW("SPEC_ROW", "参数行"),
    /**
     * 必显信息（品牌名/logo/口号/资质等必须出现在成品里的内容）
     *
     * <p><b>R7 起不提供录入入口，必显信息走品牌 Brief 的 mustShow；此枚举值仅为兼容保留。</b>
     * 两个真相源会让「必显到底以哪个为准」变成需要解释的问题：提示词与闸门读的都是
     * {@code dp_brand_brief.must_show}，因此文案与要点一侧不再派生、也不再校验本类型。
     * 历史数据（若有）仍按本编码原样读出，不删除、不改写。</p>
     */
    MUST_SHOW("MUST_SHOW", "必显信息");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找（大小写不敏感，前端传小写也能识别）。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static DpCopyBlockTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        for (DpCopyBlockTypeEnum item : values()) {
            if (item.code.equals(normalized)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 描述（未命中返回原始编码，便于排障展示）。
     *
     * @param code 编码
     * @return 可读描述
     */
    public static String descOf(String code) {
        DpCopyBlockTypeEnum item = find(code);
        return item == null ? code : item.desc;
    }

    /**
     * 合法取值清单（报错文案里用，避免只说「非法」不说什么是合法）。
     *
     * @return 形如 SELLING_POINT/BODY_SECTION/SPEC_ROW/MUST_SHOW
     */
    public static String codes() {
        StringBuilder sb = new StringBuilder();
        for (DpCopyBlockTypeEnum item : values()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(item.code);
        }
        return sb.toString();
    }

}
