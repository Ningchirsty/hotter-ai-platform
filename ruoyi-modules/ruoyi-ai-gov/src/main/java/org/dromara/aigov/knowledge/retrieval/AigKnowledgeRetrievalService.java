package org.dromara.aigov.knowledge.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentVersionMapper;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识检索实现（设计 §7.4）。
 *
 * <p><b>首期只做关键字检索</b>：对 {@code text_excerpt} 做包含匹配并按段序返回。
 * 向量检索与嵌入模型不在本实现里宣称可用——设计 §7.4 明确"实际嵌入模型、向量存储和排序参数
 * 通过已批准资料的评测确定，不在本文件中宣称某方案已能达到准确率"。</p>
 *
 * <p>授权两道：先按允许集合/版本过滤出版本，再把片段按版本<b>复查</b>一次。
 * 即便内容相同，不同用户授权也不能共用未过滤的结果。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigKnowledgeRetrievalService implements KnowledgePort {

    private static final int DEFAULT_MAX_CHUNKS = 5;

    private static final int HARD_MAX_CHUNKS = 20;

    private final AigKnowledgeCollectionMapper collectionMapper;
    private final AigKnowledgeDocumentMapper documentMapper;
    private final AigKnowledgeDocumentVersionMapper versionMapper;
    private final AigKnowledgeChunkMapper chunkMapper;

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query == null || StringUtils.isBlank(query.query())) {
            return RetrievalResult.none("查询为空");
        }
        Set<String> allowedCodes = query.allowedCollectionCodes() == null
            ? Set.of() : query.allowedCollectionCodes();
        Set<Long> allowedVersions = query.allowedVersionIds() == null
            ? Set.of() : query.allowedVersionIds();
        if (allowedCodes.isEmpty() && allowedVersions.isEmpty()) {
            return RetrievalResult.none("没有可检索的授权范围");
        }
        int limit = query.maxChunks() == null || query.maxChunks() <= 0
            ? DEFAULT_MAX_CHUNKS : Math.min(query.maxChunks(), HARD_MAX_CHUNKS);

        // 授权前过滤：先按集合解析候选文档与版本
        Map<Long, String> collectionCodeById = new HashMap<>();
        Map<String, Long> collectionIdByCode = new HashMap<>();
        if (!allowedCodes.isEmpty()) {
            List<AigKnowledgeCollection> collections = collectionMapper.selectList(
                new LambdaQueryWrapper<AigKnowledgeCollection>()
                    .in(AigKnowledgeCollection::getCollectionCode, allowedCodes));
            for (AigKnowledgeCollection c : collections) {
                collectionCodeById.put(c.getCollectionId(), c.getCollectionCode());
                collectionIdByCode.put(c.getCollectionCode(), c.getCollectionId());
            }
            if (collectionIdByCode.isEmpty()) {
                return RetrievalResult.none("授权范围内没有知识集合");
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
                return RetrievalResult.none("授权范围内没有知识文档");
            }
            List<Long> documentIds = new ArrayList<>();
            for (AigKnowledgeDocument d : documents) {
                documentIds.add(d.getDocumentId());
            }
            versionQuery.in(AigKnowledgeDocumentVersion::getDocumentId, documentIds);
        }
        List<AigKnowledgeDocumentVersion> versions = versionMapper.selectList(versionQuery);
        if (versions.isEmpty()) {
            return RetrievalResult.none("没有可检索的文档版本");
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
        for (AigKnowledgeDocumentVersion v : versions) {
            AigKnowledgeDocument document = documentById.get(v.getDocumentId());
            String code = document == null ? null : collectionCodeById.get(document.getCollectionId());
            if (AigKnowledgeAccess.retrievable(v, allowedVersions, allowedCodes, code, now)) {
                authorizedVersionIds.add(v.getVersionId());
            }
        }
        if (authorizedVersionIds.isEmpty()) {
            return RetrievalResult.none("没有处于就绪状态且在授权范围内的知识版本");
        }

        // 关键字检索（首期）
        List<AigKnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AigKnowledgeChunk>()
            .in(AigKnowledgeChunk::getDocumentVersionId, authorizedVersionIds)
            .like(AigKnowledgeChunk::getTextExcerpt, query.query())
            .orderByAsc(AigKnowledgeChunk::getDocumentVersionId)
            .orderByAsc(AigKnowledgeChunk::getOrdinal));
        if (chunks.isEmpty()) {
            return RetrievalResult.none("没有命中任何已授权片段");
        }

        List<Citation> citations = new ArrayList<>();
        for (AigKnowledgeChunk chunk : chunks) {
            // 授权后校验：片段所属版本必须仍在授权集合内
            if (!authorizedVersionIds.contains(chunk.getDocumentVersionId())) {
                continue;
            }
            AigKnowledgeDocumentVersion version = null;
            for (AigKnowledgeDocumentVersion v : versions) {
                if (v.getVersionId().equals(chunk.getDocumentVersionId())) {
                    version = v;
                    break;
                }
            }
            AigKnowledgeDocument document = version == null ? null : documentById.get(version.getDocumentId());
            citations.add(new Citation(chunk.getChunkId(), chunk.getDocumentVersionId(),
                document == null ? null : document.getDocumentId(),
                document == null ? null : document.getSourceName(),
                chunk.getLocator(), chunk.getTextExcerpt(), chunk.getDataLevel()));
            if (citations.size() >= limit) {
                break;
            }
        }
        if (citations.isEmpty()) {
            return RetrievalResult.none("没有命中任何已授权片段");
        }
        log.info("知识检索命中, query={}, citations={}", StringUtils.substring(query.query(), 0, 50),
            citations.size());
        return RetrievalResult.of(citations);
    }

}
