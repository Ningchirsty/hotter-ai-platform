package org.dromara.aigov.knowledge.pipeline;

import java.util.Collection;
import java.util.List;

/**
 * 向量索引端口（设计 §7.4；附件「向量存储……通过已批准资料的评测确定」）。
 *
 * <p><b>本体库不存向量</b>：{@code aig_knowledge_chunk} 只保留 {@code index_ref}（索引里的键），
 * 向量本身由本端口写进"索引"。这样"换向量存储"不会牵动业务表结构——设计上向量存储是被评测
 * 决定的外部件，而不是知识表的一部分。</p>
 *
 * <h3>三条硬约束</h3>
 * <ol>
 *     <li><b>替换要原子</b>：同一版本重建索引时，旧向量必须先失效，否则"删掉的段落"还能被检索到；</li>
 *     <li><b>不同维度的向量不能混算</b>：维数不一致时宁可跳过并告警，也不能截断/补齐——那会产出
 *         一个"有分数但无意义"的排序；</li>
 *     <li><b>分数必须可比</b>：约定返回<b>余弦相似度</b>（-1..1，越大越像），这样关键词与向量两路
 *         结果才谈得上"可组合"。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface KnowledgeVectorIndexPort {

    /**
     * 索引引擎标识（写入 {@code aig_knowledge_chunk.index_ref} 的前缀，回答"这个键在哪个索引里"）。
     *
     * @return 稳定的引擎 ID
     */
    String engineId();

    /**
     * 用给定向量<b>替换</b>某版本的全部向量。
     *
     * @param versionId 文档版本ID
     * @param entries   该版本的全部分段向量（可空 = 清空该版本）
     * @return 实际写入条数
     */
    int replace(Long versionId, List<VectorEntry> entries);

    /**
     * 在给定版本范围内按余弦相似度检索。
     *
     * @param versionIds   版本范围（授权前过滤的结果；空 = 返回空）
     * @param queryVector  查询向量
     * @param topK         最多返回条数
     * @return 命中（按相似度降序；分数 &lt;= 0 的不返回）
     */
    List<Hit> search(Collection<Long> versionIds, float[] queryVector, int topK);

    /**
     * 删除某版本的全部向量。
     *
     * @param versionId 版本ID
     * @return 删除的条数（无索引返回 0）
     */
    int remove(Long versionId);

    /**
     * 一条待写入的向量。
     *
     * @param chunkId 分段ID（检索后用它回查正文与授权）
     * @param vector  向量（长度必须等于使用的嵌入维度）
     */
    record VectorEntry(Long chunkId, float[] vector) {
    }

    /**
     * 一条检索命中。
     *
     * @param chunkId 分段ID
     * @param score   余弦相似度（越大越像）
     */
    record Hit(Long chunkId, double score) {
    }

}
