package org.dromara.aigov.agent.helper;

import org.dromara.common.core.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 沙箱产物清单：解析 {@code result.json} 里声明的产物，并做路径安全校验（ADR-016）。
 *
 * <h3>这份清单是唯一的校验基准</h3>
 * <p><b>只信账本、不信上传方</b>：平台登记的 {@code result.json} 原文里写了每个产物的
 * {@code path/bytes/sha256}（由宿主 worker 产出：{@code script/deploy/sandbox-run.sh}）。
 * 上传产物字节时，服务端拿这份声明去比对——上传方<b>塞不进</b>清单外的文件，
 * 也<b>换不掉</b>清单内文件的内容。</p>
 *
 * <h3>为什么要在这里把路径管死</h3>
 * <p>{@code path} 是上传方给的，而它有两个危险的用法：①拿去拼对象存储键（路径穿越就有了落点）；
 * ②拿去拼 {@code Content-Disposition} 的文件名（响应头注入）。本类因此：
 * <b>只接受相对 POSIX 路径</b>，拒绝绝对路径、{@code ..}、反斜杠与控制字符；
 * 并提供 {@link #downloadFileName} 给响应头用。<b>对象键一律由服务端按 sha256 生成</b>，
 * 根本不用 path 拼——这一条比"把 path 洗干净"更彻底。</p>
 *
 * @author ai-gov
 */
public final class AigSandboxArtifactManifest {

    /**
     * 声明里的 sha256 形态（64 位十六进制，大小写不敏感——worker 输出小写，但不因此拒绝大写）
     */
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private AigSandboxArtifactManifest() {
    }

    /**
     * 一条声明的产物。
     *
     * @param path   归一化后的相对路径
     * @param bytes  声明字节数
     * @param sha256 声明摘要（小写）
     */
    public record Entry(String path, long bytes, String sha256) {
    }

    /**
     * 解析 {@code result.json} 里声明的产物清单。
     *
     * <p>任何形状问题都<b>拒绝</b>而不是"尽力解析"：解析失败被当成"没有产物"时，
     * 上传会得到"清单里没有这个产物"这种误导性提示，而真正的问题是账本本身坏了。</p>
     *
     * @param resultJson result.json 原文
     * @param mapper     JSON 解析器（由调用方注入，便于单测）
     * @return 归一化路径 → 声明（保持声明顺序）
     * @throws IllegalArgumentException 原文为空 / 非法 JSON / 缺 artifacts / artifacts 非数组 / 条目缺字段或形态非法 / 重复路径
     */
    public static Map<String, Entry> parse(String resultJson, JsonMapper mapper) {
        if (StringUtils.isBlank(resultJson)) {
            throw new IllegalArgumentException("该作业的 result.json 为空，无法校验产物清单");
        }
        JsonNode root;
        try {
            root = mapper.readTree(resultJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("result.json 不是合法 JSON，无法校验产物清单：" + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("result.json 必须是一个 JSON 对象");
        }
        JsonNode artifacts = root.get("artifacts");
        if (artifacts == null || artifacts.isNull()) {
            throw new IllegalArgumentException("该作业的 result.json 里没有 artifacts 清单："
                + "没有清单就没有校验基准，上传的字节无从采信（旧版 worker 请升级后重跑）");
        }
        if (!artifacts.isArray()) {
            throw new IllegalArgumentException("result.json 的 artifacts 必须是数组");
        }

        Map<String, Entry> entries = new LinkedHashMap<>();
        int index = 0;
        for (JsonNode node : artifacts) {
            index++;
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("artifacts 第 " + index + " 项不是 JSON 对象");
            }
            String rawPath = text(node, "path");
            String path = normalizePath(rawPath);
            if (entries.containsKey(path)) {
                throw new IllegalArgumentException("artifacts 里同一个路径出现了两次：" + path
                    + "（重复声明会让「存了几份」变成假账）");
            }
            JsonNode bytesNode = node.get("bytes");
            if (bytesNode == null || !bytesNode.isNumber() || bytesNode.asLong() < 0) {
                throw new IllegalArgumentException("artifacts 第 " + index + " 项（" + path
                    + "）的 bytes 必须是非负整数");
            }
            String sha = text(node, "sha256").toLowerCase();
            if (!SHA256.matcher(sha).matches()) {
                throw new IllegalArgumentException("artifacts 第 " + index + " 项（" + path
                    + "）的 sha256 不是 64 位十六进制");
            }
            entries.put(path, new Entry(path, bytesNode.asLong(), sha));
        }
        return entries;
    }

    /**
     * 归一化并校验产物路径。
     *
     * <p>规则：非空；不允许以 {@code /} 开头（绝对路径）；不允许反斜杠（Windows 风格分隔符会让
     * "看起来是相对路径"的值在别的系统上变成绝对路径）；不允许任何 {@code ..} 片段；
     * 不允许控制字符（响应头注入）；去掉前缀 {@code ./} 与多余的 {@code //}。</p>
     *
     * @param rawPath 原始路径
     * @return 归一化后的相对路径
     * @throws IllegalArgumentException 非法路径
     */
    public static String normalizePath(String rawPath) {
        if (StringUtils.isBlank(rawPath)) {
            throw new IllegalArgumentException("产物路径不能为空");
        }
        String path = rawPath.trim();
        if (path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("产物路径不允许反斜杠：" + path
                + "（只接受相对 POSIX 路径，反斜杠在别的系统上会变成绝对路径）");
        }
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) < 0x20 || path.charAt(i) == 0x7f) {
                throw new IllegalArgumentException("产物路径含有控制字符，拒绝：" + printable(path));
            }
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("产物路径必须是相对路径，不能以 / 开头：" + path);
        }
        // 去掉 ./ 前缀与重复斜杠（find 的输出可能带 ./）
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("产物路径去掉前后缀后为空，拒绝：" + printable(rawPath));
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("产物路径不允许 .. ：" + path);
            }
        }
        return path;
    }

    /**
     * 由路径推出下载文件名（用于 {@code Content-Disposition}）。
     *
     * <p>只取最后一段，并把引号/分号/控制字符换掉——响应头里放未经处理的用户输入是 header injection。
     * 空名兜底为 {@code artifact}。</p>
     *
     * @param path 归一化路径
     * @return 安全的文件名（可能不是 ASCII；调用方另行给出 RFC 5987 形式）
     */
    public static String downloadFileName(String path) {
        String name = path == null ? "" : path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder safe = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '"' || c == ';' || c == '\'' || c == '\\') {
                safe.append('_');
            } else {
                safe.append(c);
            }
        }
        String result = safe.toString().trim();
        return result.isEmpty() ? "artifact" : result;
    }

    /**
     * 对字节实算 SHA-256（小写十六进制）。
     *
     * @param body 字节
     * @return 摘要
     */
    public static String sha256(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然带 SHA-256；真缺了也不能静默返回一个假摘要
            throw new IllegalStateException("计算产物 SHA-256 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 取必填文本字段。
     *
     * @param node  条目节点
     * @param field 字段名
     * @return 文本
     * @throws IllegalArgumentException 缺失或空白
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString() || StringUtils.isBlank(value.stringValue())) {
            throw new IllegalArgumentException("artifacts 条目缺少必填字段 " + field
                + "（必须是字符串）");
        }
        return value.stringValue().trim();
    }

    /**
     * 控制字符替换（日志/报错里不带原始控制字符）。
     *
     * @param text 文本
     * @return 可打印文本
     */
    private static String printable(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c < 0x20 || c == 0x7f ? '?' : c);
        }
        return out.toString();
    }

    /**
     * UTF-8 的 RFC 5987 编码（用于 {@code filename*=UTF-8''...}）。
     *
     * @param value 值
     * @return 编码后的值
     */
    public static String rfc5987(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c >= 0x30 && c <= 0x39 || c >= 0x41 && c <= 0x5a || c >= 0x61 && c <= 0x7a
                || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }

}
