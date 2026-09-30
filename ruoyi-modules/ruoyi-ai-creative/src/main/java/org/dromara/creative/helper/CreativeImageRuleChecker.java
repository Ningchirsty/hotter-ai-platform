package org.dromara.creative.helper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dromara.common.core.utils.StringUtils;

/**
 * 交付图的确定性规则体检（V0.2 R29）。
 *
 * <p><b>它回答什么</b>：这张交付图**在客观度量上**是否满足这一屏的质检规则——
 * 画布是不是 1:1、最短边够不够、有没有透明通道、边缘白不白、主体占多少、有没有被画布裁到。
 * 全部由像素统计得出，**不调用任何模型**，同一张图任何时候跑都是同一份结论（可复算）。</p>
 *
 * <p><b>它不回答什么（重要）</b>：产品像不像、好不好看、文案对不对、品牌调性对不对——
 * 那是模型质检（{@code cp_output_check} 双基准）与人的判断。这里刻意不去碰：
 * 用像素统计冒充艺术判断，是这套系统一直拒绝做的事。</p>
 *
 * <p><b>度量口径（写清楚，避免被当成"事实"）</b>：</p>
 * <ul>
 *   <li>白：R/G/B 三通道都 ≥ 236（≈92.5%）算背景白像素；</li>
 *   <li>边缘白度：画布外圈 2% 采样带里"白像素"占比；</li>
 *   <li>背景基准色：四个角 5% 方形块的平均色；</li>
 *   <li>主体占比：与背景基准色距离 &gt; 30（0~255 空间）的采样像素占比；
 *       <b>这是个启发式度量</b>，非白底图（场景/卖点图）上它把"背景里的变化"也算成主体，
 *       所以它只作为参考项（SOFT），不作为硬性判据；</li>
 *   <li><b>贴边率</b>：最外圈 <b>0.5%</b> 那一环上"非背景像素"占该环的比例——
 *       直接回答"主体是不是被画布切掉了"。注意第一版写的是"主体像素里落在 2% 带内的比例"，
 *       真机一跑就发现这个口径不对：主图底部的柔和投影/渐变也会落进 2% 带，于是 800×800 的
 *       合格主图被判 3.5%（阈值 1%）——度量的是"边缘有没有非白像素"而不是"主体被裁"。
 *       改成最外环占比以后，量的是"画布边界上有多少不是背景"，被裁才显著升高。</li>
 * </ul>
 *
 * @author creative
 */
public final class CreativeImageRuleChecker {

    /** 判定为"白"的通道下限（0~255） */
    private static final int WHITE_CHANNEL = 236;

    /** 与背景基准色的距离阈值（0~255 空间） */
    private static final double SUBJECT_DISTANCE = 30d;

    /** 结论：没配规则，未做体检 */
    public static final String VERDICT_NOT_CONFIGURED = "NOT_CONFIGURED";
    /** 结论：全部通过 */
    public static final String VERDICT_PASS = "PASS";
    /** 结论：有 HARD 项未通过 */
    public static final String VERDICT_HARD_FAILED = "HARD_FAILED";
    /** 结论：HARD 全过，但有 SOFT 项未通过 */
    public static final String VERDICT_SOFT_ONLY = "SOFT_ONLY";
    /** 结论：图读不出来，没能体检 */
    public static final String VERDICT_UNREADABLE = "UNREADABLE";

    private CreativeImageRuleChecker() {
    }

    /**
     * 单个检查项结论。
     *
     * @param code   检查项编码（CANVAS_SQUARE / MIN_SIDE / NO_ALPHA / WHITE_BACKGROUND / SUBJECT_RATIO / EDGE_BLEED）
     * @param label  中文名（页面直接显示）
     * @param level  HARD / SOFT
     * @param ok     是否通过
     * @param detail 说明（含实测值，便于人工复核）
     */
    public record Finding(String code, String label, String level, boolean ok, String detail) {
    }

    /**
     * 体检结论。
     *
     * @param configured 本屏是否配置了规则（false 时其余字段为空，页面应显示"未配置"）
     * @param verdict    总结论（见本类常量）
     * @param metrics    确定性度量（可复算的证据）
     * @param findings   逐项结论
     * @param hardFailed 未通过的 HARD 项数
     * @param softFailed 未通过的 SOFT 项数
     */
    public record Report(boolean configured, String verdict, Map<String, Object> metrics,
                         List<Finding> findings, int hardFailed, int softFailed) {

        /**
         * 是否全部通过（未配置不算通过——"没检查"与"检查通过"必须能分开）。
         *
         * @return true 表示配置了规则且没有未通过项
         */
        public boolean passed() {
            return configured && hardFailed == 0 && softFailed == 0;
        }

        /** 转成可落库的 JSON 文本（手写序列化：这个类要能在脱离 Spring 的单测里跑）。 */
        public String toJson() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schema", "screen-qa-report/1");
            root.put("configured", configured);
            root.put("verdict", verdict);
            root.put("metrics", metrics);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Finding finding : findings) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", finding.code());
                item.put("label", finding.label());
                item.put("level", finding.level());
                item.put("ok", finding.ok());
                item.put("detail", finding.detail());
                items.add(item);
            }
            root.put("findings", items);
            root.put("hardFailed", hardFailed);
            root.put("softFailed", softFailed);
            return CreativeJson.write(root);
        }
    }

    /**
     * 按规则体检一张交付图。
     *
     * @param bytes 图片字节（可空）
     * @param rules 规则（{@link CreativeQaRules#NONE} 表示没配）
     * @return 体检结论
     */
    public static Report inspect(byte[] bytes, CreativeQaRules rules) {
        if (rules == null || !rules.configured()) {
            return new Report(false, VERDICT_NOT_CONFIGURED, Map.of(), List.of(), 0, 0);
        }
        BufferedImage image = read(bytes);
        if (image == null) {
            List<Finding> findings = List.of(new Finding("UNREADABLE", "交付图可读", CreativeQaRules.LEVEL_HARD,
                false, "这张图读不出来（不是有效图片或已损坏），没有做规则体检"));
            return new Report(true, VERDICT_UNREADABLE, Map.of(), findings, 1, 0);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, Math.min(width, height) / 512);
        int band = Math.max(2, Math.min(width, height) / 50);
        int ring = Math.max(1, Math.min(width, height) / 200);
        int patch = Math.max(4, Math.min(width, height) / 20);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("width", width);
        metrics.put("height", height);
        metrics.put("sampleStep", step);
        metrics.put("edgeBandPx", band);
        metrics.put("edgeRingPx", ring);
        metrics.put("whiteChannelMin", WHITE_CHANNEL);
        metrics.put("subjectDistance", (int) SUBJECT_DISTANCE);

        // 1) 四角基准色（背景基准）与角部白度
        double[] corners = cornerAverage(image, patch);
        long cornerTotal = 0;
        long cornerWhite = 0;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (!inCornerPatch(x, y, width, height, patch)) {
                    continue;
                }
                cornerTotal++;
                if (isWhite(image.getRGB(x, y))) {
                    cornerWhite++;
                }
            }
        }
        double cornerWhiteness = cornerTotal == 0 ? 0d : (double) cornerWhite / cornerTotal;

        // 2) 边缘白度 / 主体占比 / 贴边率（一次遍历同时算）
        long edgeTotal = 0;
        long edgeWhite = 0;
        long subjectTotal = 0;
        long ringTotal = 0;
        long ringSubject = 0;
        long sampled = 0;
        boolean alphaFound = false;
        boolean hasAlphaChannel = image.getColorModel().hasAlpha();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int argb = image.getRGB(x, y);
                sampled++;
                boolean onEdge = x < band || y < band || x >= width - band || y >= height - band;
                boolean onRing = x < ring || y < ring || x >= width - ring || y >= height - ring;
                if (onEdge) {
                    edgeTotal++;
                    if (isWhite(argb)) {
                        edgeWhite++;
                    }
                }
                if (hasAlphaChannel && ((argb >>> 24) & 0xFF) < 250) {
                    alphaFound = true;
                }
                if (onRing) {
                    ringTotal++;
                }
                if (distance(argb, corners) > SUBJECT_DISTANCE) {
                    subjectTotal++;
                    if (onRing) {
                        ringSubject++;
                    }
                }
            }
        }
        double edgeWhiteness = edgeTotal == 0 ? 0d : (double) edgeWhite / edgeTotal;
        double subjectRatio = sampled == 0 ? 0d : (double) subjectTotal / sampled;
        // 贴边率 = 最外环上非背景像素占该环的比例（"画布边界被主体占了多少"）
        double bleedRatio = ringTotal == 0 ? 0d : (double) ringSubject / ringTotal;

        metrics.put("cornerRgb", String.format("#%02X%02X%02X",
            clamp((int) Math.round(corners[0])), clamp((int) Math.round(corners[1])),
            clamp((int) Math.round(corners[2]))));
        metrics.put("cornerWhiteness", round(cornerWhiteness));
        metrics.put("edgeWhiteness", round(edgeWhiteness));
        metrics.put("subjectRatio", round(subjectRatio));
        metrics.put("edgeBleedRatio", round(bleedRatio));
        metrics.put("hasAlpha", alphaFound);
        metrics.put("square", width == height);

        // 3) 逐项判定（规则没开的项不出现——"没检查"不写成"通过"）
        List<Finding> findings = new ArrayList<>();
        if (rules.square()) {
            boolean ok = width == height;
            findings.add(new Finding("CANVAS_SQUARE", "画布为正方形", rules.levelOf("CANVAS_SQUARE"), ok,
                "实测画布 " + width + "×" + height + (ok ? "，是 1:1" : "，不是 1:1")));
        }
        if (rules.minSide() > 0) {
            boolean ok = Math.min(width, height) >= rules.minSide();
            findings.add(new Finding("MIN_SIDE", "最短边不小于 " + rules.minSide() + "px",
                rules.levelOf("MIN_SIDE"), ok,
                "实测最短边 " + Math.min(width, height) + "px"));
        }
        if (rules.alphaForbidden()) {
            boolean ok = !alphaFound;
            findings.add(new Finding("NO_ALPHA", "不含透明通道", rules.levelOf("NO_ALPHA"), ok,
                ok ? "没有半透明像素" : "存在半透明像素（主图交付不应带透明通道）"));
        }
        if (rules.whiteBackground()) {
            boolean ok = edgeWhiteness >= rules.minEdgeWhiteness();
            findings.add(new Finding("WHITE_BACKGROUND", "白底（边缘白度）", rules.levelOf("WHITE_BACKGROUND"), ok,
                "实测边缘白度 " + pct(edgeWhiteness) + "（要求 ≥ " + pct(rules.minEdgeWhiteness()) + "）"));
        }
        if (rules.subjectRatio()) {
            boolean ok = subjectRatio >= rules.subjectRatioMin();
            findings.add(new Finding("SUBJECT_RATIO", "主体占比（对背景基准色）", rules.levelOf("SUBJECT_RATIO"), ok,
                "实测主体占比 " + pct(subjectRatio) + "（要求 ≥ " + pct(rules.subjectRatioMin())
                    + "；这是启发式度量，非白底图上会把背景变化也算进去，仅供参考）"));
        }
        if (rules.edgeBleed()) {
            boolean ok = bleedRatio <= rules.maxBleedRatio();
            findings.add(new Finding("EDGE_BLEED", "主体未贴边（未被画布裁切）",
                rules.levelOf("EDGE_BLEED"), ok,
                "实测最外 " + ring + "px 环上非背景占比 " + pct(bleedRatio)
                    + "（要求 ≤ " + pct(rules.maxBleedRatio()) + "）"));
        }

        int hardFailed = 0;
        int softFailed = 0;
        for (Finding finding : findings) {
            if (finding.ok()) {
                continue;
            }
            if (CreativeQaRules.LEVEL_HARD.equals(finding.level())) {
                hardFailed++;
            } else {
                softFailed++;
            }
        }
        String verdict = hardFailed > 0 ? VERDICT_HARD_FAILED
            : (softFailed > 0 ? VERDICT_SOFT_ONLY : VERDICT_PASS);
        return new Report(true, verdict, metrics, findings, hardFailed, softFailed);
    }

    /**
     * 读图（ImageIO 支持的格式都能读；读不出返回 null，由调用方如实记为"没能体检"）。
     *
     * @param bytes 字节
     * @return 图片；读不出返回 null
     */
    private static BufferedImage read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 四个角方形块的平均色（背景基准）。
     *
     * @param image 图
     * @param patch 角块边长
     * @return RGB 均值
     */
    private static double[] cornerAverage(BufferedImage image, int patch) {
        int width = image.getWidth();
        int height = image.getHeight();
        long r = 0;
        long g = 0;
        long b = 0;
        long n = 0;
        int[][] origins = {{0, 0}, {Math.max(0, width - patch), 0},
            {0, Math.max(0, height - patch)}, {Math.max(0, width - patch), Math.max(0, height - patch)}};
        for (int[] origin : origins) {
            for (int y = origin[1]; y < Math.min(height, origin[1] + patch); y++) {
                for (int x = origin[0]; x < Math.min(width, origin[0] + patch); x++) {
                    int argb = image.getRGB(x, y);
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    n++;
                }
            }
        }
        if (n == 0) {
            return new double[]{255d, 255d, 255d};
        }
        return new double[]{(double) r / n, (double) g / n, (double) b / n};
    }

    private static boolean inCornerPatch(int x, int y, int width, int height, int patch) {
        boolean left = x < patch;
        boolean right = x >= width - patch;
        boolean top = y < patch;
        boolean bottom = y >= height - patch;
        return (left || right) && (top || bottom);
    }

    private static boolean isWhite(int argb) {
        return ((argb >> 16) & 0xFF) >= WHITE_CHANNEL
            && ((argb >> 8) & 0xFF) >= WHITE_CHANNEL
            && (argb & 0xFF) >= WHITE_CHANNEL;
    }

    private static double distance(int argb, double[] reference) {
        double dr = ((argb >> 16) & 0xFF) - reference[0];
        double dg = ((argb >> 8) & 0xFF) - reference[1];
        double db = (argb & 0xFF) - reference[2];
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100d);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * 极简 JSON 序列化（只处理 Map/List/String/Number/Boolean），避免在纯函数里依赖 Spring 上下文。
     */
    static final class CreativeJson {

        private CreativeJson() {
        }

        static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, value);
            return sb.toString();
        }

        private static void writeValue(StringBuilder sb, Object value) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Map<?, ?> map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeString(sb, String.valueOf(entry.getKey()));
                    sb.append(':');
                    writeValue(sb, entry.getValue());
                }
                sb.append('}');
            } else if (value instanceof Iterable<?> list) {
                sb.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeValue(sb, item);
                }
                sb.append(']');
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                writeString(sb, String.valueOf(value));
            }
        }

        private static void writeString(StringBuilder sb, String text) {
            sb.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }
    }

    /**
     * 从落库的体检 JSON 里取总结论（页面只读它，不需要解析全部明细）。
     *
     * @param json 体检 JSON（可空）
     * @return 结论；取不到返回 null
     */
    public static String verdictOf(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        int idx = json.indexOf("\"verdict\"");
        if (idx < 0) {
            return null;
        }
        int start = json.indexOf('"', idx + 9);
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        return end < 0 ? null : json.substring(start + 1, end);
    }
}
