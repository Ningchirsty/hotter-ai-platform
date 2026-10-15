package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 内置 DOCX 解析引擎（{@value #ENGINE_ID}，设计 §6.4；附件「优先内部可部署」）。
 *
 * <p><b>为什么 DOCX 不需要外部引擎</b>：{@code .docx} 就是一个 zip 包里的
 * {@code word/document.xml}。用 JDK 的 {@code java.util.zip} 把它取出来、按 {@code <w:p>}
 * 切段、拼 {@code <w:t>} 里的文本，就能拿到段落级文本与段落定位——这正是首期需要的东西。</p>
 *
 * <h3>三个刻意的取舍</h3>
 * <ol>
 *     <li><b>只读正文（{@code document.xml}）</b>：页眉/页脚/批注/文本框不进入正文。它们往往是
 *         "合同编号""第 X 页 共 Y 页"这类噪声，混进正文只会污染检索；</li>
 *     <li><b>段落就是定位单位</b>：{@code 第 N 段}（N 只数非空段）比"字符偏移"更适合人核对——
 *         维护人打开 Word 就能数段落；</li>
 *     <li><b>解压有上限</b>：zip 可以被构造成"解出来几个 G"，这里对解出的 XML 设硬上限并显式失败。
 *         没有这个上限时，一份几十 KB 的恶意文件就能把处理进程拖死。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Component
public class AigDocxParseEngine implements KnowledgeParsePort {

    /**
     * 引擎 ID。
     */
    public static final String ENGINE_ID = "builtin-docx-v1";

    /**
     * 正文 XML 大小上限（字节）：正常 DOCX 的 document.xml 远小于它。
     */
    static final int MAX_DOCUMENT_XML_BYTES = 64 * 1024 * 1024;

    private static final String DOCUMENT_ENTRY = "word/document.xml";

    private static final Pattern PARAGRAPH = Pattern.compile(
        "(?s)<w:p(?:\\s[^>]*)?/>|<w:p(?:\\s[^>]*)?>.*?</w:p>");

    /**
     * 段落内的文本元素，<b>按文档顺序</b>匹配：{@code <w:t>} 取内容，{@code <w:tab>}/{@code <w:br>}/{@code <w:cr>} 取制表/换行。
     *
     * <p>为什么必须"按顺序一次扫"：制表与换行元素通常<b>夹在两个 {@code <w:t>} 之间</b>
     * （{@code <w:t>a</w:t><w:tab/><w:t>b</w:t>}）。若先把 {@code <w:tab/>} 替换成一个制表符再单独抽
     * {@code <w:t>} 内容，那个制表符落在元素<b>外面</b>，会被直接丢掉——排版信息就"静默消失"了。</p>
     */
    private static final Pattern TEXT_TOKEN = Pattern.compile(
        "(?s)<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:(tab|br|cr)(?:\\s[^>]*)?/?>");

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public boolean supports(AigKnowledgeSourceTypeEnum sourceType) {
        return sourceType == AigKnowledgeSourceTypeEnum.DOCX;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        String xml = readDocumentXml(request.content(), request.fileName());
        List<ParsedBlock> blocks = paragraphs(xml);
        return new ParseResult(ENGINE_ID, "UTF-8 (OOXML)", blocks);
    }

    /**
     * 从 zip 里取出 {@code word/document.xml}。
     *
     * @param content  文件字节
     * @param fileName 文件名
     * @return 正文 XML
     */
    static String readDocumentXml(byte[] content, String fileName) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            boolean anyEntry = false;
            while ((entry = zip.getNextEntry()) != null) {
                anyEntry = true;
                if (!DOCUMENT_ENTRY.equals(entry.getName())) {
                    continue;
                }
                byte[] xml = readCapped(zip);
                if (xml == null) {
                    throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                        "DOCX 正文超过 " + MAX_DOCUMENT_XML_BYTES + " 字节上限，拒绝解析：" + fileName
                            + "（异常大的正文应先拆分，而不是直接推进管道）");
                }
                return new String(xml, StandardCharsets.UTF_8);
            }
            if (!anyEntry) {
                // ZipInputStream 对"根本不是 zip"的内容不抛错、只返回没有条目：必须自己判，
                // 否则这类文件会被报成"缺正文"，把"文件坏了"误导成"格式不对"
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "DOCX 不是可读的 zip 包（文件可能损坏，或实际是 .doc 旧格式）：" + fileName);
            }
        } catch (IOException e) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "DOCX 不是可读的 zip 包（文件可能损坏或实际是 .doc 旧格式）：" + fileName, e);
        }
        throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
            "DOCX 里找不到正文 " + DOCUMENT_ENTRY + "（不是标准 Word 文档？）：" + fileName);
    }

    /**
     * 读一个 zip 条目，超上限返回 {@code null}。
     *
     * @param in 输入流
     * @return 字节；超限返回 null
     * @throws IOException 读失败
     */
    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MAX_DOCUMENT_XML_BYTES) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * 按 {@code <w:p>} 切段，只保留有文本的段落。
     *
     * @param xml 正文 XML
     * @return 来源块
     */
    static List<ParsedBlock> paragraphs(String xml) {
        List<ParsedBlock> blocks = new ArrayList<>();
        Matcher matcher = PARAGRAPH.matcher(xml);
        int ordinal = 0;
        while (matcher.find()) {
            String text = paragraphText(matcher.group());
            if (text.isEmpty()) {
                continue;
            }
            ordinal++;
            blocks.add(new ParsedBlock(text, "第 " + ordinal + " 段"));
        }
        if (blocks.isEmpty()) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "DOCX 正文里没有取到任何文本（可能整篇都在图片/文本框里；纯图文档需 OCR，不在内置引擎范围内）");
        }
        return blocks;
    }

    /**
     * 取一个段落的文本：拼 {@code <w:t>}，把制表符与换行还原成真实字符。
     *
     * @param paragraphXml 段落 XML
     * @return 段落文本（可能为空=空段）
     */
    static String paragraphText(String paragraphXml) {
        Matcher tokens = TEXT_TOKEN.matcher(paragraphXml);
        StringBuilder sb = new StringBuilder();
        while (tokens.find()) {
            if (tokens.group(1) != null) {
                sb.append(unescapeXml(tokens.group(1)));
            } else if ("tab".equals(tokens.group(2))) {
                sb.append('\t');
            } else {
                sb.append('\n');
            }
        }
        if (sb.length() == 0) {
            return "";
        }
        String[] lines = sb.toString().split("\n", -1);
        StringBuilder normalized = new StringBuilder();
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append('\n');
            }
            normalized.append(stripped);
        }
        return normalized.toString();
    }

    /**
     * 反转义 XML 文本节点。
     *
     * <p>只扫一遍、遇到实体就地替换（不反复替换 {@code &amp;}），否则 {@code &amp;lt;} 会被解成
     * {@code <}——那是把"用户真的写了 &lt; 这四个字符"改成了别的意思。</p>
     *
     * @param raw 原始文本
     * @return 文本
     */
    static String unescapeXml(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            int semi = raw.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                sb.append(c);
                i++;
                continue;
            }
            String entity = raw.substring(i + 1, semi);
            String decoded = decodeEntity(entity);
            if (decoded == null) {
                sb.append(c);
                i++;
                continue;
            }
            sb.append(decoded);
            i = semi + 1;
        }
        return sb.toString();
    }

    /**
     * 解一个实体名（不含 {@code &} 与 {@code ;}）。
     *
     * @param entity 实体名
     * @return 字符；不认识返回 null
     */
    private static String decodeEntity(String entity) {
        return switch (entity) {
            case "lt" -> "<";
            case "gt" -> ">";
            case "amp" -> "&";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> decodeNumericEntity(entity);
        };
    }

    /**
     * 解数字实体 {@code &#65;} / {@code &#x41;}。
     *
     * @param entity 实体名
     * @return 字符；不是数字实体返回 null
     */
    private static String decodeNumericEntity(String entity) {
        try {
            int codePoint;
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                codePoint = Integer.parseInt(entity.substring(2), 16);
            } else if (entity.startsWith("#")) {
                codePoint = Integer.parseInt(entity.substring(1));
            } else {
                return null;
            }
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
