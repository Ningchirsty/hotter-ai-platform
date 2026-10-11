package org.dromara.aigov.agent.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱产物清单解析与路径安全测试（ADR-016）。
 *
 * <p><b>这条测试守的是"只信账本"这条不变式</b>：清单解析错一点，上传方就能塞进一个
 * 账本里没有的文件、或换掉清单内文件的内容——而这两种都不会报错，只会让证据悄悄失真。
 * 路径部分守的是另一半：{@code path} 是上传方给的，它要去拼对象键与响应头文件名。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxArtifactManifestTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String SHA_A = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static String resultJson(String artifacts) {
        return "{\"jobId\":\"JOB-1\",\"image\":\"img:1\",\"exitCode\":0,\"timedOut\":false,"
            + "\"network\":\"none\",\"artifacts\":" + artifacts + "}";
    }

    @Test
    @DisplayName("解析清单：路径归一化（去 ./ 、压重复斜杠）、摘要统一小写、顺序保持")
    void parsesManifest() {
        String json = resultJson("[{\"path\":\"./out//a.txt\",\"bytes\":3,\"sha256\":\""
            + SHA_A.toUpperCase() + "\"},{\"path\":\"b/c.bin\",\"bytes\":0,\"sha256\":\"" + SHA_A + "\"}]");

        Map<String, AigSandboxArtifactManifest.Entry> entries =
            AigSandboxArtifactManifest.parse(json, MAPPER);

        assertEquals(2, entries.size());
        assertEquals(java.util.List.of("out/a.txt", "b/c.bin"), entries.keySet().stream().toList());
        assertEquals(3L, entries.get("out/a.txt").bytes());
        assertEquals(SHA_A, entries.get("out/a.txt").sha256(), "摘要统一成小写");
        assertEquals(0L, entries.get("b/c.bin").bytes(), "0 字节产物是合法声明");
    }

    @Test
    @DisplayName("★ 清单为空数组也要能解析（返回空表），但缺 artifacts 必须拒绝——两者不是一回事")
    void emptyListIsAllowedButMissingManifestIsNot() {
        assertTrue(AigSandboxArtifactManifest.parse(resultJson("[]"), MAPPER).isEmpty());

        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson("null"), MAPPER)).getMessage()
            .contains("没有 artifacts"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(
                "{\"jobId\":\"J\",\"artifacts_typo\":[]}", MAPPER)).getMessage().contains("没有 artifacts"));
    }

    @Test
    @DisplayName("形状不对：非法 JSON / 非对象 / artifacts 非数组 / 条目非对象，各自给出可读原因")
    void rejectsBadShapes() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse("{not json", MAPPER)).getMessage().contains("不是合法 JSON"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse("[1,2]", MAPPER)).getMessage().contains("必须是一个 JSON 对象"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson("\"oops\""), MAPPER)).getMessage()
            .contains("必须是数组"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson("[\"oops\"]"), MAPPER)).getMessage()
            .contains("不是 JSON 对象"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(null, MAPPER)).getMessage().contains("为空"));
    }

    @Test
    @DisplayName("条目字段：缺 path / 缺 bytes / bytes 非数字或负数 / sha 不是 64 位十六进制 → 都拒绝并指出第几项")
    void rejectsBadEntries() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson(
                "[{\"bytes\":1,\"sha256\":\"" + SHA_A + "\"}]"), MAPPER)).getMessage().contains("缺少必填字段 path"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson(
                "[{\"path\":\"a\",\"sha256\":\"" + SHA_A + "\"}]"), MAPPER)).getMessage().contains("bytes"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson(
                "[{\"path\":\"a\",\"bytes\":-1,\"sha256\":\"" + SHA_A + "\"}]"), MAPPER)).getMessage()
            .contains("非负整数"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson(
                "[{\"path\":\"a\",\"bytes\":1,\"sha256\":\"deadbeef\"}]"), MAPPER)).getMessage()
            .contains("64 位十六进制"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson(
                "[{\"path\":\"a\",\"bytes\":\"x\",\"sha256\":\"" + SHA_A + "\"}]"), MAPPER)).getMessage()
            .contains("第 1 项"), "报错要指出是第几项，否则一份长清单只能靠猜");
    }

    @Test
    @DisplayName("★ 同一路径声明两次 → 拒绝（重复声明会让「存了几份」变成假账）")
    void rejectsDuplicatedPath() {
        String one = "{\"path\":\"out/a.txt\",\"bytes\":1,\"sha256\":\"" + SHA_A + "\"}";
        String sameButPrefixed = "{\"path\":\"./out/a.txt\",\"bytes\":2,\"sha256\":\"" + SHA_A + "\"}";

        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.parse(resultJson("[" + one + "," + sameButPrefixed + "]"), MAPPER))
            .getMessage().contains("两次"), "归一化后同路径也算重复");
    }

    @Test
    @DisplayName("★ 路径安全：绝对路径 / .. / 反斜杠 / 控制字符 / 空 一律拒绝")
    void rejectsUnsafePaths() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("/etc/passwd")).getMessage().contains("相对路径"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("a/../../b")).getMessage().contains(".."));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("..\\windows\\x")).getMessage().contains("反斜杠"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("a\u0001b")).getMessage().contains("控制字符"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("   ")).getMessage().contains("不能为空"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigSandboxArtifactManifest.normalizePath("./")).getMessage().contains("为空"));
    }

    @Test
    @DisplayName("路径归一化：去 ./ 前缀、压 //、去尾部斜杠；普通相对路径原样")
    void normalizesPaths() {
        assertEquals("out/a.txt", AigSandboxArtifactManifest.normalizePath("./out/a.txt"));
        assertEquals("out/a.txt", AigSandboxArtifactManifest.normalizePath("out//a.txt"));
        assertEquals("out/a.txt", AigSandboxArtifactManifest.normalizePath("out/a.txt/"));
        assertEquals("a/b/c", AigSandboxArtifactManifest.normalizePath("a/b/c"));
        assertEquals("文件.txt", AigSandboxArtifactManifest.normalizePath("文件.txt"));
    }

    @Test
    @DisplayName("sha256：已知向量对得上（摘要错=校验形同虚设）")
    void computesSha256() {
        assertEquals(SHA_A, AigSandboxArtifactManifest.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AigSandboxArtifactManifest.sha256(new byte[0]), "空文件的摘要也要算得对");
    }

    @Test
    @DisplayName("★ 下载文件名：只取最后一段，引号/分号/控制字符换成下划线（响应头注入）")
    void downloadFileNameIsSafe() {
        assertEquals("a.txt", AigSandboxArtifactManifest.downloadFileName("out/a.txt"));
        // 只取最后一段（a/ 是目录），引号与分号换成下划线
        assertEquals("_x__y.txt", AigSandboxArtifactManifest.downloadFileName("a/\"x\";y.txt"));
        assertEquals("artifact", AigSandboxArtifactManifest.downloadFileName("out/"));
        assertEquals("artifact", AigSandboxArtifactManifest.downloadFileName(null));
        assertEquals("文件.txt", AigSandboxArtifactManifest.downloadFileName("dir/文件.txt"));
    }

    @Test
    @DisplayName("RFC 5987 编码：非 ASCII 与空格被百分号编码，ASCII 安全字符保留")
    void rfc5987Encodes() {
        assertEquals("a%20b.txt", AigSandboxArtifactManifest.rfc5987("a b.txt"));
        assertEquals("a-._~", AigSandboxArtifactManifest.rfc5987("a-._~"));
        assertTrue(AigSandboxArtifactManifest.rfc5987("文件.txt").startsWith("%E6%96%87"));
    }

}
