package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置 DOCX 解析引擎测试。
 *
 * <p>测试里自己造一个最小 DOCX（zip + {@code word/document.xml}）：不引入任何二进制夹具，
 * 也让"段落切分、实体反转义、制表/换行还原"这些规则能被逐条钉住。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigDocxParseEngineTest {

    private final AigDocxParseEngine engine = new AigDocxParseEngine();

    @Test
    @DisplayName("只处理 DOCX")
    void supportsOnlyDocx() {
        assertTrue(engine.supports(AigKnowledgeSourceTypeEnum.DOCX));
        assertFalse(engine.supports(AigKnowledgeSourceTypeEnum.TXT));
        assertEquals("builtin-docx-v1", engine.engineId());
    }

    @Test
    @DisplayName("★ 段落定位只数非空段：空段落不占号，也不产出分块")
    void paragraphsSkipEmptyOnes() {
        String xml = body("""
            <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
            <w:p/>
            <w:p><w:r><w:t>第二</w:t></w:r><w:r><w:t>段</w:t></w:r></w:p>
            <w:p><w:r><w:t xml:space="preserve">   </w:t></w:r></w:p>
            """);

        List<KnowledgeParsePort.ParsedBlock> blocks = AigDocxParseEngine.paragraphs(xml);

        assertEquals(2, blocks.size());
        assertEquals("第一段", blocks.get(0).text());
        assertEquals("第 1 段", blocks.get(0).locator());
        assertEquals("第二段", blocks.get(1).text());
        assertEquals("第 2 段", blocks.get(1).locator());
    }

    @Test
    @DisplayName("★ 实体只解一遍：&amp;lt; 不能被解成 &lt;（那是把用户写的内容改了意思）")
    void entitiesDecodedOnce() {
        assertEquals("a & b <c>", AigDocxParseEngine.unescapeXml("a &amp; b &lt;c&gt;"));
        assertEquals("A B", AigDocxParseEngine.unescapeXml("&#65; &#x42;"));
        assertEquals("&lt;", AigDocxParseEngine.unescapeXml("&amp;lt;"));
        assertEquals("&未知;", AigDocxParseEngine.unescapeXml("&未知;"), "不认识的实体原样保留");
    }

    @Test
    @DisplayName("制表符与换行在段落内还原（否则审阅时看到的排版会跑掉）")
    void tabsAndBreaksRestored() {
        assertEquals("a\tb", AigDocxParseEngine.paragraphText(
            "<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t></w:r></w:p>"));
        assertEquals("a\nb", AigDocxParseEngine.paragraphText(
            "<w:p><w:r><w:t>a</w:t><w:br/><w:t>b</w:t></w:r></w:p>"));
    }

    @Test
    @DisplayName("正常 DOCX：解析出引擎、编码与段落")
    void parseMinimalDocx() throws IOException {
        byte[] docx = docx(body("<w:p><w:r><w:t>保修条款</w:t></w:r></w:p>"));

        KnowledgeParsePort.ParseResult result = engine.parse(new KnowledgeParsePort.ParseRequest(
            docx, AigKnowledgeSourceTypeEnum.DOCX, "手册.docx"));

        assertEquals("builtin-docx-v1", result.engineId());
        assertEquals("UTF-8 (OOXML)", result.encoding());
        assertEquals("保修条款", result.blocks().get(0).text());
    }

    @Test
    @DisplayName("不是 zip / 缺 document.xml / 正文全空：三种都必须失败并说清原因")
    void brokenDocxIsExplicit() throws IOException {
        AigKnowledgePipelineException notZip = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest("not a zip".getBytes(StandardCharsets.UTF_8),
                AigKnowledgeSourceTypeEnum.DOCX, "a.docx")));
        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, notZip.getErrorCode());
        assertTrue(notZip.getMessage().contains("zip"), notZip.getMessage());

        AigKnowledgePipelineException noBody = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest(zip("word/styles.xml", "<x/>"),
                AigKnowledgeSourceTypeEnum.DOCX, "a.docx")));
        assertTrue(noBody.getMessage().contains("document.xml"), noBody.getMessage());

        AigKnowledgePipelineException empty = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest(
                docx(body("<w:p><w:r><w:t>   </w:t></w:r></w:p>")),
                AigKnowledgeSourceTypeEnum.DOCX, "a.docx")));
        assertTrue(empty.getMessage().contains("没有取到任何文本"), empty.getMessage());
    }

    private static String body(String paragraphs) {
        return "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body>" + paragraphs + "</w:body></w:document>";
    }

    private static byte[] docx(String documentXml) throws IOException {
        return zip("word/document.xml", documentXml);
    }

    private static byte[] zip(String entryName, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

}
