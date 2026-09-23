package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Visual DNA 的种子构造：从「已确认的产品事实 + 参考图 + 明确标注的行业默认值」推导出一版可用基因。
 *
 * <p><b>它不是 AI 生成的结论</b>：本类只做确定性的映射与取默认值，凡是没把握的一律<b>留空</b>
 * （留给人工补或等视觉模型），并把「哪一项来自哪条事实、哪一项是默认值」逐条写进证据链。
 * 这样页面可以如实告诉用户「这版基因没有经过视觉模型分析」，而不是把默认值包装成 AI 判断。</p>
 *
 * <p>有可用的视觉模型时，模型结果只用于<b>补全或修正</b>种子里的空缺项，来源标记改为 AI，
 * 并保留证据链（见 {@code VisualBrainAdapter}）。</p>
 *
 * @author creative
 */
@Component
public class DnaSeedBuilder {

    /**
     * 来源：由已确认事实推导
     */
    public static final String SOURCE_FACTS = "FACTS";

    /**
     * 常见中文颜色 → 十六进制（只做保守映射，认不出就留空，不猜）
     */
    private static final Map<String, String> COLOR_TABLE = buildColorTable();

    /**
     * 默认背景色（纯色底摄影棚常见取值）
     */
    private static final String DEFAULT_BACKGROUND = "#F5F5F3";

    /**
     * 构造输入。
     *
     * @param taskId            项目ID
     * @param productName       产品名
     * @param skuCode           SKU
     * @param facts             已确认事实（字段编码 → 值）
     * @param referenceFileNames 参考图文件名（用于证据链）
     */
    public record SeedInput(Long taskId,
                            String productName,
                            String skuCode,
                            List<FactRow> facts,
                            List<String> referenceFileNames) {
    }

    /**
     * 一条已确认事实。
     *
     * @param fieldCode 字段编码
     * @param fieldName 字段名
     * @param value     值
     * @param source    来源定位（附件与位置，用于溯源）
     */
    public record FactRow(String fieldCode, String fieldName, String value, String source) {
    }

    /**
     * 构造结果。
     *
     * @param dna      DNA 树
     * @param notes    给用户看的说明（哪些来自事实、哪些是默认值、哪些缺失）
     * @param missing  建议人工补的项
     */
    public record SeedResult(ObjectNode dna, List<String> notes, List<String> missing) {
    }

    /**
     * 构造一版种子基因。
     *
     * @param input 输入
     * @return 种子结果
     */
    public SeedResult build(SeedInput input) {
        ObjectNode dna = VisualDnaSchema.empty();
        List<String> notes = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        Map<String, FactRow> byCode = new LinkedHashMap<>();
        if (input.facts() != null) {
            for (FactRow fact : input.facts()) {
                if (fact.fieldCode() != null && StringUtils.isNotBlank(fact.value())) {
                    byCode.put(fact.fieldCode(), fact);
                }
            }
        }

        // 1) 品牌调性 → 风格关键词 + 字体风格
        FactRow tone = byCode.get("brand_tone");
        if (tone != null) {
            List<String> keywords = splitKeywords(tone.value());
            if (!keywords.isEmpty()) {
                for (String keyword : keywords) {
                    dna.withArray("styleKeywords").add(keyword);
                }
                notes.add("风格关键词来自「品牌调性说明」（已确认事实）");
            }
            dna.put("typographyStyle", "字体风格跟随品牌调性：" + abbreviate(tone.value(), 60));
            VisualDnaSchema.addEvidence(dna, "FACT", tone.fieldName(), tone.value(), factSource(tone));
        } else {
            missing.add("品牌调性说明（brand_tone）：缺失，风格关键词目前只有通用默认值");
        }

        // 2) 颜色 → 主色
        FactRow color = byCode.get("color");
        if (color != null) {
            String hex = toHex(color.value());
            if (hex != null) {
                dna.withObject("/colors").put("primary", hex);
                notes.add("主色由「颜色」事实映射得到：" + color.value() + " → " + hex);
            } else {
                notes.add("「颜色」事实为「" + color.value() + "」，未能映射为色值，主色留空待人工确认");
                missing.add("主色（colors.primary）：颜色事实无法自动映射，请人工填写色值");
            }
            VisualDnaSchema.addEvidence(dna, "FACT", color.fieldName(), color.value(), factSource(color));
        } else {
            missing.add("颜色（color）：缺失，主色无法从事实推导");
        }

        // 3) 其余事实：进证据链（是视觉规格的重要背景，但不对应单一字段）
        for (String code : List.of("product_name", "sku_code", "main_version", "spec_params",
            "package_version", "quantity", "reference_image")) {
            FactRow fact = byCode.get(code);
            if (fact != null) {
                VisualDnaSchema.addEvidence(dna, "FACT", fact.fieldName(), fact.value(), factSource(fact));
            }
        }
        if (byCode.containsKey("main_version")) {
            notes.add("主体版本：" + byCode.get("main_version").value() + "（已确认）");
        }
        if (byCode.containsKey("package_version")) {
            notes.add("包装版本：" + byCode.get("package_version").value() + "（已确认）");
        }

        // 4) 参考图 → 证据链（模型可用时会在此基础上做真正的图像分析）
        if (input.referenceFileNames() != null) {
            for (String name : input.referenceFileNames()) {
                VisualDnaSchema.addEvidence(dna, "REFERENCE", "参考图", name, "项目附件（cp_task_file）");
            }
            if (input.referenceFileNames().isEmpty()) {
                missing.add("参考图：未上传，无法做图像侧分析");
            }
        }

        // 5) 明确标注的默认值（不假装是分析结论）
        dna.put("saturation", defaultIfAbsent(dna, "saturation", "MEDIUM"));
        dna.put("contrastLevel", defaultIfAbsent(dna, "contrastLevel", "MEDIUM"));
        dna.put("whitespaceLevel", defaultIfAbsent(dna, "whitespaceLevel", "HIGH"));
        dna.put("sceneType", defaultIfAbsent(dna, "sceneType", "纯色底"));
        VisualDnaSchema.setLighting(dna, "SOFT", "FRONT");
        VisualDnaSchema.setProductRatio(dna, 45, 65);
        if (StringUtils.isBlank(dna.path("colors").path("background").asText(null))) {
            dna.withObject("/colors").put("background", DEFAULT_BACKGROUND);
        }
        dna.withArray("avoidKeywords").add("杂乱背景").add("强撞色").add("文字水印")
            .add("产品变形").add("低分辨率").add("过曝");
        if (dna.path("styleKeywords").isEmpty()) {
            dna.withArray("styleKeywords").add("现代简约").add("清爽留白").add("商业摄影");
        }
        VisualDnaSchema.addEvidence(dna, "DEFAULT", "饱和度/对比度/留白/场景/光线/占比/禁忌词",
            "MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/45~65%", "行业默认值（非模型结论，可人工修改）");
        notes.add("饱和度、对比度、留白、场景、光线、产品占比、禁忌词为默认值，需人工确认或等视觉模型分析");

        // 6) 主体说明
        dna.put("subject", StringUtils.blankToDefault(input.productName(),
            StringUtils.blankToDefault(input.skuCode(), "当前产品")));
        if (StringUtils.isBlank(input.productName())) {
            notes.add("项目未关联产品名，主体按「当前产品」占位");
        }

        return new SeedResult(dna, notes, missing);
    }

    /**
     * 把常见中文颜色名映射为十六进制；认不出返回 null（不猜）。
     *
     * @param value 事实值（可能是「粉色」「#E8543F」「红色/金色」等）
     * @return #RRGGBB 或 null
     */
    public String toHex(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        if (text.matches("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$")) {
            return text.toUpperCase(Locale.ROOT);
        }
        // 取第一个能认出的颜色词（「粉白」这类组合按第一个算，并在证据里保留原值）
        for (Map.Entry<String, String> entry : COLOR_TABLE.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String defaultIfAbsent(ObjectNode node, String field, String fallback) {
        String current = node.path(field).asText(null);
        return StringUtils.isBlank(current) ? fallback : current;
    }

    private static String factSource(FactRow fact) {
        String source = StringUtils.blankToDefault(fact.source(), "产品事实（已确认）");
        return "产品事实 " + fact.fieldCode() + "（已确认）· " + source;
    }

    private static List<String> splitKeywords(String value) {
        List<String> keywords = new ArrayList<>();
        for (String part : value.split("[,，、;；/\\s]+")) {
            String keyword = part.trim();
            if (!keyword.isEmpty() && keyword.length() <= 12) {
                keywords.add(keyword);
            }
        }
        return keywords.size() > 6 ? keywords.subList(0, 6) : keywords;
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static Map<String, String> buildColorTable() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("米白", "#F5F5F0");
        map.put("白", "#FFFFFF");
        map.put("粉", "#E8A0A8");
        map.put("红", "#C0392B");
        map.put("橙", "#E67E22");
        map.put("黄", "#F1C40F");
        map.put("金", "#C9A227");
        map.put("绿", "#4E8C6A");
        map.put("青", "#3AA6A6");
        map.put("蓝", "#2E6DA4");
        map.put("紫", "#7D5BA6");
        map.put("黑", "#1C1C1C");
        map.put("灰", "#9AA3B2");
        map.put("银", "#C0C4CC");
        map.put("棕", "#8B5E3C");
        map.put("咖", "#6F4E37");
        return map;
    }

}
