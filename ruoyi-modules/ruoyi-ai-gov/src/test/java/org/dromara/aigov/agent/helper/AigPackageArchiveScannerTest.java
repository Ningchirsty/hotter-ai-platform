package org.dromara.aigov.agent.helper;

import org.dromara.aigov.agent.config.AigPackageSecurityProperties;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包体安全检查器的行为测试（F-02）。
 *
 * <p><b>为什么每条规则都要有正反例</b>：这类检查的失效方式是<b>安静地放行</b>——
 * 遍历判断少写一种形态（Windows 盘符）、魔数比较写反、上限配错单位，
 * 都表现为"某个不该收的包被收下了"，而系统不会报任何错。因此这里用**真实生成的
 * ZIP**（不是构造的对象）逐条钉住：合法的要过，每种形态都要被拦，且拦的理由要对上规则码。</p>
 *
 * <p>另外一条同样重要：<b>"没扫"不等于"通过"</b>——非 ZIP 体与开关关闭时
 * {@code scanned=false}，测试必须能区分这两件事，否则安全结论就是空话。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPackageArchiveScannerTest {

    private AigPackageSecurityProperties properties() {
        return new AigPackageSecurityProperties();
    }

    private AigPackageArchiveScanner scanner() {
        return new AigPackageArchiveScanner(properties());
    }

    /**
     * 造一个 ZIP。
     *
     * @param entries 条目名 → 内容
     * @return 字节
     */
    private static byte[] zip(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, byte[]> one(String name, String content) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        map.put(name, content.getBytes(StandardCharsets.UTF_8));
        return map;
    }

    @Test
    @DisplayName("正常的声明式包通过：Manifest + 文本资源 + 图片资源")
    void cleanPackagePasses() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("SKILL.md", "# 技能\n步骤一".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/logo.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        entries.put("manifest.json", "{\"packageCode\":\"p\"}".getBytes(StandardCharsets.UTF_8));

        AigArchiveScanResult result = scanner().scan(zip(entries));

        assertTrue(result.isPass(), "合法包不该被拦：" + result.getDetail());
        assertTrue(result.isScanned(), "这次是真扫过的");
        assertTrue(result.getHitRules().isEmpty());
        assertTrue(result.getDetail().contains("扫描 3 个条目"), result.getDetail());
    }

    @Test
    @DisplayName("★ 路径穿越（../）必须拦：它会在解压时写到包根之外")
    void pathTraversalIsRejected() {
        AigArchiveScanResult result = scanner().scan(zip(one("../../etc/passwd", "x")));

        assertFalse(result.isPass());
        assertTrue(result.hitRuleCodes().contains(
                AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS.getCode()),
            "越界写入属 §6.2-1：" + result.getDetail());
        assertTrue(result.getDetail().contains("上跳路径段"), result.getDetail());
    }

    @Test
    @DisplayName("★ 绝对路径与 Windows 盘符必须拦（两种形态都要有例子，少一种就有一条路能过）")
    void absolutePathsAreRejected() {
        assertFalse(scanner().scan(zip(one("/etc/shadow", "x"))).isPass(), "Unix 绝对路径");
        assertFalse(scanner().scan(zip(one("C:\\Windows\\system32\\x.dll", "x"))).isPass(), "Windows 盘符路径");
        assertFalse(scanner().scan(zip(one("a\\..\\b", "x"))).isPass(), "反斜杠分隔（跨平台解压位置不可预期）");
    }

    @Test
    @DisplayName("★ 脚本/可执行扩展名必须拦（平台无法界定其行为）")
    void riskyExtensionsAreRejected() {
        for (String name : new String[]{"install.sh", "run.ps1", "x.exe", "lib.so", "a.jar", "start.bat"}) {
            AigArchiveScanResult result = scanner().scan(zip(one(name, "echo hi")));
            assertFalse(result.isPass(), name + " 应被拦");
            assertTrue(result.hitRuleCodes().contains(
                    AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION.getCode()),
                name + " 应归到 §6.2-3：" + result.getDetail());
        }
    }

    @Test
    @DisplayName("★ 魔数嗅探：扩展名伪装成 .md 的 ELF 也要拦（否则改名就绕过了）")
    void binaryMagicIsDetectedRegardlessOfExtension() {
        Map<String, byte[]> entries = one("readme.md", "x");
        entries.put("innocent.md", new byte[]{0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 9, 9});

        AigArchiveScanResult result = scanner().scan(zip(entries));

        assertFalse(result.isPass(), "ELF 正文必须拦，扩展名无关：" + result.getDetail());
        assertTrue(result.getDetail().contains("ELF"), result.getDetail());
    }

    @Test
    @DisplayName("★ 正文里的私钥/Docker Socket 特征必须拦（§6.2-1 明列生产密钥与 Docker Socket）")
    void riskyMarkersAreRejected() {
        AigArchiveScanResult key = scanner().scan(zip(one("notes.txt",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIE...")));
        assertFalse(key.isPass(), key.getDetail());
        assertTrue(key.hitRuleCodes().contains(
            AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS.getCode()), key.getDetail());

        AigArchiveScanResult docker = scanner().scan(zip(one("docker-compose.yml",
            "volumes:\n  - /var/run/docker.sock:/var/run/docker.sock")));
        assertFalse(docker.isPass(), docker.getDetail());
    }

    @Test
    @DisplayName("★ zip bomb 形态必须拦：条目数与压缩比都有上限（否则一次注册就能把磁盘/内存打满）")
    void bombShapesAreRejected() {
        AigPackageSecurityProperties small = properties();
        small.setMaxEntries(3);
        Map<String, byte[]> many = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            many.put("f" + i + ".txt", "x".getBytes(StandardCharsets.UTF_8));
        }
        AigArchiveScanResult byCount = new AigPackageArchiveScanner(small).scan(zip(many));
        assertFalse(byCount.isPass(), "条目数超限应被拦：" + byCount.getDetail());
        assertTrue(byCount.getDetail().contains("条目数超过上限"), byCount.getDetail());

        // 高压缩比：1MB 的重复内容压完远小于 1/200
        AigPackageSecurityProperties ratio = properties();
        ratio.setMaxCompressionRatio(50);
        byte[] zeros = new byte[1024 * 1024];
        AigArchiveScanResult byRatio = new AigPackageArchiveScanner(ratio).scan(zip(one("big.txt",
            new String(zeros, StandardCharsets.ISO_8859_1))));
        assertFalse(byRatio.isPass(), "压缩比超限应被拦：" + byRatio.getDetail());
    }

    @Test
    @DisplayName("★ 「没扫」不等于「通过」：非 ZIP 体与开关关闭都要如实标记 scanned=false")
    void notScannedIsReportedHonestly() {
        AigArchiveScanResult plain = scanner().scan("这不是压缩包".getBytes(StandardCharsets.UTF_8));
        assertTrue(plain.isPass(), "不因为'我们没查'就拒绝一个合法包");
        assertFalse(plain.isScanned(), "但必须能看出这次没扫");
        assertTrue(plain.getDetail().contains("不是 ZIP"), plain.getDetail());

        AigPackageSecurityProperties off = properties();
        off.setEnabled(false);
        AigArchiveScanResult disabled = new AigPackageArchiveScanner(off).scan(zip(one("a.txt", "x")));
        assertTrue(disabled.isPass());
        assertFalse(disabled.isScanned(), "开关关闭时必须标记未扫描");
        assertTrue(disabled.getDetail().contains("开关关闭"), disabled.getDetail());
    }

    @Test
    @DisplayName("包体为空/损坏：如实返回，不抛异常（注册路径要靠结论而不是异常来判断）")
    void emptyAndBrokenBodiesAreSafe() {
        assertEquals(false, scanner().scan(null).isScanned());
        assertEquals(false, scanner().scan(new byte[0]).isScanned());

        byte[] broken = {'P', 'K', 0x03, 0x04, 1, 2, 3, 4, 5};
        AigArchiveScanResult result = scanner().scan(broken);
        assertFalse(result.isPass(), "损坏的压缩包内容不可界定，不能当通过：" + result.getDetail());
        assertTrue(result.getDetail().contains("没有任何可读条目"),
            "截断的 ZIP 头会让 getNextEntry() 直接返回 null——必须显式判空，否则坏包会「扫描 0 个条目」地通过："
                + result.getDetail());
    }

}
