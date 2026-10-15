package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识处理器测试（解析 → 分段 → 嵌入）。
 *
 * <p>用桩解析引擎 + 真实哈希嵌入：被测的是"编排与不变量"，不是具体解析格式。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeProcessorTest {

    private static AigKnowledgePipelineProperties props(int target, int excerptMax) {
        AigKnowledgePipelineProperties properties = new AigKnowledgePipelineProperties();
        properties.setChunkTargetChars(target);
        properties.setChunkOverlapChars(2);
        properties.setExcerptMaxChars(excerptMax);
        properties.setEmbeddingDimension(64);
        return properties;
    }

    private static AigKnowledgeProcessor processor(List<KnowledgeParsePort.ParsedBlock> blocks,
                                                   AigKnowledgePipelineProperties properties) {
        AigKnowledgeParseRegistry registry = new AigKnowledgeParseRegistry(
            List.of(new StubParseEngine(blocks)), properties);
        return new AigKnowledgeProcessor(registry, new AigHashedNgramEmbedding(properties), properties);
    }

    @Test
    @DisplayName("★ 分段序号从 1 连续、数据等级原样贴到每条、向量长度等于嵌入维度")
    void chunksAreOrderedAndCarryDataLevel() {
        AigKnowledgePipelineProperties properties = props(10, 10);
        AigKnowledgeProcessor processor = processor(List.of(
            new KnowledgeParsePort.ParsedBlock("0123456789", "行 1"),
            new KnowledgeParsePort.ParsedBlock("ab", "行 2")), properties);

        AigKnowledgeProcessor.ProcessingResult result = processor.process(
            "x".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT, "a.txt", "STRICT");

        assertEquals("stub-parse", result.parserEngineId());
        assertEquals("UTF-8", result.encoding());
        assertEquals(2, result.chunks().size());
        assertEquals(1, result.chunks().get(0).ordinal());
        assertEquals(2, result.chunks().get(1).ordinal());
        assertEquals("行 1", result.chunks().get(0).locator());
        assertEquals("行 2", result.chunks().get(1).locator());
        for (AigKnowledgeProcessor.ProcessedChunk chunk : result.chunks()) {
            assertEquals("STRICT", chunk.dataLevel(), "分段等级必须来自集合，不能各自猜");
            assertNotNull(chunk.vector());
            assertEquals(64, chunk.vector().length);
        }
    }

    @Test
    @DisplayName("引用预览按列宽截断并带省略号（让人看得出「这不是全文」）")
    void excerptIsTruncatedWithEllipsis() {
        assertEquals("0123456…", AigKnowledgeProcessor.excerpt("0123456789", 8));
        assertEquals("短", AigKnowledgeProcessor.excerpt("短", 8));
    }

    @Test
    @DisplayName("★ 分段长度超过预览列宽 = 配置错误：处理前就挡住（否则正文被静默截断）")
    void chunkTargetAboveExcerptWidthFails() {
        AigKnowledgeProcessor processor = processor(List.of(
            new KnowledgeParsePort.ParsedBlock("内容", "行 1")), props(20, 10));

        ServiceException error = assertThrows(ServiceException.class, () -> processor.process(
            "x".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT, "a.txt", "STRICT"));

        assertTrue(error.getMessage().contains("chunk-target-chars"), error.getMessage());
        assertTrue(error.getMessage().contains("无处存放"), error.getMessage());
    }

    @Test
    @DisplayName("★ 嵌入抛错：分类成 INDEX_FAILED 并带上第几段（不能只说「处理失败」）")
    void embeddingFailureIsClassified() {
        AigKnowledgePipelineProperties properties = props(10, 10);
        AigKnowledgeParseRegistry registry = new AigKnowledgeParseRegistry(
            List.of(new StubParseEngine(List.of(new KnowledgeParsePort.ParsedBlock("内容", "行 1")))),
            properties);
        AigKnowledgeProcessor processor = new AigKnowledgeProcessor(registry, new FailingEmbedding(), properties);

        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> processor.process("x".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT,
                "a.txt", "STRICT"));

        assertEquals(AigKnowledgePipelineException.INDEX_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("第 1 段"), error.getMessage());
    }

    @Test
    @DisplayName("解析失败的错误分类原样上传（供 error_code 落库）")
    void parseFailureKeepsErrorCode() {
        AigKnowledgeProcessor processor = processor(List.of(), props(10, 10));

        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> processor.process("x".getBytes(StandardCharsets.UTF_8), AigKnowledgeSourceTypeEnum.TXT,
                "a.txt", "STRICT"));

        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, error.getErrorCode());
    }

    /**
     * 桩解析引擎：返回固定来源块；块为空列表时按解析失败抛出（模拟"取不到文本"）。
     */
    private static final class StubParseEngine implements KnowledgeParsePort {

        private final List<ParsedBlock> blocks;

        private StubParseEngine(List<ParsedBlock> blocks) {
            this.blocks = blocks;
        }

        @Override
        public String engineId() {
            return "stub-parse";
        }

        @Override
        public boolean supports(AigKnowledgeSourceTypeEnum sourceType) {
            return true;
        }

        @Override
        public ParseResult parse(ParseRequest request) {
            if (blocks.isEmpty()) {
                throw new AigKnowledgePipelineException(AigKnowledgePipelineException.PARSE_FAILED,
                    "桩引擎：没有取到任何文本");
            }
            return new ParseResult(engineId(), "UTF-8", blocks);
        }

    }

    /**
     * 桩嵌入：总是抛错。
     */
    private static final class FailingEmbedding implements KnowledgeEmbeddingPort {

        @Override
        public String engineId() {
            return "stub-embedding";
        }

        @Override
        public int dimension() {
            return 8;
        }

        @Override
        public float[] embed(String text) {
            throw new IllegalStateException("模型不可用");
        }

    }

}
