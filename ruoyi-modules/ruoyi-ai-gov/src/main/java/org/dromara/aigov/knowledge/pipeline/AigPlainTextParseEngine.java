package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置纯文本/Markdown 解析引擎（{@value #ENGINE_ID}，设计 §6.4；附件「优先内部可部署」）。
 *
 * <p><b>为什么必须是"内置、零依赖"的一档</b>：TXT/Markdown 是企业资料里最常见的两种"已经能读"的格式。
 * 如果连它们都要先选型一个外部引擎才能处理，那试点阶段就什么都跑不起来；而它们本来只需要 JDK。</p>
 *
 * <h3>三件容易做错的事，这里逐条处理</h3>
 * <ol>
 *     <li><b>编码不能猜成"乱码但能跑"</b>：先按 BOM 判定 UTF-8/UTF-16；没有 BOM 时<b>严格</b>试
 *         UTF-8，失败再严格试 GBK，都失败就报 {@code PARSE_ENCODING_UNKNOWN}。若直接把字节按
 *         UTF-8 硬解，中文资料会变成一屏乱码，而乱码还会被正常索引、正常检索——查都没法查。
 *         （注意：UTF-8 对任意 GBK 字节几乎都会失败，所以顺序不能反过来。）</li>
 *     <li><b>行号必须是"源文件里的行号"</b>：定位写 {@code 行 88} 才有人能按它翻回原文；
 *         写成"第 12 块"就只有程序看得懂。</li>
 *     <li><b>Markdown 要带标题路径</b>：Markdown 的价值就在层级；丢掉标题后，检索命中一段话
 *         却答不出"这是哪一节"。代码围栏里的 {@code #} 不是标题，这里也一并排除。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Component
public class AigPlainTextParseEngine implements KnowledgeParsePort {

    /**
     * 引擎 ID。
     */
    public static final String ENGINE_ID = "builtin-text-v1";

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s{0,3}(```+|~~~+).*$");

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public boolean supports(AigKnowledgeSourceTypeEnum sourceType) {
        return sourceType == AigKnowledgeSourceTypeEnum.TXT
            || sourceType == AigKnowledgeSourceTypeEnum.MARKDOWN;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        Decoded decoded = decode(request.content(), request.fileName());
        if (decoded.text().isBlank()) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "文件里没有可索引的文本（只有空白字符）：" + request.fileName());
        }
        List<ParsedBlock> blocks = request.sourceType() == AigKnowledgeSourceTypeEnum.MARKDOWN
            ? markdownBlocks(decoded.text()) : plainBlocks(decoded.text());
        return new ParseResult(ENGINE_ID, decoded.encoding(), blocks);
    }

    /**
     * 按 BOM / 严格解码判定文本编码。
     *
     * @param content  文件字节
     * @param fileName 文件名（错误文案用）
     * @return 文本与编码名
     */
    static Decoded decode(byte[] content, String fileName) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
            && (content[2] & 0xFF) == 0xBF) {
            return new Decoded(new String(content, 3, content.length - 3, StandardCharsets.UTF_8), "UTF-8");
        }
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(content, 2, content.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (content.length >= 2 && (content[0] & 0xFF) == 0xFE && (content[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(content, 2, content.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }
        String utf8 = strictDecode(content, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return new Decoded(utf8, "UTF-8");
        }
        if (Charset.isSupported("GBK")) {
            String gbk = strictDecode(content, Charset.forName("GBK"));
            if (gbk != null) {
                return new Decoded(gbk, "GBK");
            }
        }
        throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_ENCODING_UNKNOWN,
            "文本编码无法识别（既不是 UTF-8 也不是 GBK）：" + fileName
                + "。请把文件另存为 UTF-8 后重传——按错误编码硬解会把正文变成乱码，而乱码同样能被索引和检索，"
                + "线上只会表现为「检索得到、但内容看不懂」。");
    }

    /**
     * 严格解码：有任何非法字节就返回 {@code null}（不产生替换字符）。
     *
     * @param content 字节
     * @param charset 字符集
     * @return 文本；不合法返回 null
     */
    private static String strictDecode(byte[] content, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 纯文本：一行一个来源块（定位=源文件行号）。合并交给分段器做，这样"以行为最小定位单位"不会被丢。
     *
     * @param text 文本
     * @return 来源块
     */
    static List<ParsedBlock> plainBlocks(String text) {
        List<ParsedBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripTrailingCr(lines[i]).strip();
            if (line.isEmpty()) {
                continue;
            }
            blocks.add(new ParsedBlock(line, "行 " + (i + 1)));
        }
        if (blocks.isEmpty()) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "文件里没有非空行，没有可索引的文本（定位单位是行）");
        }
        return blocks;
    }

    /**
     * Markdown：一行一个来源块，定位=「标题路径 · 行 N」；围栏代码块内的 {@code #} 不当标题。
     *
     * @param text 文本
     * @return 来源块
     */
    static List<ParsedBlock> markdownBlocks(String text) {
        List<ParsedBlock> blocks = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = stripTrailingCr(lines[i]);
            if (CODE_FENCE.matcher(line).matches()) {
                inFence = !inFence;
            }
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!inFence) {
                Matcher heading = MARKDOWN_HEADING.matcher(line);
                if (heading.matches()) {
                    int level = heading.group(1).length();
                    String title = heading.group(2).strip();
                    while (headings.size() >= level) {
                        headings.remove(headings.size() - 1);
                    }
                    headings.add(title.isEmpty() ? "未命名标题" : title);
                }
            }
            blocks.add(new ParsedBlock(trimmed, locatorFor(headings, i + 1)));
        }
        if (blocks.isEmpty()) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "文件里没有非空行，没有可索引的文本（定位单位是行）");
        }
        return blocks;
    }

    /**
     * 拼定位：有标题路径时带上路径，方便"这段出自哪一节"直接可读。
     *
     * @param headings  当前标题路径
     * @param lineNumber 源文件行号（1 起）
     * @return 定位
     */
    static String locatorFor(List<String> headings, int lineNumber) {
        if (headings.isEmpty()) {
            return "行 " + lineNumber;
        }
        return String.join(" > ", headings) + " · 行 " + lineNumber;
    }

    private static String stripTrailingCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /**
     * 解码结果。
     *
     * @param text     文本
     * @param encoding 编码名（写入处理结果，回答"这份文件是怎么读出来的"）
     */
    record Decoded(String text, String encoding) {
    }

}
