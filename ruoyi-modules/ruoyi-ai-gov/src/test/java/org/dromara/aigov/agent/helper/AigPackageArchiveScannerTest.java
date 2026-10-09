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

    // ------------------------------------------------------------------
    // 第二切片（2026-10-10）：符号链接 / 特殊文件、全文凭据扫描、非 ZIP 形态判定
    // ------------------------------------------------------------------

    /**
     * 定位 EOCD（{@code PK\x05\x06}）。
     *
     * @param body ZIP 字节
     * @return 偏移；找不到返回 -1
     */
    private static int eocd(byte[] body) {
        for (int i = body.length - 22; i >= 0; i--) {
            if (body[i] == 'P' && body[i + 1] == 'K' && body[i + 2] == 5 && body[i + 3] == 6) {
                return i;
            }
        }
        return -1;
    }

    private static long u32(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8) | ((b[o + 2] & 0xFFL) << 16)
            | ((b[o + 3] & 0xFFL) << 24);
    }

    private static void putU16(byte[] b, int o, int v) {
        b[o] = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void putU32(byte[] b, int o, long v) {
        b[o] = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
        b[o + 2] = (byte) ((v >> 16) & 0xFF);
        b[o + 3] = (byte) ((v >> 24) & 0xFF);
    }

    /**
     * 把第一个中央目录条目的 Unix 模式位改成给定类型。
     *
     * <p><b>为什么要在字节上打补丁，而不是用 JDK API 造</b>：{@code java.util.zip} 既不能写
     * Unix 模式位也不能写符号链接条目，而"平台收到的包"恰恰可能是别处（zip 命令、Python、
     * Go）造出来的。所以这里用 JDK 造一个<b>流有效</b>的 ZIP，再只改中央目录里的宿主与模式位——
     * 这样"流式读得出条目、中央目录说它是符号链接"这个真实场景就被复现了。</p>
     *
     * @param body     ZIP 字节（会就地修改）
     * @param unixMode Unix 模式（含类型位与权限位）
     */
    private static void patchFirstEntryUnixMode(byte[] body, int unixMode) {
        int eocd = eocd(body);
        int cd = (int) u32(body, eocd + 16);
        assertEquals('P', (char) body[cd], "中央目录签名应在 EOCD 指出的偏移处");
        // version made by：高字节 = 宿主（3 = Unix），低字节 = 版本
        putU16(body, cd + 4, (3 << 8) | 20);
        // external file attributes：高 16 位是 Unix 模式位
        putU32(body, cd + 38, ((long) unixMode & 0xFFFFL) << 16);
    }

    /**
     * 把 EOCD 里"总条目数"改成给定值（用于制造"声明与内容对不上"）。
     *
     * @param body  ZIP 字节
     * @param count 新的声明条目数
     */
    private static void patchEocdEntryCount(byte[] body, int count) {
        putU16(body, eocd(body) + 10, count);
    }

    @Test
    @DisplayName("★ 符号链接条目必须拦：先落一个指向包外的链接、后续条目穿过它写入（zip-slip 变体）")
    void symlinkEntryIsRejected() {
        byte[] body = zip(one("SKILL.md", "# 技能\n正常内容"));
        // S_IFLNK | 0777
        patchFirstEntryUnixMode(body, 0xA000 | 0777);

        AigArchiveScanResult result = scanner().scan(body);

        assertFalse(result.isPass(), "符号链接必须拦：" + result.getDetail());
        assertTrue(result.hitRuleCodes().contains(
                AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS.getCode()), result.getDetail());
        assertTrue(result.getDetail().contains("符号链接"), result.getDetail());
    }

    @Test
    @DisplayName("★ 特殊文件（FIFO/设备/套接字）也要拦，且它归到越权而不是可执行")
    void specialFileEntryIsRejected() {
        byte[] body = zip(one("pipe", "x"));
        patchFirstEntryUnixMode(body, 0x1000 | 0644); // S_IFIFO

        AigArchiveScanResult result = scanner().scan(body);

        assertFalse(result.isPass(), "特殊文件必须拦：" + result.getDetail());
        assertTrue(result.getDetail().contains("特殊文件"), result.getDetail());
        assertTrue(result.hitRuleCodes().contains(
            AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS.getCode()), result.getDetail());
    }

    @Test
    @DisplayName("反例控制：Unix 普通文件（host=Unix 且 S_IFREG）不能被误拦——否则合法包全被挡")
    void regularUnixFileStillPasses() {
        byte[] body = zip(one("SKILL.md", "# 技能\n正常内容"));
        patchFirstEntryUnixMode(body, 0x8000 | 0644); // S_IFREG

        AigArchiveScanResult result = scanner().scan(body);

        assertTrue(result.isPass(), "普通文件不该被拦：" + result.getDetail());
        assertTrue(result.isScanned());
    }

    @Test
    @DisplayName("★ 凭据特征出现在头部嗅探窗口之外也要拦（把私钥放到第 5KB 之后是最容易的绕过）")
    void credentialBeyondHeadWindowIsRejected() {
        String filler = "a".repeat(6000); // > sniffBytes(4096)
        AigArchiveScanResult result = scanner().scan(zip(one("notes.txt",
            filler + "\naws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n")));

        assertFalse(result.isPass(), "第一切片只嗅头部 4096 字节，这条会漏；第二切片必须拦住："
            + result.getDetail());
        assertTrue(result.getDetail().contains("aws_secret_access_key"), result.getDetail());
    }

    @Test
    @DisplayName("★ 特征串横跨读取分片边界也要拦（滑动窗口；否则把特征对准 8KB 边界就能绕过）")
    void credentialStraddlingChunkBoundaryIsRejected() {
        // 读缓冲是 8192：让特征正好从第 8192-10 字节开始，前 10 个字符落在第一个分片里
        String filler = "b".repeat(8192 - 10);
        AigArchiveScanResult result = scanner().scan(zip(one("notes.txt",
            filler + "aws_secret_access_key=AKIAIOSFODNN7EXAMPLE")));

        assertFalse(result.isPass(), "跨分片的特征也必须命中：" + result.getDetail());
        assertTrue(result.getDetail().contains("aws_secret_access_key")
            || result.getDetail().contains("AKIA"), result.getDetail());
    }

    @Test
    @DisplayName("全文扫描的上限之外不扫，且结论里如实写明——不把「扫了一部分」说成「全文扫过了」")
    void textScanBoundaryIsReportedHonestly() {
        // 注意两个窗口都要缩小：头部嗅探（sniffBytes）本身也搜特征串，它的覆盖是"前 sniffBytes"，
        // 全文扫描把覆盖扩到 maxTextScanBytes。只缩后者的话，第 500 字节处的特征仍会被头部嗅探抓到
        // ——我第一次就是这么写的，断言失败暴露的是**测试前提错了**（覆盖度比设想的大），不是代码错。
        AigPackageSecurityProperties tiny = properties();
        tiny.setSniffBytes(100);
        tiny.setMaxTextScanBytes(100L);
        AigArchiveScanResult result = new AigPackageArchiveScanner(tiny).scan(zip(one("notes.txt",
            "c".repeat(500) + "aws_secret_access_key=x")));

        assertTrue(result.isPass(), "两个窗口之外的内容按设计不扫：" + result.getDetail());
        assertTrue(result.getDetail().contains("有条目超过该上限故只扫到上限"),
            "必须如实说明只扫了一部分：" + result.getDetail());

        // 对照：把全文扫描放大到能覆盖它，同一份内容就会被抓住（证明差别确实来自扫描窗口）
        AigPackageSecurityProperties wider = properties();
        wider.setSniffBytes(100);
        wider.setMaxTextScanBytes(4096L);
        AigArchiveScanResult caught = new AigPackageArchiveScanner(wider).scan(zip(one("notes.txt",
            "c".repeat(500) + "aws_secret_access_key=x")));
        assertFalse(caught.isPass(), "放大窗口后应命中：" + caught.getDetail());
    }

    @Test
    @DisplayName("★ 非 ZIP 形态要能说出「是什么」，并且默认仍不判不拒（改准入策略要显式开开关）")
    void nonZipFormsAreNamedAndOptionallyRejected() {
        byte[] gzip = {0x1F, (byte) 0x8B, 8, 0, 0, 0, 0, 0, 0, 0};
        AigArchiveScanResult named = scanner().scan(gzip);
        assertTrue(named.isPass(), "默认维持第一切片行为：非 ZIP 不拒");
        assertFalse(named.isScanned(), "但要如实标记未扫描");
        assertTrue(named.getDetail().contains("gzip"), "现在要能说出是哪种形态：" + named.getDetail());

        AigPackageSecurityProperties strict = properties();
        strict.setRejectNonZipArchives(true);
        AigArchiveScanResult rejected = new AigPackageArchiveScanner(strict).scan(gzip);
        assertFalse(rejected.isPass(), "开了开关就该拒：" + rejected.getDetail());
        assertTrue(rejected.getDetail().contains("gzip"), rejected.getDetail());

        // 纯文本不该被"非 ZIP 压缩包"这条开关误伤（它不是压缩包）
        AigArchiveScanResult text = new AigPackageArchiveScanner(strict)
            .scan("就是一个文本文件".getBytes(StandardCharsets.UTF_8));
        assertTrue(text.isPass(), "文本不是压缩包，不该被这条开关拒：" + text.getDetail());
        assertTrue(text.getDetail().contains("纯文本"), text.getDetail());
    }

    @Test
    @DisplayName("★ 中央目录声明的条目数少于实际可读条目：声明与内容对不上，必须拦")
    void declaredEntryCountMismatchIsRejected() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.txt", "a".getBytes(StandardCharsets.UTF_8));
        entries.put("b.txt", "b".getBytes(StandardCharsets.UTF_8));
        byte[] body = zip(entries);
        patchEocdEntryCount(body, 1); // 声明 1 个，实际流里有 2 个

        AigArchiveScanResult result = scanner().scan(body);

        assertFalse(result.isPass(), "声明与内容对不上必须拦：" + result.getDetail());
        assertTrue(result.getDetail().contains("对不上"), result.getDetail());
    }

}
