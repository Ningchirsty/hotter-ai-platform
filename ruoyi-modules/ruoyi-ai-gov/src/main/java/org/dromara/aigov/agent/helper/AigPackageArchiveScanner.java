package org.dromara.aigov.agent.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.config.AigPackageSecurityProperties;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 包体（压缩包）安全检查器：查"这个包里装了什么"。
 *
 * <p><b>它补的是 F-02 的那个洞</b>：在此之前平台只校验<b>声明</b>（Manifest 的五类拒绝规则）
 * 与包体哈希，<b>包体内容从头到尾没有人看</b>——一个 Manifest 写得干干净净、
 * 包里塞着 {@code install.sh} 或一个 ELF 可执行文件的包会被照单收下。
 * 而"包体里是什么"正是 {@code EXECUTABLE} Skill 与 V2 导入能不能开放的前提，
 * 也就是"外部做好的 agent/skill 能不能接进来"这件事的准入口。</p>
 *
 * <h3>检查项与拒绝规则的对应（刻意不发明新规则码）</h3>
 * <table border="1">
 *     <caption>映射表</caption>
 *     <tr><th>发现</th><th>归到哪条规则</th><th>为什么</th></tr>
 *     <tr><td>路径穿越（{@code ..}）、绝对路径、Windows 盘符、反斜杠分隔符</td>
 *         <td>{@code EXECUTABLE_OR_PRIVILEGED_ACCESS}</td>
 *         <td>解压时会写到包根之外——这是"越界写入"，属 §6.2-1 的越权形态</td></tr>
 *     <tr><td><b>符号链接 / 特殊文件</b>（FIFO/设备/套接字，见中央目录的 Unix 模式位）</td>
 *         <td>{@code EXECUTABLE_OR_PRIVILEGED_ACCESS}</td>
 *         <td>经典 zip-slip 变体：先落一个指向包外的链接，再由后续条目<b>穿过它</b>写入。
 *             声明式包没有任何正当理由需要符号链接</td></tr>
 *     <tr><td>可执行/脚本扩展名、ELF/PE/Mach-O 魔数</td>
 *         <td>{@code UNBOUNDED_CODE_EXECUTION}</td>
 *         <td>平台无法界定其行为的可执行体（§6.2-3）</td></tr>
 *     <tr><td>条目数/单条体积/总解压量/压缩比超限（zip bomb 形态）</td>
 *         <td>{@code UNBOUNDED_CODE_EXECUTION}</td>
 *         <td>把 §6.2-3 用在"体量无法界定"这一形态上——<b>不是新规则</b>，
 *             而是在既有规则下的解释；之所以不新造一条码，是因为 §6.2 的五条是冻结词表</td></tr>
 *     <tr><td>中央目录声明条目数与实际可读条目数不一致</td>
 *         <td>{@code UNBOUNDED_CODE_EXECUTION}</td>
 *         <td>截断/伪造的包：声明与内容对不上时，"我扫过的"不等于"包里的"</td></tr>
 *     <tr><td>正文出现私钥/凭据特征（各型 {@code PRIVATE KEY}、{@code aws_secret_access_key}、
 *         {@code AKIA…}、{@code _authToken}、{@code AccountKey=}、{@code docker.sock}、{@code id_rsa}）</td>
 *         <td>{@code EXECUTABLE_OR_PRIVILEGED_ACCESS}</td>
 *         <td>§6.2-1 明列"生产密钥"与"Docker Socket"。第二切片把扫描范围从"头部 4096 字节"
 *             扩到 {@code maxTextScanBytes}（默认 8MB/条），因为"把私钥放在第 5KB 之后"是
 *             最容易的绕过方式</td></tr>
 * </table>
 *
 * <h3>刻意不做的事（边界写清，免得被当成"已覆盖"）</h3>
 * <ul>
 *     <li><b>不解压到磁盘</b>：全部在内存流上判定，避免"为了检查先炸一次"；</li>
 *     <li><b>每次扫描有上限</b>：单条文本扫描上限 {@code maxTextScanBytes}，超出部分不扫，
 *         并且结论里会写明"扫到 N 字节为止"——<b>不把"扫了一部分"说成"全文扫过了"</b>；</li>
 *     <li><b>不识别被编码/拆分的凭据</b>：base64 过的私钥、把 {@code AKIA} 拆成两段拼接的写法
 *         都躲得过特征匹配。上界是"明显特征"，不是"找到所有秘密"；</li>
 *     <li><b>非 ZIP 形态默认不判不拒</b>：第二切片能<b>认出</b>形态（gzip/bzip2/xz/7z/rar/tar/
 *         PDF/图片/文本/未知二进制），默认仍返回"未扫描"并写明是哪种形态；
 *         要不要拒绝由 {@code aigov.package.security.reject-non-zip-archives} 决定
 *         （**默认 false = 维持第一切片行为**，因为那会改变上传口的准入策略）；</li>
 *     <li><b>不执行、不沙箱运行</b>：扫的是"装了什么"，不是"跑起来会做什么"。
 *         真要执行外部代码，前提是沙箱运行时（P1-003），本类不是它；</li>
 *     <li><b>不做内容合规判断</b>（图形/文档内容是否合规）：那是质检与人工审核的事。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigPackageArchiveScanner {

    /**
     * ZIP 本地文件头魔数（{@code PK\x03\x04}）。
     */
    private static final byte[] ZIP_LOCAL_HEADER = {0x50, 0x4B, 0x03, 0x04};

    /**
     * ZIP 空档案魔数（{@code PK\x05\x06}）。
     */
    private static final byte[] ZIP_EMPTY_HEADER = {0x50, 0x4B, 0x05, 0x06};

    /**
     * ZIP 中央目录条目签名（{@code PK\x01\x02}）。
     */
    private static final byte[] ZIP_CENTRAL_HEADER = {0x50, 0x4B, 0x01, 0x02};

    /**
     * ZIP 中央目录结束记录签名（{@code PK\x05\x06}）——与空档案头同值，用法不同。
     */
    private static final byte[] ZIP_EOCD = {0x50, 0x4B, 0x05, 0x06};

    /**
     * EOCD 结构长度（不含可变长的注释）。
     */
    private static final int EOCD_MIN_LENGTH = 22;

    /**
     * ZIP 注释的最大长度（EOCD 之前最多这么多字节里找 EOCD）。
     */
    private static final int MAX_ZIP_COMMENT = 0xFFFF;

    /**
     * 中央目录条目的固定部分长度（到文件名之前）。
     */
    private static final int CENTRAL_HEADER_FIXED = 46;

    /**
     * Unix 文件类型掩码与各类型值（取自 mode 的高 4 位）。
     */
    private static final int S_IFMT = 0xF000;

    /**
     * 符号链接
     */
    private static final int S_IFLNK = 0xA000;

    /**
     * 普通文件（不作为发现）
     */
    private static final int S_IFREG = 0x8000;

    /**
     * 目录（不作为发现）
     */
    private static final int S_IFDIR = 0x4000;

    /**
     * 可执行/脚本扩展名（按 §6.2-3 的"无法界定的可执行体"处理）。
     */
    private static final Set<String> RISKY_EXTENSIONS = Set.of(
        "sh", "bash", "zsh", "fish", "ps1", "psm1", "bat", "cmd", "com",
        "exe", "dll", "so", "dylib", "jar", "class", "bin", "msi", "deb", "rpm", "apk");

    /**
     * 正文里的高危特征（§6.2-1 明列生产密钥与 Docker Socket）。
     *
     * <p>第二切片扩充，但仍然只收<b>高精度</b>特征：命中即整笔拒绝，误报的代价是挡掉合法包，
     * 所以刻意<b>不</b>收 {@code password=}、{@code secret=} 这类会出现在普通文档里的词。
     * 全部小写，比对前把窗口统一转小写。</p>
     */
    private static final List<String> RISKY_MARKERS = List.of(
        "-----begin rsa private key-----",
        "-----begin private key-----",
        "-----begin openssh private key-----",
        "-----begin ec private key-----",
        "-----begin dsa private key-----",
        "-----begin pgp private key-----",
        "docker.sock",
        "id_rsa",
        "id_ed25519",
        "id_ecdsa",
        "aws_secret_access_key",
        "aws_access_key_id",
        "private_key_id",
        "_authtoken",
        "accountkey=");

    /**
     * AWS 访问密钥 ID 形态（{@code AKIA} + 16 位）。用正则而不是字面量，因为后 16 位是随机的。
     */
    private static final Pattern AWS_KEY_ID = Pattern.compile("\\bakia[0-9a-z]{16}\\b");

    /**
     * 特征串窗口的长度（最长 marker - 1，用于跨 chunk 匹配）。
     */
    private static final int MARKER_WINDOW = RISKY_MARKERS.stream()
        .mapToInt(String::length).max().orElse(0) + "akia0123456789abcdef".length();

    /**
     * 说明文本里逐条列出的最大发现数（避免说明被前 50 条同类发现占满）。
     */
    private static final int MAX_LISTED_FINDINGS = 8;

    private final AigPackageSecurityProperties properties;

    /**
     * 扫描一份包体。
     *
     * @param body 包体字节（可为 null）
     * @return 结论（非 ZIP 且未开启拒绝时 {@code scanned=false}、{@code pass=true}）
     */
    public AigArchiveScanResult scan(byte[] body) {
        AigArchiveScanResult result = new AigArchiveScanResult();
        result.setPass(true);
        if (body == null || body.length == 0) {
            result.setScanned(false);
            result.setDetail("包体为空，未做内容检查");
            return result;
        }
        if (!properties.isEnabled()) {
            // 关闭时**必须留痕**：否则"安全检查没跑"与"跑了且通过"在日志里长得一样
            log.warn("包体安全检查已关闭（aigov.package.security.enabled=false），本次包体只校验声明与哈希");
            result.setScanned(false);
            result.setDetail("包体安全检查被开关关闭，本包体只校验了声明与哈希");
            return result;
        }
        if (!isZip(body)) {
            String form = detectContainerForm(body);
            if (properties.isRejectNonZipArchives() && isArchiveForm(form)) {
                result.setScanned(true);
                result.setHitRules(List.of(AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION));
                result.setPass(false);
                result.setDetail("包体是 " + form + " 形态，而本平台只检查 ZIP："
                    + "reject-non-zip-archives=true，因此拒绝（内容无法界定）");
                log.warn("包体因非 ZIP 形态被拒：form={}, bytes={}", form, body.length);
                return result;
            }
            result.setScanned(false);
            result.setDetail("包体不是 ZIP 形态（识别为：" + form + "），本切片未做内容检查");
            if (isArchiveForm(form)) {
                // 认出来是"另一种压缩包却没人看"——这是要让人看见的事实，而不是静默通过
                log.warn("包体是 {} 形态，当前配置未检查其内容（reject-non-zip-archives=false）", form);
            }
            return result;
        }

        Set<AigPackageRejectRuleEnum> hits = new LinkedHashSet<>();
        List<String> findings = new ArrayList<>();
        long totalBytes = 0L;
        int entries = 0;
        boolean stoppedByCap = false;
        long textScannedMax = 0L;
        boolean textScanTruncated = false;

        // 中央目录元数据（JDK 的 ZipEntry 不暴露 Unix 模式位，符号链接只能从这里看出来）
        List<CentralEntry> central = readCentralDirectory(body);
        checkCentralEntries(central, hits, findings);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > properties.getMaxEntries()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "条目数超过上限 " + properties.getMaxEntries() + "（zip bomb 形态）");
                    stoppedByCap = true;
                    break;
                }
                String name = StringUtils.blankToDefault(entry.getName(), "");
                checkEntryName(name, hits, findings);
                checkExtension(name, hits, findings);

                // 逐条读出来（带上限）：流式 ZIP 的 getSize()/getCompressedSize() 常常是 -1，
                // 只信"声明的体积"等于没有体积防线——真正能拦住 zip bomb 的是"读到多少算多少"。
                // 读的上限 = 单条上限 + 1，超了就说明超限，且不多读一个字节。
                DrainResult drained = drainEntry(zip, name, hits, findings);
                long read = drained.read();
                textScannedMax = Math.max(textScannedMax, drained.textScanned());
                textScanTruncated |= drained.textTruncated();
                if (read > properties.getMaxEntryBytes()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "条目 " + name + " 解压后超过单条上限 " + properties.getMaxEntryBytes()
                            + " 字节（zip bomb 形态）");
                    stoppedByCap = true;
                    break;
                }
                totalBytes += read;
                if (totalBytes > properties.getMaxTotalBytes()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "解压后总量超过上限 " + properties.getMaxTotalBytes() + " 字节（zip bomb 形态）");
                    stoppedByCap = true;
                    break;
                }
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && read / Math.max(1L, compressed) > properties.getMaxCompressionRatio()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "条目 " + name + " 压缩比 " + (read / compressed) + ":1，超过上限 "
                            + properties.getMaxCompressionRatio() + ":1（zip bomb 形态）");
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            // 压缩包损坏/读不动：这本身就是"内容不可界定"，不能当成通过
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "压缩包无法读取（" + e.getClass().getSimpleName() + "），内容不可界定");
        }
        if (entries == 0) {
            // 空档案（或声明是 ZIP 却读不出任何条目）：内容无从界定，不能算通过。
            // 这一条是测试逼出来的：截断的 ZIP 头会让 getNextEntry() 直接返回 null，
            // 若不显式判空，一个坏包会"扫描 0 个条目"地通过
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "压缩包里没有任何可读条目（空档案或已损坏），内容不可界定");
        }
        if (!stoppedByCap && !central.isEmpty() && central.size() != entries) {
            // 只有"扫完了"才比对这个数：被上限提前终止时，对不上是预期的
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "中央目录声明 " + central.size() + " 个条目，实际读出 " + entries
                    + " 个：声明与内容对不上（截断或被改过），所以「扫过的」不等于「包里的」");
        }

        result.setScanned(true);
        result.setHitRules(new ArrayList<>(hits));
        result.setPass(hits.isEmpty());
        String scope = "扫描 " + entries + " 个条目（前 " + properties.getSniffBytes() + " 字节/条做魔数嗅探，"
            + "前 " + properties.getMaxTextScanBytes() + " 字节/条做凭据特征扫描"
            + (textScanTruncated ? "，有条目超过该上限故只扫到上限" : "")
            + "；已解析中央目录元数据 " + central.size() + " 条；不解压落盘）";
        result.setDetail(hits.isEmpty()
            ? "包体安全检查通过：" + scope
            : "包体安全检查未通过，命中 " + result.hitRuleCodes() + "：" + joinFindings(findings)
                + "；" + scope);
        return result;
    }

    /**
     * 判断是否为 ZIP 形态（本地文件头或空档案头）。
     *
     * @param body 包体
     * @return 是则 true
     */
    private boolean isZip(byte[] body) {
        return startsWith(body, ZIP_LOCAL_HEADER) || startsWith(body, ZIP_EMPTY_HEADER);
    }

    /**
     * 按魔数识别包体形态（非 ZIP 时用）。
     *
     * <p>第二切片新增：第一切片只能说"不是 ZIP"，现在能说清"是什么"。
     * 对运维排查与策略收紧都有用——"一个 .tar.gz 一直没人看"与"一堆未知二进制"不是同一件事。</p>
     *
     * @param body 包体
     * @return 可读形态名
     */
    private String detectContainerForm(byte[] body) {
        if (startsWith(body, new byte[]{0x1F, (byte) 0x8B})) {
            return "gzip";
        }
        if (startsWith(body, new byte[]{'B', 'Z', 'h'})) {
            return "bzip2";
        }
        if (startsWith(body, new byte[]{(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00})) {
            return "xz";
        }
        if (startsWith(body, new byte[]{'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C})) {
            return "7z";
        }
        if (startsWith(body, new byte[]{'R', 'a', 'r', '!', 0x1A, 0x07})) {
            return "rar";
        }
        if (startsWith(body, new byte[]{'M', 'S', 'C', 'F'})) {
            return "cab";
        }
        // tar：偏移 257 处的 "ustar"
        if (body.length > 262 && body[257] == 'u' && body[258] == 's' && body[259] == 't'
            && body[260] == 'a' && body[261] == 'r') {
            return "tar";
        }
        if (startsWith(body, new byte[]{'%', 'P', 'D', 'F'})) {
            return "pdf";
        }
        if (startsWith(body, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
            return "png";
        }
        if (startsWith(body, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "jpeg";
        }
        return looksLikeText(body) ? "纯文本" : "未知二进制";
    }

    /**
     * 形态名是否属于"压缩包"（用于决定 reject-non-zip-archives 是否适用）。
     *
     * @param form 形态名
     * @return 是压缩包返回 true
     */
    private boolean isArchiveForm(String form) {
        return switch (form) {
            case "gzip", "bzip2", "xz", "7z", "rar", "cab", "tar" -> true;
            default -> false;
        };
    }

    /**
     * 是否像文本（用于把"未知二进制"与"纯文本"分开）。
     *
     * @param body 包体
     * @return 前 512 字节里没有控制字符（除常见空白）则视为文本
     */
    private boolean looksLikeText(byte[] body) {
        int limit = Math.min(body.length, 512);
        for (int i = 0; i < limit; i++) {
            int b = body[i] & 0xFF;
            boolean printable = b == 0x09 || b == 0x0A || b == 0x0D || (b >= 0x20 && b != 0x7F);
            if (!printable) {
                return false;
            }
        }
        return true;
    }

    /**
     * 字节前缀匹配。
     *
     * @param body    包体
     * @param pattern 前缀
     * @return 匹配返回 true
     */
    private boolean startsWith(byte[] body, byte[] pattern) {
        if (body.length < pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (body[i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 中央目录（符号链接 / 特殊文件 / 条目数一致性）
    // ------------------------------------------------------------------

    /**
     * 中央目录里一个条目的元数据。
     *
     * @param name        条目名（仅用于报错回显）
     * @param unixMode    Unix 模式位（host 为 Unix 时有效）
     * @param hasUnixMode 是否拿到了可信的 Unix 模式位
     */
    private record CentralEntry(String name, int unixMode, boolean hasUnixMode) {
    }

    /**
     * 解析 ZIP 中央目录，取出每个条目的 Unix 模式位。
     *
     * <p><b>为什么必须自己解析</b>：JDK 的 {@link ZipEntry} 不暴露 Unix 模式位，
     * 因此"这个条目是不是符号链接"在流式接口里根本问不出来。而符号链接正是 zip-slip 的经典变体：
     * 先落一个指向包外的链接，再由后续条目穿过它写入。第一切片把这列为已知缺口。</p>
     *
     * <p>解析路径：从尾部找 EOCD（{@code PK\x05\x06}）拿中央目录偏移与条目数，再逐条走
     * 中央目录头（{@code PK\x01\x02}）。<b>不用"全文搜签名"</b>——那会把文件正文里恰好出现
     * 同样字节的内容当成条目。EOCD 读不到就返回空表（不去猜），此时符号链接检查<b>没做</b>，
     * 这一点由返回空表如实体现，不假装做过。</p>
     *
     * @param body 包体
     * @return 条目元数据；无法解析时返回空表
     */
    private List<CentralEntry> readCentralDirectory(byte[] body) {
        List<CentralEntry> entries = new ArrayList<>();
        int eocd = findEocd(body);
        if (eocd < 0) {
            log.debug("未找到 ZIP 中央目录结束记录，跳过符号链接/特殊文件检查（不做猜测）");
            return entries;
        }
        long count = readU16(body, eocd + 10);
        long cdOffset = readU32(body, eocd + 16);
        long cdSize = readU32(body, eocd + 12);
        if (count <= 0 || cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > body.length) {
            log.debug("中央目录指针越界（count={}, offset={}, size={}, len={}）",
                count, cdOffset, cdSize, body.length);
            return entries;
        }
        int cursor = (int) cdOffset;
        long max = Math.min(count, properties.getMaxEntries() + 1L);
        for (long i = 0; i < max; i++) {
            if (cursor + CENTRAL_HEADER_FIXED > body.length
                || !startsWithAt(body, cursor, ZIP_CENTRAL_HEADER)) {
                break;
            }
            int versionMadeBy = (int) readU16(body, cursor + 4);
            int hostSystem = (versionMadeBy >> 8) & 0xFF;
            long externalAttrs = readU32(body, cursor + 38);
            int nameLen = (int) readU16(body, cursor + 28);
            int extraLen = (int) readU16(body, cursor + 30);
            int commentLen = (int) readU16(body, cursor + 32);
            int nameAt = cursor + CENTRAL_HEADER_FIXED;
            String name = nameAt + nameLen <= body.length
                ? new String(body, nameAt, nameLen, StandardCharsets.UTF_8) : "";
            int unixMode = (int) ((externalAttrs >>> 16) & 0xFFFF);
            // host 3 = Unix：只有它写下的高 16 位才是 Unix 模式位，别的宿主平台那 16 位是别的东西
            entries.add(new CentralEntry(name, unixMode, hostSystem == 3));
            cursor = nameAt + nameLen + extraLen + commentLen;
        }
        return entries;
    }

    /**
     * 从尾部往前找 EOCD 签名。
     *
     * @param body 包体
     * @return EOCD 起始偏移；找不到返回 -1
     */
    private int findEocd(byte[] body) {
        if (body.length < EOCD_MIN_LENGTH) {
            return -1;
        }
        int lowest = Math.max(0, body.length - EOCD_MIN_LENGTH - MAX_ZIP_COMMENT);
        for (int i = body.length - EOCD_MIN_LENGTH; i >= lowest; i--) {
            if (startsWithAt(body, i, ZIP_EOCD)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 指定偏移处的字节前缀匹配。
     *
     * @param body    包体
     * @param offset  偏移
     * @param pattern 前缀
     * @return 匹配返回 true
     */
    private boolean startsWithAt(byte[] body, int offset, byte[] pattern) {
        if (offset < 0 || offset + pattern.length > body.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (body[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 小端 16 位读取（越界返回 0）。
     *
     * @param body   包体
     * @param offset 偏移
     * @return 值
     */
    private long readU16(byte[] body, int offset) {
        if (offset < 0 || offset + 2 > body.length) {
            return 0L;
        }
        return (body[offset] & 0xFFL) | ((body[offset + 1] & 0xFFL) << 8);
    }

    /**
     * 小端 32 位读取（越界返回 0）。
     *
     * @param body   包体
     * @param offset 偏移
     * @return 值
     */
    private long readU32(byte[] body, int offset) {
        if (offset < 0 || offset + 4 > body.length) {
            return 0L;
        }
        return (body[offset] & 0xFFL) | ((body[offset + 1] & 0xFFL) << 8)
            | ((body[offset + 2] & 0xFFL) << 16) | ((body[offset + 3] & 0xFFL) << 24);
    }

    /**
     * 检查中央目录元数据：符号链接与特殊文件。
     *
     * @param central  条目元数据
     * @param hits     命中集合
     * @param findings 发现明细
     */
    private void checkCentralEntries(List<CentralEntry> central, Set<AigPackageRejectRuleEnum> hits,
                                     List<String> findings) {
        for (CentralEntry entry : central) {
            if (!entry.hasUnixMode()) {
                continue;
            }
            int type = entry.unixMode() & S_IFMT;
            if (type == S_IFLNK) {
                addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                    "条目 " + entry.name() + " 是符号链接（Unix 模式位 " + Integer.toOctalString(entry.unixMode())
                        + "）：解压后可以指向包外，后续条目能穿过它写入");
            } else if (type != S_IFREG && type != S_IFDIR && type != 0) {
                addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                    "条目 " + entry.name() + " 是特殊文件（模式位 " + Integer.toOctalString(entry.unixMode())
                        + "，非普通文件/目录）：FIFO、设备或套接字都不该出现在声明式包里");
            }
        }
    }

    /**
     * 检查条目名（路径穿越与绝对路径）。
     *
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     */
    private void checkEntryName(String name, Set<AigPackageRejectRuleEnum> hits, List<String> findings) {
        if (name.isBlank()) {
            addHit(hits, findings, AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE, "存在无名条目");
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.startsWith("/") || lower.matches("^[a-z]:[\\\\/].*")) {
            addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                "条目 " + name + " 是绝对路径：解压会写到包根之外");
        }
        if (name.contains("\\")) {
            addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                "条目 " + name + " 用反斜杠分隔：跨平台解压位置不可预期");
        }
        for (String segment : name.split("/")) {
            if ("..".equals(segment)) {
                addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                    "条目 " + name + " 含上跳路径段（..）：解压会写到包根之外");
                break;
            }
        }
    }

    /**
     * 检查扩展名是否属于可执行/脚本类。
     *
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     */
    private void checkExtension(String name, Set<AigPackageRejectRuleEnum> hits, List<String> findings) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (RISKY_EXTENSIONS.contains(ext)) {
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "条目 " + name + " 是可执行/脚本类（." + ext + "）：平台无法界定它的行为");
        }
    }

    /**
     * 读干一个条目的结果。
     *
     * @param read          实际读到的字节数
     * @param textScanned   其中做过凭据特征扫描的字节数
     * @param textTruncated 是否因为上限而没扫完
     */
    private record DrainResult(long read, long textScanned, boolean textTruncated) {
    }

    /**
     * 读干一个条目（带上限），顺带完成"魔数嗅探 + 凭据特征全文扫描"。
     *
     * <p><b>为什么"读"而不是"信声明的体积"</b>：流式 ZIP 的 {@link ZipEntry#getSize()} 与
     * {@link ZipEntry#getCompressedSize()} 经常是 -1（只有中央目录里才有真值），
     * 只信声明值等于没有体积防线。这里最多读 {@code maxEntryBytes + 1} 字节：
     * 超限即判定，且不多读一个字节。</p>
     *
     * <p><b>凭据扫描为什么要跨 chunk</b>：特征是固定串（如 {@code aws_secret_access_key}），
     * 用滑动窗口保留"上一个 chunk 的尾巴"才能匹配跨边界的串——否则把特征正好放在 8KB 边界上
     * 就能绕过。窗口长度 = 最长特征串长度。</p>
     *
     * @param zip      条目流
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     * @return 读取与扫描结果
     */
    private DrainResult drainEntry(InputStream zip, String name, Set<AigPackageRejectRuleEnum> hits,
                                  List<String> findings) {
        long limit = properties.getMaxEntryBytes() + 1;
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        byte[] carry = new byte[0];
        long total = 0L;
        long textScanned = 0L;
        boolean textTruncated = false;
        boolean markerFound = false;
        try {
            int n;
            while (total < limit && (n = zip.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) > 0) {
                if (head.size() < properties.getSniffBytes()) {
                    head.write(buffer, 0, (int) Math.min(n, properties.getSniffBytes() - head.size()));
                }
                if (!markerFound) {
                    if (textScanned >= properties.getMaxTextScanBytes()) {
                        textTruncated = true;
                    } else {
                        int allowed = (int) Math.min(n, properties.getMaxTextScanBytes() - textScanned);
                        if (allowed < n) {
                            textTruncated = true;
                        }
                        TextChunk chunk = scanTextChunk(carry, buffer, allowed, name, hits, findings);
                        carry = chunk.carry();
                        markerFound = chunk.found();
                        textScanned += allowed;
                    }
                }
                total += n;
            }
        } catch (Exception e) {
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "条目 " + name + " 无法读取（" + e.getClass().getSimpleName() + "），内容不可界定");
            return new DrainResult(total, textScanned, textTruncated);
        }
        sniff(head.toByteArray(), name, hits, findings);
        return new DrainResult(total, textScanned, textTruncated);
    }

    /**
     * 一个 chunk 的扫描结果。
     *
     * @param carry 留给下一个 chunk 的尾部窗口
     * @param found 本 chunk 是否命中了特征
     */
    private record TextChunk(byte[] carry, boolean found) {
    }

    /**
     * 在一个 chunk 上做凭据特征匹配（带跨 chunk 窗口）。
     *
     * @param carry    上一个 chunk 的尾部（用于跨边界匹配）
     * @param buffer   本 chunk
     * @param length   本 chunk 的有效长度
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     * @return 新的 carry 与命中标记
     */
    private TextChunk scanTextChunk(byte[] carry, byte[] buffer, int length, String name,
                                    Set<AigPackageRejectRuleEnum> hits, List<String> findings) {
        if (length <= 0) {
            return new TextChunk(carry, false);
        }
        byte[] window = new byte[carry.length + length];
        System.arraycopy(carry, 0, window, 0, carry.length);
        System.arraycopy(buffer, 0, window, carry.length, length);
        String text = new String(window, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        for (String marker : RISKY_MARKERS) {
            if (text.contains(marker)) {
                addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                    "条目 " + name + " 正文出现高危特征「" + marker + "」");
                return new TextChunk(new byte[0], true);
            }
        }
        if (AWS_KEY_ID.matcher(text).find()) {
            addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                "条目 " + name + " 正文出现疑似 AWS 访问密钥 ID（AKIA…）");
            return new TextChunk(new byte[0], true);
        }
        int keep = Math.min(MARKER_WINDOW, window.length);
        byte[] next = new byte[keep];
        System.arraycopy(window, window.length - keep, next, 0, keep);
        return new TextChunk(next, false);
    }

    /**
     * 嗅探条目正文的头部字节（魔数 + 高危特征）。
     *
     * @param head     已读到的头部字节
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     */
    private void sniff(byte[] head, String name, Set<AigPackageRejectRuleEnum> hits,
                       List<String> findings) {
        if (head.length >= 4) {
            String magic = detectBinaryMagic(head);
            if (magic != null) {
                addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                    "条目 " + name + " 的正文是" + magic + " 可执行体（内容与扩展名无关）");
                return;
            }
        }
        String text = new String(head, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        for (String marker : RISKY_MARKERS) {
            if (text.contains(marker)) {
                addHit(hits, findings, AigPackageRejectRuleEnum.EXECUTABLE_OR_PRIVILEGED_ACCESS,
                    "条目 " + name + " 正文出现高危特征「" + marker + "」");
                return;
            }
        }
    }

    /**
     * 按魔数判断可执行体类型。
     *
     * @param head 头部字节
     * @return 类型描述；不是可执行体返回 null
     */
    private String detectBinaryMagic(byte[] head) {
        // ELF：0x7F 'E' 'L' 'F'
        if (head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F') {
            return "ELF";
        }
        // PE：'M' 'Z'
        if (head[0] == 'M' && head[1] == 'Z') {
            return "PE/Windows";
        }
        // Mach-O：0xFEEDFACE / 0xFEEDFACF / 0xCAFEBABE（Java class）
        if ((head[0] == (byte) 0xFE && head[1] == (byte) 0xED)
            || (head[0] == (byte) 0xCA && head[1] == (byte) 0xFE && head[2] == (byte) 0xBA
                && head[3] == (byte) 0xBE)) {
            return "Mach-O/Java class";
        }
        return null;
    }

    /**
     * 记一条发现（规则去重，明细有个数上限）。
     *
     * @param hits     命中集合
     * @param findings 明细
     * @param rule     规则
     * @param text     说明
     */
    private void addHit(Set<AigPackageRejectRuleEnum> hits, List<String> findings,
                        AigPackageRejectRuleEnum rule, String text) {
        hits.add(rule);
        if (findings.size() < MAX_LISTED_FINDINGS) {
            findings.add(text);
        }
    }

    /**
     * 拼接发现明细（超出列出上限时说明还有多少条）。
     *
     * @param findings 明细
     * @return 文本
     */
    private String joinFindings(List<String> findings) {
        String text = String.join("；", findings);
        return text.isBlank() ? "（明细为空）" : text;
    }

}
