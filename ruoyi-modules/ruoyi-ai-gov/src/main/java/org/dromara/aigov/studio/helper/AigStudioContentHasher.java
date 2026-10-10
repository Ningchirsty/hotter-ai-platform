package org.dromara.aigov.studio.helper;

import cn.hutool.crypto.digest.DigestUtil;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * 训练草稿内容的<b>规范化 + 哈希</b>（专题 C §C3.1、§C11 ST-001）。
 *
 * <h3>为什么不能直接对入库的那串 JSON 算哈希</h3>
 * <p>页面上那句「未发布改动」的判据是 {@code contentHash != lastPublishedHash}。
 * 如果哈希直接作用在原始文本上，那么**只要序列化器把键的顺序换了、或者谁多敲了一个换行**，
 * 哈希就会变——界面就会指着一份**内容完全没变**的草稿说"你有未发布改动"。
 * 反过来更危险：两人各自保存后哈希恰好相等，于是真实的改动被判成"没改"。
 * 所以必须先<b>规范化</b>再哈希：键排序、去掉无意义空白、数值统一形态。</p>
 *
 * <h3>规范化规则（可断言、可复现）</h3>
 * <ol>
 *     <li>对象：按键的**字符序**排序后输出（{@link TreeMap}）；</li>
 *     <li>数组：保持原顺序（顺序是内容的一部分）；</li>
 *     <li>字符串：标准 JSON 转义，非 ASCII 原样保留（UTF-8）；</li>
 *     <li>数值：按 {@link BigDecimal#stripTrailingZeros()} 归一，因此 {@code 1} 与 {@code 1.0}
 *         视为同一内容——它们本来就是同一个数，不该被判成改动；</li>
 *     <li>布尔/null：输出 {@code true}/{@code false}/{@code null}；</li>
 *     <li>不输出任何多余空白。</li>
 * </ol>
 *
 * <p><b>数值归一是一处刻意的取舍</b>：它会让 {@code 1} 与 {@code 1.0} 相等。
 * 这在"内容是否变了"这个语义下是对的（变了的是文本，不是内容）；代价是
 * "把精度从 1.0 改成 1"这种纯文本差异不会被报成改动——而那本来也不该报。</p>
 *
 * @author ai-gov
 */
public final class AigStudioContentHasher {

    /**
     * 解析用（只用到 readValue，输出由本类自己拼，以保证跨版本/跨库一致）
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AigStudioContentHasher() {
    }

    /**
     * 计算内容哈希（sha256 十六进制小写，64 位）。
     *
     * @param json 草稿内容 JSON；null 视为 JSON {@code null}（其哈希照算）
     * @return 十六进制小写 sha256
     * @throws IllegalArgumentException 入参为空白串，或不是合法 JSON
     */
    public static String hash(String json) {
        return DigestUtil.sha256Hex(canonicalize(json));
    }

    /**
     * 把内容 JSON 规范化成稳定形态（键排序、无多余空白、数值归一）。
     *
     * @param json 原始 JSON；null 返回 {@code "null"}
     * @return 规范化后的 JSON
     * @throws IllegalArgumentException 入参为空白串，或不是合法 JSON
     */
    public static String canonicalize(String json) {
        if (json == null) {
            return "null";
        }
        if (json.isBlank()) {
            throw new IllegalArgumentException("草稿内容不能为空白：无法计算内容哈希");
        }
        Object parsed;
        try {
            parsed = MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("草稿内容不是合法 JSON，无法计算内容哈希：" + e.getMessage(), e);
        }
        StringBuilder out = new StringBuilder(json.length());
        write(parsed, out);
        return out.toString();
    }

    /**
     * 递归写出规范化 JSON。
     *
     * @param value 解析后的值（Map/List/String/Number/Boolean/null）
     * @param out   输出缓冲
     */
    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            // 键排序：内容哈希不能被序列化顺序影响
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(e.getKey(), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out);
            }
            out.append(']');
            return;
        }
        if (value instanceof String s) {
            writeString(s, out);
            return;
        }
        if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
            return;
        }
        if (value instanceof Number n) {
            // 归一：1 与 1.0 是同一个内容
            out.append(new BigDecimal(n.toString()).stripTrailingZeros().toPlainString());
            return;
        }
        // 其余类型（理论上不会出现）按字符串处理，保证不会静默丢内容
        writeString(String.valueOf(value), out);
    }

    /**
     * 写一个 JSON 字符串字面量（标准转义）。
     *
     * @param s   字符串
     * @param out 输出缓冲
     */
    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

}
