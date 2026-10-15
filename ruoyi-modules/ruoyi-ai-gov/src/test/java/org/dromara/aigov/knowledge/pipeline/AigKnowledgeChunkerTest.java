package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分段器测试。
 *
 * <p>钉住：短块合并后定位写成范围、超长块内部重叠、越界直接失败。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigKnowledgeChunkerTest {

    private static AigKnowledgePipelineProperties props(int target, int overlap, int maxChunks) {
        AigKnowledgePipelineProperties properties = new AigKnowledgePipelineProperties();
        properties.setChunkTargetChars(target);
        properties.setChunkOverlapChars(overlap);
        properties.setMaxChunksPerVersion(maxChunks);
        return properties;
    }

    private static KnowledgeParsePort.ParsedBlock block(String text, String locator) {
        return new KnowledgeParsePort.ParsedBlock(text, locator);
    }

    @Test
    @DisplayName("★ 相邻短块合并成一段，定位写成范围（只留第一条定位会让引用比内容更窄）")
    void mergesShortBlocksIntoRangeLocator() {
        List<AigKnowledgeChunker.DraftChunk> chunks = AigKnowledgeChunker.chunk(List.of(
            block("aaaa", "行 1"),
            block("bbbb", "行 2")), props(10, 3, 100));

        assertEquals(1, chunks.size());
        assertEquals("aaaa\nbbbb", chunks.get(0).text());
        assertEquals("行 1～行 2", chunks.get(0).locator());
    }

    @Test
    @DisplayName("超过目标长度就换段：不硬塞（硬塞会让单段体量失控）")
    void flushesWhenTargetExceeded() {
        List<AigKnowledgeChunker.DraftChunk> chunks = AigKnowledgeChunker.chunk(List.of(
            block("0123456789", "行 1"),
            block("ab", "行 2")), props(10, 3, 100));

        assertEquals(2, chunks.size());
        assertEquals("0123456789", chunks.get(0).text());
        assertEquals("行 1", chunks.get(0).locator());
        assertEquals("ab", chunks.get(1).text());
        assertEquals("行 2", chunks.get(1).locator());
    }

    @Test
    @DisplayName("★ 超长块内部重叠切分并标注片段序号（同一块内重叠没有歧义）")
    void splitsLongBlockWithOverlap() {
        List<AigKnowledgeChunker.DraftChunk> chunks = AigKnowledgeChunker.chunk(List.of(
            block("0123456789abcdefghijklmno", "页 1")), props(10, 3, 100));

        assertEquals(4, chunks.size());
        assertEquals("0123456789", chunks.get(0).text());
        assertEquals("页 1（片段 1）", chunks.get(0).locator());
        assertTrue(chunks.get(1).text().startsWith("789"), chunks.get(1).text());
        assertEquals("页 1（片段 4）", chunks.get(3).locator());
    }

    @Test
    @DisplayName("★ 分段数超过上限：直接失败，绝不「只索引前 N 段」")
    void exceedingLimitFails() {
        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> AigKnowledgeChunker.chunk(List.of(
                block("0123456789", "行 1"),
                block("abcdefghij", "行 2"),
                block("klmnopqrst", "行 3")), props(10, 3, 2)));

        assertEquals(AigKnowledgePipelineException.CHUNK_LIMIT_EXCEEDED, error.getErrorCode());
        assertTrue(error.getMessage().contains("max-chunks-per-version"), error.getMessage());
    }

    @Test
    @DisplayName("重叠长度被夹在目标长度以内（否则窗口不前进，会死循环）")
    void overlapIsClamped() {
        List<AigKnowledgeChunker.DraftChunk> chunks = AigKnowledgeChunker.chunk(List.of(
            block("0123456789ab", "页 1")), props(10, 50, 100));

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(1).text().startsWith(chunks.get(0).text().substring(1)),
            chunks.get(1).text());
    }

    @Test
    @DisplayName("全是空白来源块：没有可索引内容，按失败处理")
    void blankBlocksFail() {
        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> AigKnowledgeChunker.chunk(List.of(block("   ", "行 1"), block("", "行 2")),
                props(10, 3, 100)));
        assertEquals(AigKnowledgePipelineException.PARSE_FAILED, error.getErrorCode());
    }

}
