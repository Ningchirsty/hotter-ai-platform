package org.dromara.content.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 事实字段别名表：把资料里的各种写法归一到闸门规则使用的 {@code field_code}。
 *
 * <p>匹配策略刻意**保守**：先剥掉括号补充说明，再做「归一化后的精确匹配」。
 * 不做模糊包含匹配——在「产品事实只能来自已确认资料」这条红线（设计文档 §3.4）下，
 * <b>漏认只是让用户手工填一次，误认却会把错误信息推成候选值</b>，代价不对称。</p>
 *
 * <p>字段编码需与 {@code cp_gate_rule.field_code} 对齐，否则闸门会永远判为缺失。</p>
 *
 * @author content
 */
public final class ContentFieldAlias {

    /**
     * fieldCode → 中文名
     */
    private static final Map<String, String> FIELD_NAMES = new LinkedHashMap<>();

    /**
     * 归一化别名 → fieldCode
     */
    private static final Map<String, String> ALIAS_TO_FIELD = new LinkedHashMap<>();

    static {
        // 电商详情图的强制项（设计文档 §8.2）
        register("sku_code", "SKU", "SKU", "sku编码", "SKU编号", "货号", "商品编码", "商品货号", "型号");
        register("product_name", "产品名称", "品名", "商品名称", "产品名", "名称");
        register("main_version", "主体版本", "产品版本", "版本号", "版本");
        register("color", "颜色", "色号", "色彩", "颜色名称");
        register("quantity", "数量", "装箱数", "装箱量", "配件数量", "每箱数量");
        register("spec_params", "参数", "规格参数", "产品参数", "规格", "技术参数");
        register("package_version", "包装版本", "包装", "包装规格");
        // 条件 / 提醒项
        register("reference_image", "参考图", "参考图片", "参考素材");
        register("brand_tone", "品牌调性", "品牌调性说明", "调性");
        // 展会宣传图的强制项
        register("brand_claim", "品牌主张", "主张", "slogan", "标语");
        register("output_size", "尺寸", "成品尺寸", "输出尺寸", "画面尺寸");
        register("resolution", "分辨率", "清晰度");
        register("print_play_req", "播放要求", "印刷要求", "播放/印刷要求", "播放与印刷要求");
    }

    private ContentFieldAlias() {
    }

    /**
     * 登记一个字段及其别名。
     *
     * @param fieldCode 字段编码
     * @param fieldName 字段中文名
     * @param aliases   别名（首个通常是规范名）
     */
    private static void register(String fieldCode, String fieldName, String... aliases) {
        FIELD_NAMES.put(fieldCode, fieldName);
        for (String alias : aliases) {
            ALIAS_TO_FIELD.putIfAbsent(normalizeKey(alias), fieldCode);
        }
        // 规范名本身也要能命中
        ALIAS_TO_FIELD.putIfAbsent(normalizeKey(fieldName), fieldCode);
    }

    /**
     * 把标签解析为字段编码。
     *
     * @param label 资料中的标签原文
     * @return 字段编码，未命中返回 null
     */
    public static String resolve(String label) {
        if (label == null) {
            return null;
        }
        String key = normalizeKey(label);
        if (key.isEmpty()) {
            return null;
        }
        String direct = ALIAS_TO_FIELD.get(key);
        if (direct != null) {
            return direct;
        }
        // 剥掉括号补充说明后再试，例如「产品名称（中文）」
        String stripped = normalizeKey(stripBrackets(label));
        return stripped.isEmpty() ? null : ALIAS_TO_FIELD.get(stripped);
    }

    /**
     * 取字段中文名，未登记时回退为编码本身。
     *
     * @param fieldCode 字段编码
     * @return 字段名
     */
    public static String fieldName(String fieldCode) {
        return FIELD_NAMES.getOrDefault(fieldCode, fieldCode);
    }

    /**
     * 是否已登记该字段编码。
     *
     * @param fieldCode 字段编码
     * @return 是否登记
     */
    public static boolean known(String fieldCode) {
        return FIELD_NAMES.containsKey(fieldCode);
    }

    /**
     * 已登记的字段编码。
     *
     * @return 字段编码列表
     */
    public static List<String> knownFields() {
        return List.copyOf(FIELD_NAMES.keySet());
    }

    /**
     * 归一化：去空白与常见标点、全角括号、大小写。
     *
     * @param text 原文
     * @return 归一 key
     */
    private static String normalizeKey(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s:：()（）\\[\\]【】*＊·、,，]", "")
            .toLowerCase(Locale.ROOT);
    }

    /**
     * 去掉中英文括号及其内容。
     *
     * @param text 原文
     * @return 去括号后的文本
     */
    private static String stripBrackets(String text) {
        return text.replaceAll("[（(【\\[][^）)】\\]]*[）)】\\]]", "");
    }

}
