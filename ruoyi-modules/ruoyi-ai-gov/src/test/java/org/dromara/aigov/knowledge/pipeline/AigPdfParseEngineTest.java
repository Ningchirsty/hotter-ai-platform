package org.dromara.aigov.knowledge.pipeline;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 解析引擎测试。
 *
 * <p>测试用的 PDF <b>用 PDFBox 现场生成</b>：不落地任何二进制夹具，也让"每页一个来源块、
 * 定位=页 N"这条规则可以被直接核对。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPdfParseEngineTest {

    private final AigPdfParseEngine engine = new AigPdfParseEngine();

    @Test
    @DisplayName("只处理 PDF")
    void supportsOnlyPdf() {
        assertTrue(engine.supports(AigKnowledgeSourceTypeEnum.PDF));
        assertFalse(engine.supports(AigKnowledgeSourceTypeEnum.DOCX));
        assertEquals("pdfbox-3", engine.engineId());
    }

    @Test
    @DisplayName("★ 每页一个来源块，定位写成「页 N」；跨页不合并（合并会让引用指向错的页）")
    void oneBlockPerPageWithPageLocator() throws IOException {
        byte[] pdf = pdfWithPages(List.of("Invoice total 1200", "Second page content"));

        KnowledgeParsePort.ParseResult result = engine.parse(new KnowledgeParsePort.ParseRequest(
            pdf, AigKnowledgeSourceTypeEnum.PDF, "手册.pdf"));

        assertEquals("pdfbox-3", result.engineId());
        assertEquals(2, result.blocks().size());
        assertEquals("页 1", result.blocks().get(0).locator());
        assertEquals("页 2", result.blocks().get(1).locator());
        assertTrue(result.blocks().get(0).text().contains("Invoice total 1200"),
            result.blocks().get(0).text());
        assertTrue(result.blocks().get(1).text().contains("Second page content"),
            result.blocks().get(1).text());
    }

    @Test
    @DisplayName("★ 扫描件/纯图 PDF：必须失败并指向 OCR，不许返回空文本")
    void scannedPdfFailsLoudly() throws IOException {
        byte[] pdf = pdfWithPages(List.of(""));

        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest(pdf,
                AigKnowledgeSourceTypeEnum.PDF, "扫描件.pdf")));

        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("OCR"), error.getMessage());
        assertTrue(error.getMessage().contains("扫描件"), error.getMessage());
    }

    @Test
    @DisplayName("不是 PDF：明确说是「无法读取」，而不是「没有文本」")
    void nonPdfIsExplicit() {
        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest("not a pdf".getBytes(StandardCharsets.UTF_8),
                AigKnowledgeSourceTypeEnum.PDF, "a.pdf")));
        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("无法读取"), error.getMessage());
    }

    @Test
    @DisplayName("归一化：去行尾空白、连续空行压成一个、首尾不留空白（分段长度统计才有意义）")
    void normalizeCollapsesWhitespace() {
        assertEquals("a\n\nb", AigPdfParseEngine.normalize("  a  \n \n\n\n   b\n\n"));
        assertEquals("", AigPdfParseEngine.normalize(null));
        assertEquals("", AigPdfParseEngine.normalize("   \n  \n"));
    }

    private static byte[] pdfWithPages(List<String> texts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : texts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    if (!text.isEmpty()) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(60, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

}
