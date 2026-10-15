package org.dromara.aigov.knowledge.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.config.AigKnowledgePipelineProperties;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
import org.dromara.aigov.knowledge.pipeline.KnowledgeEmbeddingPort;
import org.dromara.aigov.knowledge.pipeline.KnowledgeVectorIndexPort;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识检索实现（设计 §7.4）。
 *
 * <p><b>关键字检索是正式执行路径</b>：对 {@code text_excerpt} 做包含匹配并按段序返回。
 * 向量检索只作为<b>检索测试</b>的一路（{@link #retrieveByVector}）用于试点评测；正式执行路径
 * 的排序组合要等评测结论——设计 §7.4 明确"实际嵌入模型、向量存储和排序参数通过已批准资料的评测
 * 确定，不在本文件中宣称某方案已能达到准确率"。</p>
 *
 * <p>授权两道：先按允许集合/版本过滤出版本，再把片段按版本<b>复查</b>一次。
 * 即便内容相同，不同用户授权也不能共用未过滤的结果。向量一路同样要过这两道
 * （索引只回 chunkId，落点必须回库复查），否则"越权片段"会以"向量命中"的形式出现。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigKnowledgeRetrievalService implements KnowledgePort {

    private static final int DEFAULT_MAX_CHUNKS = 5;

    private static final int HARD_MAX_CHUNKS = 20;

    /**
     * 向量检索的过取倍数。
     *
     * <p>索引只回 chunkId，落点要回库复查（分段可能已被重建删除、可能已越权）。过取之后再按复查
     * 结果截到上限，避免"过取不足"导致明明有合法命中却返回空。</p>
     */
    private static final int VECTOR_OVERFETCH = 4;

    private final AigKnowledgeCollectionMapper collectionMapper;

    private final AigKnowledgeDocumentMapper documentMapper;

    private final AigKnowledgeDocumentVersionMapper versionMapper;

    private final AigKnowledgeChunkMapper chunkMapper;

    private final KnowledgeEmbeddingPort embeddingPort;

    private final KnowledgeVectorIndexPort vectorIndex;

    private final AigKnowledgePipelineProperties properties;

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query == null || StringUtils.isBlank(query.query())) {
            return RetrievalResult.none("查询为空");
        }
        int limit = limit(query);
        Resolved resolved = resolve(query);
        if (resolved.reason() != null) {
            return RetrievalResult.none(resolved.reason());
        }

        // 关键字检索（正式执行路径）
        List<AigKnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AigKnowledgeChunk>()
            .in(AigKnowledgeChunk::getDocumentVersionId, resolved.authorizedVersionIds())
            .like(AigKnowledgeChunk::getTextExcerpt, query.query())
            .orderByAsc(AigKnowledgeChunk::getDocumentVersionId)
            .orderByAsc(AigKnowledgeChunk::getOrdinal));
        if (chunks.isEmpty()) {
            return RetrievalResult.none("没有命中任何已授权片段");
        }

        List<Citation> citations = new ArrayList<>();
        for (AigKnowledgeChunk chunk : chunks) {
            // 授权后校验：片段所属版本必须仍在授权集合内
            if (!resolved.authorizedVersionIds().contains(chunk.getDocumentVersionId())) {
                continue;
            }
            citations.add(toCitation(chunk, resolved));
            if (citations.size() >= limit) {
                break;
            }
        }
        if (citations.isEmpty()) {
            return RetrievalResult.none("没有命中任何已授权片段");
        }
        log.info("知识检索命中, mode=KEYWORD, query={}, citations={}",
            StringUtils.substring(query.query(), 0, 50), citations.size());
        return RetrievalResult.of(citations);
    }

    /**
     * 向量检索（检索测试入口；见 {@link KnowledgePort#retrieveByVector}）。
     *
     * <p>命中排序是"按余弦相似度降序"，且只返回相似度为正的命中：非正相似度意味着"方向不同甚至
     * 相反"，把它当成命中比不给命中更糟。</p>
     *
     * @param query 检索请求
     * @return 检索结果
     */
    @Override
    public RetrievalResult retrieveByVector(RetrievalQuery query) {
        if (query == null || StringUtils.isBlank(query.query())) {
            return RetrievalResult.none("查询为空");
        }
        if (!properties.isVectorSearchEnabled()) {
            return RetrievalResult.none(
                "向量检索未启用（aigov.knowledge.pipeline.vector-search-enabled=false）");
        }
        int limit = limit(query);
        Resolved resolved = resolve(query);
        if (resolved.reason() != null) {
            return RetrievalResult.none(resolved.reason());
        }
        float[] queryVector = embeddingPort.embed(query.query());
        if (queryVector == null || queryVector.length == 0) {
            return RetrievalResult.none("查询文本没能生成向量（嵌入引擎 " + embeddingPort.engineId()
                + " 返回空向量）");
        }
        List<KnowledgeVectorIndexPort.Hit> hits = vectorIndex.search(resolved.authorizedVersionIds(),
            queryVector, Math.max(limit * VECTOR_OVERFETCH, limit));
        if (hits.isEmpty()) {
            return RetrievalResult.none("向量索引没有命中（该版本可能还没执行过「解析并索引」，或索引为空）：index="
                + vectorIndex.engineId() + ", embedding=" + embeddingPort.engineId());
        }

        List<Long> chunkIds = new ArrayList<>(hits.size());
        for (KnowledgeVectorIndexPort.Hit hit : hits) {
            chunkIds.add(hit.chunkId());
        }
        Map<Long, AigKnowledgeChunk> rowById = new LinkedHashMap<>();
        for (AigKnowledgeChunk row : chunkMapper.selectByIds(chunkIds)) {
            rowById.put(row.getChunkId(), row);
        }
        List<Citation> citations = new ArrayList<>();
        for (KnowledgeVectorIndexPort.Hit hit : hits) {
            AigKnowledgeChunk chunk = rowById.get(hit.chunkId());
            if (chunk == null) {
                // 索引里有、库里没有：多半是重建索引时的中间态，跳过而不是把不存在的来源当引用
                continue;
            }
            if (!resolved.authorizedVersionIds().contains(chunk.getDocumentVersionId())) {
                continue;
            }
            citations.add(toCitation(chunk, resolved));
            if (citations.size() >= limit) {
                break;
            }
        }
        if (citations.isEmpty()) {
            return RetrievalResult.none("向量索引命中的分段已被删除或不在授权范围内");
        }
        log.info("知识检索命中, mode=VECTOR, query={}, citations={}, embedding={}",
            StringUtils.substring(query.query(), 0, 50), citations.size(), embeddingPort.engineId());
        return RetrievalResult.of(citations);
    }

    /**
     * 归一化条数（空/非正数 → 默认；超过上限 → 按上限）。
     *
     * @param query 检索请求
     * @return 实际条数
     */
    private static int limit(RetrievalQuery query) {
        return query.maxChunks() == null || query.maxChunks() <= 0
            ? DEFAULT_MAX_CHUNKS : Math.min(query.maxChunks(), HARD_MAX_CHUNKS);
    }

    /**
     * 组装一条引用。
     *
     * @param chunk    分段行
     * @param resolved 授权范围解析结果
     * @return 引用
     */
    private static Citation toCitation(AigKnowledgeChunk chunk, Resolved resolved) {
        Long documentId = resolved.documentIdByVersionId().get(chunk.getDocumentVersionId());
        AigKnowledgeDocument document = documentId == null ? null : resolved.documentById().get(documentId);
        return new Citation(chunk.getChunkId(), chunk.getDocumentVersionId(),
            document == null ? null : document.getDocumentId(),
            document == null ? null : document.getSourceName(),
            chunk.getLocator(), chunk.getTextExcerpt(), chunk.getDataLevel());
    }

    /**
     * 解析授权范围（关键字与向量两路共用同一套判定）。
     *
     * <p>抽出来是为了让"两路检索的授权口径完全一致"成为结构上的事实：如果各写一遍，
     * 迟早会出现"关键字过滤了、向量忘了过滤"这类不对称——而那种错误只会以"越权片段出现在结果里"
     * 的形式暴露。</p>
     *
     * @param query 检索请求
     * @return 解析结果；{@link Resolved#reason()} 非空表示"没有可检索范围"及其原因
     */
    private Resolved resolve(RetrievalQuery query) {
        Set<String> allowedCodes = query.allowedCollectionCodes() == null
            ? Set.of() : query.allowedCollectionCodes();
        Set<Long> allowedVersions = query.allowedVersionIds() == null
            ? Set.of() : query.allowedVersionIds();
        if (allowedCodes.isEmpty() && allowedVersions.isEmpty()) {
            return Resolved.none("没有可检索的授权范围");
        }

        // 授权前过滤：先按集合解析候选文档与版本
        Map<Long, String> collectionCodeById = new HashMap<>();
        Map<String, Long> collectionIdByCode = new HashMap<>();
        if (!allowedCodes.isEmpty()) {
            List<AigKnowledgeCollection> collections = collectionMapper.selectList(
                new LambdaQueryWrapper<AigKnowledgeCollection>()
                    .in(AigKnowledgeCollection::getCollectionCode, allowedCodes));
            for (AigKnowledgeCollection c : collections) {
                // §7.2：身份层收窄——组织范围不匹配的集合不进入候选
                if (!AigKnowledgeAccess.orgAllowed(c.getOrgScope(), query.orgId())) {
                    continue;
                }
                collectionCodeById.put(c.getCollectionId(), c.getCollectionCode());
                collectionIdByCode.put(c.getCollectionCode(), c.getCollectionId());
            }
            if (collectionIdByCode.isEmpty()) {
                return Resolved.none("授权范围内没有知识集合");
            }
        }

        LambdaQueryWrapper<AigKnowledgeDocumentVersion> versionQuery =
            new LambdaQueryWrapper<AigKnowledgeDocumentVersion>();
        if (!allowedVersions.isEmpty()) {
            versionQuery.in(AigKnowledgeDocumentVersion::getVersionId, allowedVersions);
        } else {
            List<AigKnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<AigKnowledgeDocument>()
                    .in(AigKnowledgeDocument::getCollectionId, collectionIdByCode.values()));
            if (documents.isEmpty()) {
                return Resolved.none("授权范围内没有知识文档");
            }
            List<Long> documentIds = new ArrayList<>();
            for (AigKnowledgeDocument d : documents) {
                documentIds.add(d.getDocumentId());
            }
            versionQuery.in(AigKnowledgeDocumentVersion::getDocumentId, documentIds);
        }
        List<AigKnowledgeDocumentVersion> versions = versionMapper.selectList(versionQuery);
        if (versions.isEmpty()) {
            return Resolved.none("没有可检索的文档版本");
        }

        // 版本 → 文档（取集合与来源名）
        Set<Long> documentIds = new HashSet<>();
        for (AigKnowledgeDocumentVersion v : versions) {
            documentIds.add(v.getDocumentId());
        }
        List<AigKnowledgeDocument> documents = documentMapper.selectList(
            new LambdaQueryWrapper<AigKnowledgeDocument>().in(AigKnowledgeDocument::getDocumentId, documentIds));
        Map<Long, AigKnowledgeDocument> documentById = new HashMap<>();
        for (AigKnowledgeDocument d : documents) {
            documentById.put(d.getDocumentId(), d);
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> authorizedVersionIds = new HashSet<>();
        Map<Long, Long> documentIdByVersionId = new HashMap<>();
        for (AigKnowledgeDocumentVersion v : versions) {
            AigKnowledgeDocument document = documentById.get(v.getDocumentId());
            String code = document == null ? null : collectionCodeById.get(document.getCollectionId());
            if (AigKnowledgeAccess.retrievable(v, allowedVersions, allowedCodes, code, now)) {
                authorizedVersionIds.add(v.getVersionId());
                documentIdByVersionId.put(v.getVersionId(), v.getDocumentId());
            }
        }
        if (authorizedVersionIds.isEmpty()) {
            return Resolved.none("没有处于就绪状态且在授权范围内的知识版本");
        }
        return new Resolved(authorizedVersionIds, documentById, documentIdByVersionId, null);
    }

    /**
     * 授权范围解析结果。
     *
     * @param authorizedVersionIds 允许检索的版本
     * @param documentById         文档（取来源名）
     * @param documentIdByVersionId 版本 → 文档
     * @param reason               不可检索的原因；非空表示没有可用范围
     */
    private record Resolved(Set<Long> authorizedVersionIds, Map<Long, AigKnowledgeDocument> documentById,
                            Map<Long, Long> documentIdByVersionId, String reason) {

        /**
         * 不可检索。
         *
         * @param reason 原因
         * @return 结果
         */
        static Resolved none(String reason) {
            return new Resolved(Set.of(), Map.of(), Map.of(), reason);
        }

    }

}
