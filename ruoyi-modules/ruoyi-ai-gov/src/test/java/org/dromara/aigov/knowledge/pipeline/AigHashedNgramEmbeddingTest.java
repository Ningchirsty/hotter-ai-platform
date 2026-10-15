package org.dromara.aigov.knowledge.pipeline;

import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置哈希嵌入测试。
 *
 * <p>钉住"确定性、定长、归一化、空文本零向量"这四条形状约束，以及一条<b>行为</b>约束：
 * 字面相近的文本相似度必须高于无关文本——否则它连"可用性基线"都不算。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigHashedNgramEmbeddingTest {

    private static AigHashedNgramEmbedding embedding(int dimension) {
        AigKnowledgePipelineProperties properties = new AigKnowledgePipelineProperties();
        properties.setEmbeddingDimension(dimension);
        return new AigHashedNgramEmbedding(properties);
    }

    @Test
    @DisplayName("维度来自配置；配置过小时上调（维度过小会让桶碰撞主导相似度）")
    void dimensionComesFromConfigWithFloor() {
        assertEquals(64, embedding(64).dimension());
        assertEquals(AigHashedNgramEmbedding.MIN_DIMENSION, embedding(4).dimension());
        assertEquals("builtin-hash-ngram-v1", embedding(64).engineId());
    }

    @Test
    @DisplayName("★ 确定性 + 定长 + L2 归一化（同一文本必须总是同一个向量）")
    void deterministicAndNormalized() {
        AigHashedNgramEmbedding engine = embedding(128);

        float[] first = engine.embed("产品保修条款 3-6 岁");
        float[] second = engine.embed("产品保修条款 3-6 岁");

        assertArrayEquals(first, second);
        assertEquals(128, first.length);
        double norm = 0;
        for (float value : first) {
            norm += (double) value * value;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-6);
    }

    @Test
    @DisplayName("空文本给零向量（调用方按「没有向量」处理，而不是当成相似）")
    void blankTextYieldsZeroVector() {
        float[] vector = embedding(64).embed("   ");
        assertEquals(64, vector.length);
        for (float value : vector) {
            assertEquals(0f, value);
        }
        assertEquals(0.0, cosine(embedding(64).embed(null), vector), 1e-9);
    }

    @Test
    @DisplayName("★ 行为底线：字面相近的文本相似度高于无关文本")
    void similarTextsScoreHigher() {
        AigHashedNgramEmbedding engine = embedding(256);

        double similar = cosine(engine.embed("产品保修条款与保修范围"),
            engine.embed("保修条款与产品手册"));
        double unrelated = cosine(engine.embed("产品保修条款与保修范围"),
            engine.embed("员工差旅报销流程"));

        assertTrue(similar > unrelated, "similar=" + similar + ", unrelated=" + unrelated);
        assertTrue(similar > 0.3, "字面高度重叠的两句话相似度过低：" + similar);
    }

    @Test
    @DisplayName("中日韩按单字 + 相邻双字出词；拉丁词大小写不敏感")
    void tokenizationDetails() {
        AigHashedNgramEmbedding engine = embedding(256);

        // 双字词让"知识库"与"知识库检索"高度重叠（单字区分度不够）
        assertTrue(cosine(engine.embed("知识库"), engine.embed("知识库检索")) > 0.5);
        assertArrayEquals(engine.embed("ABC-123"), engine.embed("abc-123"));
        assertTrue(cosine(engine.embed("保修"), engine.embed("报销")) < 0.5,
            "两个字不同的词不应被算成近似");
    }

    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

}
