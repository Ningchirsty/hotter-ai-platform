package org.dromara.aigov.knowledge.pipeline;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析引擎（{@value #ENGINE_ID}，设计 §6.4；附件「知识处理引擎：优先内部可部署」）。
 *
 * <h3>为什么选 PDFBox，而不是自己按字节抠文本</h3>
 * <p>PDF 的正文不一定以"可见字符"存在：现代 Word/浏览器导出的 PDF 普遍用 <b>Identity-H 子集字体</b>，
 * 正文是字形编号，必须查字体的 ToUnicode 映射才能还原。自己抠 {@code (…)Tj} 这类字面串，结果会是
 * "能跑、但有相当一部分 PDF 抽出乱码或抽不出"——而抽出的乱码照样能被索引、被检索，线上只表现为
 * "检索得到、内容看不懂"。这种错误没有任何信号，所以不值得为"少一个依赖"去承担。</p>
 *
 * <p>PDFBox 3 是 <b>Apache-2.0、纯 Java、离线可跑</b>，满足附件的"优先内部可部署"；它是 <b>库</b>
 * 而不是外部服务，部署形态不变（多一个 jar），也不引入任何外发通道。</p>
 *
 * <h3>刻意不做的部分（留给评测结论）</h3>
 * <ul>
 *     <li><b>不做 OCR</b>：纯图/扫描件取不到文本时<b>显式失败</b>并说明"需要 OCR 引擎"，而不是
 *         返回空文本往下走；OCR 属于量表之外的选项，由试点评测决定是否接入；</li>
 *     <li><b>不接收加密文件</b>：需要密码的 PDF 直接失败并说明原因——加密正文无法建立可核对引用；</li>
 *     <li><b>一页一个来源块</b>：定位写成 {@code 页 N}，人打开 PDF 就能核对；跨页合并会让引用指向错的页。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Component
public class AigPdfParseEngine implements KnowledgeParsePort {

    /**
     * 引擎 ID（含主版本，便于将来对比不同大版本的抽取差异）。
     */
    public static final String ENGINE_ID = "pdfbox-3";

    /**
     * 页面数上限：超过就不猜了。
     *
     * <p>2000 页以上的单份资料属于"应该拆分"，而不是"继续处理"——一次解析塞进内存再切成上万段，
     * 既拖住处理线程，也超出分段上限（{@code maxChunksPerVersion}）。这里提前失败，给出的原因比
     * 后面那条"分段超限"更准确。</p>
     */
    static final int MAX_PAGES = 2000;

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public boolean supports(AigKnowledgeSourceTypeEnum sourceType) {
        return sourceType == AigKnowledgeSourceTypeEnum.PDF;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try (PDDocument document = load(request)) {
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "PDF 共 " + pages + " 页，超过单份上限 " + MAX_PAGES + " 页：" + request.fileName()
                        + "。请先拆分资料（一次处理上万段既慢又超出分段上限）。");
            }
            List<ParsedBlock> blocks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pages; page++) {
                String text = pageText(document, stripper, page);
                if (text.isBlank()) {
                    continue;
                }
                blocks.add(new ParsedBlock(text, "页 " + page));
            }
            if (blocks.isEmpty()) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "PDF 一页文本都没取到：" + request.fileName()
                        + "。最常见的原因是扫描件/纯图 PDF（需要 OCR 引擎，内置引擎按设计不做 OCR）；"
                        + "请提供文本版 PDF，或转成 TXT/Markdown 后重传。");
            }
            return new ParseResult(ENGINE_ID, "PDF (PDFBox 3)", blocks);
        } catch (InvalidPasswordException e) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "PDF 已加密（需要密码）：" + request.fileName()
                    + "。请先解除密码保护后重传——加密正文无法建立可核对的引用。", e);
        } catch (IOException e) {
            throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                "PDF 无法读取（文件损坏或不是合法 PDF）：" + request.fileName(), e);
        }
    }

    /**
     * 载入 PDF。
     *
     * @param request 解析请求
     * @return 文档（调用方负责关闭）
     * @throws IOException 读取失败
     */
    private PDDocument load(ParseRequest request) throws IOException {
        return Loader.loadPDF(request.content());
    }

    /**
     * 取单页文本（保持阅读顺序）。
     *
     * @param document PDF
     * @param stripper 抽取器（复用，避免每页重建）
     * @param page     页码（1 起）
     * @return 文本；可能为空
     * @throws IOException 抽取失败
     */
    private String pageText(PDDocument document, PDFTextStripper stripper, int page) throws IOException {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return normalize(stripper.getText(document));
    }

    /**
     * 归一化抽取结果：去行尾空白、把连续空行压成一个空行、去首尾空白。
     *
     * <p>PDF 文本抽取会产出大量"视觉排版用"的空行与行尾空白，直接入库会让分段长度统计失去意义
     * （一段 800 字的预算里塞进 300 个空行）。</p>
     *
     * @param raw 原始文本
     * @return 归一化文本
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastBlank = true;
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                if (!lastBlank) {
                    sb.append('\n');
                    lastBlank = true;
                }
                continue;
            }
            sb.append(stripped).append('\n');
            lastBlank = false;
        }
        return sb.toString().strip();
    }

}
