package org.dromara.aigov.knowledge.service;

import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeIndexingVo;

/**
 * 知识索引服务：把一个文档版本<b>真的处理出来</b>（设计 §6.4 的
 * {@code UPLOADED → SCANNING → PARSING → CHUNKING → INDEXING → READY}）。
 *
 * <p><b>它和 {@link IAigKnowledgeService#advanceProcessing} 的分工</b>：后者只做状态机推进
 * （谁都能调，用于运维手工纠偏），本服务做的是"真的解析、真的分段、真的嵌向量、真的写索引"。
 * 把两者分开是因为：只有状态没有内容时，"就绪"是一种假象——检索不到任何东西，而所有界面都显示正常。</p>
 *
 * @author ai-gov
 */
public interface IAigKnowledgeIndexingService {

    /**
     * 处理并索引一份文件内容。
     *
     * <p><b>失败会写库并抛错，两件都做</b>：写库是为了让"这份资料处理失败"这个事实留在管道里
     * （{@code FAILED} + 失败分类），抛错是为了让调用方当场看到一个可读的原因，而不是收到一个
     * 静默的失败状态。</p>
     *
     * @param content        文件字节
     * @param versionId      文档版本ID
     * @param idempotencyKey 幂等键（重试必须复用同一个键）
     * @return 索引结果
     */
    AigKnowledgeIndexingVo index(byte[] content, Long versionId, String idempotencyKey);

}
