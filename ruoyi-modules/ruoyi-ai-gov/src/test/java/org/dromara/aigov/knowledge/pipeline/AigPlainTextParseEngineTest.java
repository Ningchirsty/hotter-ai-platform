package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置文本/Markdown 解析引擎测试。
 *
 * <p>钉住三件最容易"静默出错"的事：编码判定、行号定位、Markdown 标题路径。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPlainTextParseEngineTest {

    private final AigPlainTextParseEngine engine = new AigPlainTextParseEngine();

    @Test
    @DisplayName("只处理 TXT/Markdown，不处理 PDF/DOCX")
    void supportsOnlyTextTypes() {
        assertTrue(engine.supports(AigKnowledgeSourceTypeEnum.TXT));
        assertTrue(engine.supports(AigKnowledgeSourceTypeEnum.MARKDOWN));
        assertFalse(engine.supports(AigKnowledgeSourceTypeEnum.PDF));
        assertFalse(engine.supports(AigKnowledgeSourceTypeEnum.DOCX));
        assertEquals("builtin-text-v1", engine.engineId());
    }

    @Test
    @DisplayName("★ 编码按 BOM / 严格 UTF-8 / GBK 判定，判不出就失败（不许硬解成乱码）")
    void encodingIsDecidedStrictly() {
        assertEquals("UTF-8", AigPlainTextParseEngine.decode(
            withPrefix(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "你好".getBytes(StandardCharsets.UTF_8)), "a.txt").encoding());
        assertEquals("UTF-16LE", AigPlainTextParseEngine.decode(
            withPrefix(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "你好".getBytes(StandardCharsets.UTF_16LE)), "a.txt").encoding());
        assertEquals("UTF-8", AigPlainTextParseEngine.decode(
            "中文资料".getBytes(StandardCharsets.UTF_8), "a.txt").encoding());
        assertEquals("GBK", AigPlainTextParseEngine.decode(
            "中文资料".getBytes(Charset.forName("GBK")), "a.txt").encoding());

        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> AigPlainTextParseEngine.decode(new byte[] {(byte) 0xC3, 0x28}, "a.txt"));
        assertEquals(AigKnowledgePipelineException.PARSE_ENCODING_UNKNOWN, error.getErrorCode());
        assertTrue(error.getMessage().contains("UTF-8"), error.getMessage());
    }

    @Test
    @DisplayName("TXT：一行一个来源块，定位是源文件行号（空行不产出块）")
    void plainBlocksKeepLineNumbers() {
        List<KnowledgeParsePort.ParsedBlock> blocks =
            AigPlainTextParseEngine.plainBlocks("第一行\n\n第三行\r\n   第四行   \n");

        assertEquals(3, blocks.size());
        assertEquals("第一行", blocks.get(0).text());
        assertEquals("行 1", blocks.get(0).locator());
        assertEquals("第三行", blocks.get(1).text());
        assertEquals("行 3", blocks.get(1).locator(), "空行不产出块，但行号必须是源文件里的行号");
        assertEquals("第四行", blocks.get(2).text());
        assertEquals("行 4", blocks.get(2).locator());
    }

    @Test
    @DisplayName("★ Markdown：定位带标题路径；代码围栏里的 # 不是标题；末尾 # 不算标题内容")
    void markdownBlocksTrackHeadings() {
        String markdown = """
            # 产品手册

            保修条款如下。

            ## 保修范围
            ```sh
            # 这是注释，不是标题
            ```
            ### 例外 ###

            末尾行
            """;

        List<KnowledgeParsePort.ParsedBlock> blocks = AigPlainTextParseEngine.markdownBlocks(markdown);

        assertTrue(blocks.get(0).locator().startsWith("产品手册"), blocks.get(0).locator());
        assertTrue(blocks.get(1).locator().startsWith("产品手册"), blocks.get(1).locator());
        assertTrue(blocks.get(2).locator().startsWith("产品手册 > 保修范围"), blocks.get(2).locator());
        KnowledgeParsePort.ParsedBlock inFence = blocks.stream()
            .filter(b -> b.text().contains("这是注释，不是标题")).findFirst().orElseThrow();
        assertTrue(inFence.locator().startsWith("产品手册 > 保修范围"), inFence.locator());
        KnowledgeParsePort.ParsedBlock exception = blocks.stream()
            .filter(b -> b.text().contains("例外")).findFirst().orElseThrow();
        assertEquals("产品手册 > 保修范围 > 例外 · 行 9", exception.locator(),
            "标题行的路径里不应带收尾 #（正文行本身按原名保留）");
        KnowledgeParsePort.ParsedBlock trailing = blocks.stream()
            .filter(b -> b.text().equals("末尾行")).findFirst().orElseThrow();
        assertTrue(trailing.locator().startsWith("产品手册 > 保修范围 > 例外"), trailing.locator());
    }

    @Test
    @DisplayName("解析结果带引擎与编码；纯空白文件按失败处理")
    void parseReportsEngineAndFailsOnBlank() {
        KnowledgeParsePort.ParseResult result = engine.parse(new KnowledgeParsePort.ParseRequest(
            "hello\nworld\n".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT, "a.txt"));
        assertEquals("builtin-text-v1", result.engineId());
        assertEquals("UTF-8", result.encoding());
        assertEquals(2, result.blocks().size());
        assertTrue(result.fullText().contains("world"));

        AigKnowledgePipelineException blank = assertThrows(AigKnowledgePipelineException.class,
            () -> engine.parse(new KnowledgeParsePort.ParseRequest("   \n\n".getBytes(StandardCharsets.UTF_8),
                AigKnowledgeSourceTypeEnum.TXT, "a.txt")));
        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, blank.getErrorCode());
    }

    @Test
    @DisplayName("内容为空在进入引擎前就被挡住（ParseRequest 构造器不变量）")
    void emptyContentRejectedByRequest() {
        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> new KnowledgeParsePort.ParseRequest(new byte[0], AigKnowledgeSourceTypeEnum.TXT, "a.txt"));
        assertTrue(error.getMessage().contains("内容为空"), error.getMessage());
    }

    private static byte[] withPrefix(byte[] prefix, byte[] body) {
        byte[] out = new byte[body.length + prefix.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(body, 0, out, prefix.length, body.length);
        return out;
    }

}
