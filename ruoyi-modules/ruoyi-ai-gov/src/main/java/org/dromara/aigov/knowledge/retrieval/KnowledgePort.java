package org.dromara.aigov.knowledge.retrieval;

import java.util.List;
import java.util.Set;

/**
 * 知识检索端口（设计 §7.1 KnowledgePort、§7.4）。
 *
 * <p><b>三条硬约束</b>：</p>
 * <ol>
 *     <li><b>只返回已授权片段</b>：调用方先算出允许的集合与版本（授权前过滤），
 *         端口再对每个候选片段<b>复查</b>其版本在允许集合内（授权后校验）——两道都要有，
 *         否则"越权片段"会以"检索结果"的形式出现；</li>
 *     <li><b>没有支持资料就明说</b>：返回 {@link RetrievalResult#noSupport()} 与原因，
 *         而不是让上层拿一个空列表去猜"是没资料、还是检索坏了"；</li>
 *     <li><b>引用可核验</b>：每个引用带 chunkId / versionId / documentId / locator，
 *         上层据此确认"引用 ID 来自合法集合"，防止模型编造不存在的来源。</li>
 * </ol>
 *
 * <p>本端口只做<b>检索</b>，不做生成，也不做嵌入模型选择——嵌入/向量存储属于实现细节，
 * 按试点资料评测后接入（设计 §7.4）。</p>
 *
 * @author ai-gov
 */
public interface KnowledgePort {

    /**
     * 执行一次检索。
     *
     * @param query 检索请求
     * @return 检索结果
     */
    RetrievalResult retrieve(RetrievalQuery query);

    /**
     * 检索请求。
     *
     * @param query                 查询文本
     * @param allowedVersionIds     允许的文档版本ID（可空=由集合解析）
     * @param allowedCollectionCodes 允许的集合编码（授权范围）
     * @param maxChunks             返回片段上限
     * @param orgId                 调用方组织ID（可空=不做组织层收窄）
     */
    record RetrievalQuery(String query, Set<Long> allowedVersionIds, Set<String> allowedCollectionCodes,
                          Integer maxChunks, Long orgId) {
    }

    /**
     * 一条引用（设计 §7.4：sourceId、documentVersionId、page、section、chunkId、excerptRef）。
     *
     * @param chunkId    片段ID（引用 ID；必须来自合法集合）
     * @param versionId  文档版本ID
     * @param documentId 文档ID
     * @param sourceName 来源名（文档名）
     * @param locator    定位（页码/标题/行列）
     * @param excerptRef 引用预览
     * @param dataLevel  片段数据等级
     */
    record Citation(Long chunkId, Long versionId, Long documentId, String sourceName,
                    String locator, String excerptRef, String dataLevel) {
    }

    /**
     * 检索结果。
     *
     * @param citations 命中片段（可能为空）
     * @param noSupport 是否"没有支持资料"
     * @param reason    说明（noSupport 时必填）
     */
    record RetrievalResult(List<Citation> citations, boolean noSupport, String reason) {

        /**
         * 无支持资料。
         *
         * @param reason 原因
         * @return 结果
         */
        public static RetrievalResult none(String reason) {
            return new RetrievalResult(List.of(), true, reason);
        }

        /**
         * 有支持资料。
         *
         * @param citations 片段
         * @return 结果
         */
        public static RetrievalResult of(List<Citation> citations) {
            return new RetrievalResult(citations, citations == null || citations.isEmpty(), null);
        }
    }

}
