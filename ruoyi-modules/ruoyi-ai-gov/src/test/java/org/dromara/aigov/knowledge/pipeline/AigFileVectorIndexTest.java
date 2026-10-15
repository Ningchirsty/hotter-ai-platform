package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件向量索引测试（单节点基线）。
 *
 * <p>钉住：整版本替换、余弦排序、维度不符不混算、索引文件损坏必须暴露。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigFileVectorIndexTest {

    @TempDir
    Path tempDir;

    private AigFileVectorIndex index() {
        AigKnowledgePipelineProperties properties = new AigKnowledgePipelineProperties();
        properties.setVectorIndexDir(tempDir.toString());
        return new AigFileVectorIndex(properties);
    }

    @Test
    @DisplayName("★ 写入后按余弦相似度排序检索；分数为正才算命中")
    void replaceThenSearchOrdersByCosine() {
        AigFileVectorIndex index = index();
        int stored = index.replace(100L, List.of(
            new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f}),
            new KnowledgeVectorIndexPort.VectorEntry(2L, new float[] {0f, 1f})));

        assertEquals(2, stored);
        List<KnowledgeVectorIndexPort.Hit> hits = index.search(Set.of(100L), new float[] {0.9f, 0.1f}, 5);

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).chunkId());
        assertTrue(hits.get(0).score() > hits.get(1).score());
        assertEquals(List.of(), index.search(Set.of(100L), new float[] {-1f, 0f}, 5),
            "与查询方向相反的向量不是命中");
        assertEquals("builtin-file-cosine-v1", index.engineId());
    }

    @Test
    @DisplayName("★ 整版本替换是原子动作：删掉的分段不能再被检索到")
    void replaceDropsPreviousEntries() {
        AigFileVectorIndex index = index();
        index.replace(100L, List.of(
            new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f}),
            new KnowledgeVectorIndexPort.VectorEntry(2L, new float[] {1f, 0f})));

        index.replace(100L, List.of(new KnowledgeVectorIndexPort.VectorEntry(2L, new float[] {1f, 0f})));

        List<KnowledgeVectorIndexPort.Hit> hits = index.search(Set.of(100L), new float[] {1f, 0f}, 5);
        assertEquals(1, hits.size());
        assertEquals(2L, hits.get(0).chunkId());
    }

    @Test
    @DisplayName("空条目 = 清空该版本；remove 也清空（不存在时返回 0，不报错）")
    void emptyEntriesClearVersion() {
        AigFileVectorIndex index = index();
        index.replace(100L, List.of(new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f})));

        assertEquals(1, index.replace(100L, List.of()));
        assertEquals(0, index.remove(100L));
        assertEquals(List.of(), index.search(Set.of(100L), new float[] {1f, 0f}, 5));
        assertEquals(0, index.remove(999L));
    }

    @Test
    @DisplayName("★ 维度不一致：不截断、不补齐，直接失败（有分数但无意义的排序更危险）")
    void dimensionMismatchFails() {
        AigFileVectorIndex index = index();
        AigKnowledgePipelineException mixed = assertThrows(AigKnowledgePipelineException.class,
            () -> index.replace(100L, List.of(
                new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f}),
                new KnowledgeVectorIndexPort.VectorEntry(2L, new float[] {1f, 0f, 0f}))));
        assertEquals(AigKnowledgePipelineException.INDEX_FAILED, mixed.getErrorCode());
        assertTrue(mixed.getMessage().contains("维度不一致"), mixed.getMessage());

        index.replace(100L, List.of(new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f, 0f})));
        assertEquals(List.of(), index.search(Set.of(100L), new float[] {1f, 0f}, 5),
            "查询向量与索引维度不同：跳过而不是硬算");
    }

    @Test
    @DisplayName("★ 索引文件损坏必须报错：不能当成「没有向量」（那会让检索静默退化）")
    void corruptedIndexIsExposed() throws IOException {
        AigFileVectorIndex index = index();
        index.replace(100L, List.of(new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f, 0f})));
        Files.writeString(tempDir.resolve("100.json"), "{not json", StandardCharsets.UTF_8);

        AigKnowledgePipelineException error = assertThrows(AigKnowledgePipelineException.class,
            () -> index.search(Set.of(100L), new float[] {1f, 0f}, 5));
        assertEquals(AigKnowledgePipelineException.INDEX_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("损坏"), error.getMessage());
    }

    @Test
    @DisplayName("缺分段ID / 缺向量的条目、空版本号：都是明确失败")
    void invalidEntriesFail() {
        AigFileVectorIndex index = index();
        assertEquals(AigKnowledgePipelineException.INDEX_FAILED,
            assertThrows(AigKnowledgePipelineException.class,
                () -> index.replace(100L, List.of(
                    new KnowledgeVectorIndexPort.VectorEntry(null, new float[] {1f})))).getErrorCode());
        assertEquals(AigKnowledgePipelineException.INDEX_FAILED,
            assertThrows(AigKnowledgePipelineException.class,
                () -> index.replace(null, List.of(
                    new KnowledgeVectorIndexPort.VectorEntry(1L, new float[] {1f})))).getErrorCode());
    }

    @Test
    @DisplayName("不存在的版本 / 空范围：检索返回空（而不是抛错）")
    void searchWithoutIndexReturnsEmpty() {
        AigFileVectorIndex index = index();
        assertEquals(List.of(), index.search(Set.of(404L), new float[] {1f, 0f}, 5));
        assertEquals(List.of(), index.search(Set.of(), new float[] {1f, 0f}, 5));
        assertEquals(List.of(), index.search(Set.of(100L), new float[] {1f, 0f}, 0));
    }

}
