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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 包体（压缩包）安全检查器：查"这个包里装了什么"。
 *
 * <p><b>它补的是 F-02 的那个洞</b>：在此之前平台只校验<b>声明</b>（Manifest 的五类拒绝规则）
 * 与包体哈希，<b>包体内容从头到尾没有人看</b>——一个 Manifest 写得干干净净、
 * 包里塞着 {@code install.sh} 或一个 ELF 可执行文件的包会被照单收下。
 * 而"包体里是什么"正是 {@code EXECUTABLE} Skill 与 V2 导入能不能开放的前提。</p>
 *
 * <h3>检查项与拒绝规则的对应（刻意不发明新规则码）</h3>
 * <table border="1">
 *     <caption>映射表</caption>
 *     <tr><th>发现</th><th>归到哪条规则</th><th>为什么</th></tr>
 *     <tr><td>路径穿越（{@code ..}）、绝对路径、Windows 盘符、反斜杠分隔符</td>
 *         <td>{@code EXECUTABLE_OR_PRIVILEGED_ACCESS}</td>
 *         <td>解压时会写到包根之外——这是"越界写入"，属 §6.2-1 的越权形态</td></tr>
 *     <tr><td>可执行/脚本扩展名、ELF/PE/Mach-O 魔数</td>
 *         <td>{@code UNBOUNDED_CODE_EXECUTION}</td>
 *         <td>平台无法界定其行为的可执行体（§6.2-3）</td></tr>
 *     <tr><td>条目数/单条体积/总解压量/压缩比超限（zip bomb 形态）</td>
 *         <td>{@code UNBOUNDED_CODE_EXECUTION}</td>
 *         <td>把 §6.2-3 用在"体量无法界定"这一形态上——<b>不是新规则</b>，
 *             而是在既有规则下的解释；之所以不新造一条码，是因为 §6.2 的五条是冻结词表</td></tr>
 *     <tr><td>正文出现私钥/凭据特征（{@code PRIVATE KEY}、{@code docker.sock}、{@code id_rsa}）</td>
 *         <td>{@code EXECUTABLE_OR_PRIVILEGED_ACCESS}</td>
 *         <td>§6.2-1 明列"生产密钥"与"Docker Socket"</td></tr>
 * </table>
 *
 * <h3>刻意不做的事（边界写清，免得被当成"已覆盖"）</h3>
 * <ul>
 *     <li><b>不解压到磁盘</b>：全部在内存流上判定，避免"为了检查先炸一次"；</li>
 *     <li><b>只嗅每个条目的前若干字节</b>（{@code sniffBytes}）：抓魔数与明显凭据特征。
 *         "全文扫描"是另一件事，这里不假装做了；</li>
 *     <li><b>不识别符号链接</b>：JDK 的 {@link ZipEntry} 不暴露 Unix 模式位，
 *         识别它需要自己解析中央目录。这是<b>已知缺口</b>，写在这里而不是留给人去猜；</li>
 *     <li><b>非 ZIP 体不判不拒</b>：本切片只覆盖 ZIP（{@code PK} 魔数）。其它形态
 *         返回"未扫描"，而不是"通过"——见 {@link AigArchiveScanResult#isScanned()}；</li>
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
     * 可执行/脚本扩展名（按 §6.2-3 的"无法界定的可执行体"处理）。
     */
    private static final Set<String> RISKY_EXTENSIONS = Set.of(
        "sh", "bash", "zsh", "fish", "ps1", "psm1", "bat", "cmd", "com",
        "exe", "dll", "so", "dylib", "jar", "class", "bin", "msi", "deb", "rpm", "apk");

    /**
     * 正文里的高危特征（§6.2-1 明列生产密钥与 Docker Socket）。
     */
    private static final List<String> RISKY_MARKERS = List.of(
        "-----begin rsa private key-----",
        "-----begin private key-----",
        "-----begin openssh private key-----",
        "-----begin ec private key-----",
        "docker.sock",
        "id_rsa",
        "id_ed25519");

    /**
     * 说明文本里逐条列出的最大发现数（避免说明被前 50 条同类发现占满）。
     */
    private static final int MAX_LISTED_FINDINGS = 8;

    private final AigPackageSecurityProperties properties;

    /**
     * 扫描一份包体。
     *
     * @param body 包体字节（可为 null）
     * @return 结论（非 ZIP 或开关关闭时 {@code scanned=false}、{@code pass=true}）
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
            result.setScanned(false);
            result.setDetail("包体不是 ZIP 形态（无 PK 魔数），本切片未做内容检查");
            return result;
        }

        Set<AigPackageRejectRuleEnum> hits = new LinkedHashSet<>();
        List<String> findings = new ArrayList<>();
        long totalBytes = 0L;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > properties.getMaxEntries()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "条目数超过上限 " + properties.getMaxEntries() + "（zip bomb 形态）");
                    break;
                }
                String name = StringUtils.blankToDefault(entry.getName(), "");
                checkEntryName(name, hits, findings);
                checkExtension(name, hits, findings);

                // 逐条读出来（带上限）：流式 ZIP 的 getSize()/getCompressedSize() 常常是 -1，
                // 只信"声明的体积"等于没有体积防线——真正能拦住 zip bomb 的是"读到多少算多少"。
                // 读的上限 = 单条上限 + 1，超了就说明超限，且不多读一个字节。
                long read = drainEntry(zip, name, hits, findings);
                if (read > properties.getMaxEntryBytes()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "条目 " + name + " 解压后超过单条上限 " + properties.getMaxEntryBytes()
                            + " 字节（zip bomb 形态）");
                    break;
                }
                totalBytes += read;
                if (totalBytes > properties.getMaxTotalBytes()) {
                    addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                        "解压后总量超过上限 " + properties.getMaxTotalBytes() + " 字节（zip bomb 形态）");
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

        result.setScanned(true);
        result.setHitRules(new ArrayList<>(hits));
        result.setPass(hits.isEmpty());
        String scope = "扫描 " + entries + " 个条目（前 " + properties.getSniffBytes() + " 字节/条做特征嗅探，"
            + "不解压落盘）";
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
     * 读干一个条目（带上限），顺带完成正文嗅探。
     *
     * <p><b>为什么"读"而不是"信声明的体积"</b>：流式 ZIP 的 {@link ZipEntry#getSize()} 与
     * {@link ZipEntry#getCompressedSize()} 经常是 -1（只有中央目录里才有真值），
     * 只信声明值等于没有体积防线。这里最多读 {@code maxEntryBytes + 1} 字节：
     * 超限即判定，且不多读一个字节。</p>
     *
     * @param zip      条目流
     * @param name     条目名
     * @param hits     命中集合
     * @param findings 发现明细
     * @return 实际读到的字节数
     */
    private long drainEntry(InputStream zip, String name, Set<AigPackageRejectRuleEnum> hits,
                            List<String> findings) {
        long limit = properties.getMaxEntryBytes() + 1;
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        long total = 0L;
        try {
            int n;
            while (total < limit && (n = zip.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) > 0) {
                if (head.size() < properties.getSniffBytes()) {
                    head.write(buffer, 0, (int) Math.min(n, properties.getSniffBytes() - head.size()));
                }
                total += n;
            }
        } catch (Exception e) {
            addHit(hits, findings, AigPackageRejectRuleEnum.UNBOUNDED_CODE_EXECUTION,
                "条目 " + name + " 无法读取（" + e.getClass().getSimpleName() + "），内容不可界定");
            return total;
        }
        sniff(head.toByteArray(), name, hits, findings);
        return total;
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
