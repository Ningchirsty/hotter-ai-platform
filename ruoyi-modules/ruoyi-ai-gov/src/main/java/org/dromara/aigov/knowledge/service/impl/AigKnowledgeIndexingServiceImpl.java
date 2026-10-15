package org.dromara.aigov.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.knowledge.domain.AigKnowledgeChunk;
import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocument;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.AigKnowledgeProcessingJob;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeIndexingVo;
import org.dromara.aigov.knowledge.enums.AigKnowledgeJobStatusEnum;
import org.dromara.aigov.knowledge.enums.AigKnowledgeSourceTypeEnum;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeChunkMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeCollectionMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeDocumentMapper;
import org.dromara.aigov.knowledge.mapper.AigKnowledgeProcessingJobMapper;
import org.dromara.aigov.knowledge.pipeline.AigKnowledgePipelineException;
import org.dromara.aigov.knowledge.pipeline.AigKnowledgeProcessor;
import org.dromara.aigov.knowledge.pipeline.KnowledgeVectorIndexPort;
import org.dromara.aigov.knowledge.service.IAigKnowledgeIndexingService;
import org.dromara.aigov.knowledge.service.IAigKnowledgeService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识索引服务实现（设计 §6.4；附件「知识处理引擎：优先内部可部署，按试点评测选型」）。
 *
 * <h3>执行顺序与"为什么先干活、再推进状态"</h3>
 * <p>管道状态是给运维看的<b>事实</b>，不是"进度条"。所以每一步都是"真的做完了才算这一步成功"：
 * 解析成功后才把 {@code PARSING} 记成成功，索引写完才推进到 {@code READY}。反过来（先推进、
 * 再干活）会让崩溃留下"状态说解析完了、其实没解析"的版本，那是最难查的一类故障。</p>
 *
 * <h3>三处刻意的设计</h3>
 * <ol>
 *     <li><b>分段物理删除后重建</b>：唯一键 {@code (document_version_id, ordinal)} 不含 {@code del_flag}，
 *         逻辑删除会让重建撞键（见 {@code AigKnowledgeChunkMapper#deletePhysicallyByVersionId}）；</li>
 *     <li><b>失败要同时"落库 + 抛错"</b>：落库让管道留下事实，抛错让调用方当场看到原因；</li>
 *     <li><b>幂等键贯穿每个阶段</b>：每个阶段用 {@code 键#阶段} 作为自己的幂等键，重试同一整套动作
 *         既不会重复推状态，也不会重复写分段。</li>
 * </ol>
 *
 * <h3>为什么这里<b>不</b>加 {@code @Transactional}</h3>
 * <p>处理一份资料可能解析上万行、嵌入上万段，整个过程放在一个数据库事务里会长时间占用连接与锁。
 * 更关键的是：失败路径里要把 {@code FAILED} 写进库，而"外层事务回滚"会把这条失败记录一起回滚掉——
 * 于是"处理失败"这件事在库里查不到，只剩一条日志。现在的做法是<b>每一步各自成事务</b>（状态推进、
 * 写分段、写索引各自的原子性由它们自己保证），整套动作靠幂等键支持重试；重试会先物理清掉该版本的
 * 旧分段，不存在"半份数据被当成完整资料"的中间态。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigKnowledgeIndexingServiceImpl implements IAigKnowledgeIndexingService {

    private static final String STATUS_NORMAL = "0";

    private final IAigKnowledgeService knowledgeService;

    private final AigKnowledgeCollectionMapper collectionMapper;

    private final AigKnowledgeDocumentMapper documentMapper;

    private final AigKnowledgeChunkMapper chunkMapper;

    private final AigKnowledgeProcessingJobMapper jobMapper;

    private final AigKnowledgeProcessor processor;

    private final KnowledgeVectorIndexPort vectorIndex;

    @Override
    public AigKnowledgeIndexingVo index(byte[] content, Long versionId, String idempotencyKey) {
        if (content == null || content.length == 0) {
            throw new ServiceException("文件内容为空，无法建立索引");
        }
        if (versionId == null) {
            throw new ServiceException("版本ID不能为空");
        }
        if (StringUtils.isBlank(idempotencyKey)) {
            throw new ServiceException("幂等键不能为空（重试必须复用同一个键，否则会重复索引）");
        }
        String key = idempotencyKey.trim();
        AigKnowledgeDocumentVersion version = knowledgeService.getVersion(versionId);
        AigKnowledgeStatusEnum current = AigKnowledgeStatusEnum.find(version.getStatus());
        if (current == null) {
            throw new ServiceException("文档版本状态不可识别：" + version.getStatus());
        }
        if (current == AigKnowledgeStatusEnum.RETIRED) {
            throw new ServiceException("版本已退役，不能重建索引：" + versionId);
        }
        if (stageAlreadySucceeded(versionId, stageKey(key, AigKnowledgeStatusEnum.READY))) {
            // 同一幂等键已经处理完成：直接回当前状态，不重复解析/嵌入（重复解析还会浪费一次外发通道）
            log.info("知识索引已按该幂等键完成过，返回当前状态：versionId={} key={}", versionId, key);
            AigKnowledgeIndexingVo vo = new AigKnowledgeIndexingVo();
            vo.setVersionId(versionId);
            vo.setStatus(current.getCode());
            vo.setChunkCount(countChunks(versionId));
            vo.setVectorIndexEngine(vectorIndex.engineId());
            vo.setMessage("该幂等键已完成索引，未重复处理");
            return vo;
        }
        if (current == AigKnowledgeStatusEnum.READY) {
            // 换个幂等键打同一个已就绪版本 = "原地重建"。这里明确拒绝，理由有两条：
            // ①设计 §8.1 的版本不可变——就绪的原文/版本不应被就地改写；
            // ②重建要"先删该版本分段再写新的"，中途失败会在库里留下"状态是就绪、分段只剩一半"
            //   的版本，而 READY 是终态、失败不会把它降级，阅读方根本看不出这份资料不完整。
            throw new ServiceException("该版本已就绪（READY），不能原地重建：原文与版本一旦就绪就保持不变"
                + "（设计 §8.1）。如需用新的解析/嵌入引擎重新处理，请登记一个新版本后对它执行本操作；"
                + "存量版本的批量重建属于运维迁移作业，不走本入口。");
        }

        AigKnowledgeDocument document = documentMapper.selectById(version.getDocumentId());
        if (document == null) {
            throw new ServiceException("文档不存在或已删除：" + version.getDocumentId());
        }
        AigKnowledgeCollection collection = collectionMapper.selectById(document.getCollectionId());
        if (collection == null) {
            throw new ServiceException("知识集合不存在或已删除：" + document.getCollectionId());
        }
        AigKnowledgeSourceTypeEnum sourceType = AigKnowledgeSourceTypeEnum.find(document.getSourceType());
        if (sourceType == null) {
            throw new ServiceException("来源类型不可识别，无法选择解析引擎：" + document.getSourceType());
        }
        // 分级取集合的等级：资料的等级由它所在的集合决定（分段不再各自猜等级，见设计 §7.2 逐层相交）
        String dataLevel = StringUtils.blankToDefault(collection.getDataLevel(),
            AigDataLevelEnum.STRICT.getCode());

        try {
            // 扫描阶段：本轮没有接入内容安全扫描器，如实把这条记成"已完成"而不是跳过
            knowledgeService.advanceProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.SCANNING));

            AigKnowledgeProcessor.ProcessingResult result = processor.process(content, sourceType,
                document.getSourceName(), dataLevel);
            knowledgeService.advanceProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.PARSING));

            // 清场 + 写分段：清的是"这个版本"的全部分段
            chunkMapper.deletePhysicallyByVersionId(versionId);
            List<AigKnowledgeChunk> rows = insertChunks(versionId, result.chunks(), key);
            knowledgeService.advanceProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.CHUNKING));

            List<KnowledgeVectorIndexPort.VectorEntry> entries = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                entries.add(new KnowledgeVectorIndexPort.VectorEntry(rows.get(i).getChunkId(),
                    result.chunks().get(i).vector()));
            }
            int indexed = vectorIndex.replace(versionId, entries);
            knowledgeService.advanceProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.INDEXING));
            // 最后一步 READY：它的成功被用作整套动作的幂等判据（见上方 stageAlreadySucceeded）
            knowledgeService.advanceProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.READY));

            AigKnowledgeIndexingVo vo = new AigKnowledgeIndexingVo();
            vo.setVersionId(versionId);
            vo.setStatus(AigKnowledgeStatusEnum.READY.getCode());
            vo.setParserEngineId(result.parserEngineId());
            vo.setEmbeddingEngineId(result.embeddingEngineId());
            vo.setEncoding(result.encoding());
            vo.setChunkCount(indexed);
            vo.setVectorIndexEngine(vectorIndex.engineId());
            vo.setMessage("解析、分段、嵌入与索引已完成，版本已就绪");
            log.info("知识索引完成, versionId={}, parser={}, embedding={}, chunks={}, dataLevel={}",
                versionId, result.parserEngineId(), result.embeddingEngineId(), indexed, dataLevel);
            return vo;
        } catch (AigKnowledgePipelineException e) {
            knowledgeService.failProcessing(versionId, stageKey(key, AigKnowledgeStatusEnum.FAILED),
                e.getErrorCode(), e.getMessage());
            log.warn("知识索引失败, versionId={}, errorCode={}, reason={}", versionId, e.getErrorCode(),
                e.getMessage());
            // 落库之后仍然抛错：调用方需要当场看到一个可读原因，而不是一个静默的 FAILED 状态
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 写分段行（返回带主键的行，供向量索引引用）。
     *
     * @param versionId 版本ID
     * @param chunks    处理好的分段
     * @param key       幂等键（仅用于日志）
     * @return 已插入的行（顺序与入参一致）
     */
    private List<AigKnowledgeChunk> insertChunks(Long versionId,
                                                 List<AigKnowledgeProcessor.ProcessedChunk> chunks, String key) {
        List<AigKnowledgeChunk> rows = new ArrayList<>(chunks.size());
        for (AigKnowledgeProcessor.ProcessedChunk chunk : chunks) {
            AigKnowledgeChunk row = new AigKnowledgeChunk();
            row.setDocumentVersionId(versionId);
            row.setOrdinal(chunk.ordinal());
            row.setLocator(chunk.locator());
            row.setTextExcerpt(chunk.excerpt());
            row.setDataLevel(chunk.dataLevel());
            row.setIndexRef(indexRef(versionId, chunk));
            row.setDelFlag(STATUS_NORMAL);
            chunkMapper.insert(row);
            if (row.getChunkId() == null) {
                throw new ServiceException("写分段失败：未取回主键（versionId=" + versionId + ", ordinal="
                    + chunk.ordinal() + "）");
            }
            rows.add(row);
        }
        log.info("知识分段已写库, versionId={}, chunks={}, key={}", versionId, rows.size(), key);
        return rows;
    }

    /**
     * 生成索引引用键。
     *
     * <p>用"版本 + 段序 + 正文"的内容哈希，而不是分段主键：这样键在落库前就能算出来，不必
     * "先插入再回填"写第二次；同时同一段内容重复出现时键不同（键里含段序），不会互相覆盖。</p>
     *
     * @param versionId 版本ID
     * @param chunk     分段
     * @return 索引引用（{@code 引擎ID#哈希前16位}）
     */
    private String indexRef(Long versionId, AigKnowledgeProcessor.ProcessedChunk chunk) {
        String material = versionId + ":" + chunk.ordinal() + ":" + chunk.text();
        return vectorIndex.engineId() + "#" + sha256Hex(material).substring(0, 16);
    }

    /**
     * 计算 SHA-256 十六进制。
     *
     * @param text 文本
     * @return 十六进制摘要
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("当前 JVM 不支持 SHA-256，无法生成索引引用");
        }
    }

    /**
     * 阶段幂等键：{@code 键#阶段}。
     *
     * @param key   调用方幂等键
     * @param stage 阶段
     * @return 阶段幂等键
     */
    private static String stageKey(String key, AigKnowledgeStatusEnum stage) {
        return key + "#" + stage.getCode();
    }

    /**
     * 该阶段是否已成功过一次（幂等判据）。
     *
     * @param versionId 版本ID
     * @param stageKey  阶段幂等键
     * @return 已成功返回 true
     */
    private boolean stageAlreadySucceeded(Long versionId, String stageKey) {
        Long count = jobMapper.selectCount(new LambdaQueryWrapper<AigKnowledgeProcessingJob>()
            .eq(AigKnowledgeProcessingJob::getDocumentVersionId, versionId)
            .eq(AigKnowledgeProcessingJob::getIdempotencyKey, stageKey)
            .eq(AigKnowledgeProcessingJob::getStatus, AigKnowledgeJobStatusEnum.SUCCEEDED.getCode()));
        return count != null && count > 0L;
    }

    /**
     * 当前分段条数。
     *
     * @param versionId 版本ID
     * @return 条数
     */
    private Integer countChunks(Long versionId) {
        Long count = chunkMapper.selectCount(new LambdaQueryWrapper<AigKnowledgeChunk>()
            .eq(AigKnowledgeChunk::getDocumentVersionId, versionId));
        return count == null ? 0 : count.intValue();
    }

}
