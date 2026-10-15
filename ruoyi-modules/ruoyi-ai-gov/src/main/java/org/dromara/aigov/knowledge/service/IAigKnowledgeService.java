package org.dromara.aigov.knowledge.service;

import org.dromara.aigov.knowledge.domain.AigKnowledgeCollection;
import org.dromara.aigov.knowledge.domain.AigKnowledgeDocumentVersion;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeCollectionBo;
import org.dromara.aigov.knowledge.domain.bo.AigKnowledgeDocumentBo;
import org.dromara.aigov.knowledge.domain.vo.AigKnowledgeDocumentRegisterVo;
import org.dromara.aigov.knowledge.enums.AigKnowledgeStatusEnum;

import java.util.List;

/**
 * 知识中心服务（设计 §6.4、§8.2）。
 *
 * <p>本轮范围：集合管理、文档/版本登记、处理管道推进（含失败与退役）。
 * 检索通过 {@code KnowledgePort} 单独提供；正文与解析器不在仓储层实现。</p>
 *
 * @author ai-gov
 */
public interface IAigKnowledgeService {

    /**
     * 新建知识集合。
     *
     * @param bo      入参
     * @param actorId 操作用户
     * @return 集合ID
     */
    Long createCollection(AigKnowledgeCollectionBo bo, Long actorId);

    /**
     * 列出全部启用集合。
     *
     * @return 集合清单
     */
    List<AigKnowledgeCollection> listCollections();

    /**
     * 列出知识文档（可按集合过滤）。
     *
     * @param collectionId 集合ID（可空=全部）
     * @return 文档清单
     */
    List<org.dromara.aigov.knowledge.domain.AigKnowledgeDocument> listDocuments(Long collectionId);

    /**
     * 登记文档与其一个新版本（处理管道从 {@code UPLOADED} 开始）。
     *
     * <p>命中同哈希的既有版本时只给"重复文件提示"，仍照常建版本——不同业务范围不共享授权。</p>
     *
     * @param bo      入参
     * @param actorId 操作用户
     * @return 登记结果
     */
    AigKnowledgeDocumentRegisterVo registerDocument(AigKnowledgeDocumentBo bo, Long actorId);

    /**
     * 取文档版本。
     *
     * @param versionId 版本ID
     * @return 版本；不存在抛错
     */
    AigKnowledgeDocumentVersion getVersion(Long versionId);

    /**
     * 列出某版本的分段预览（设计 §6.4 的「解析预览、分段」）。
     *
     * <p><b>只给预览不给正文</b>：返回的是受限长度的 {@code text_excerpt}，
     * 正文仍在受控存储引用里（设计 §10.2）。这条边界不是"暂时没做"，
     * 而是"维护人看解析效果"与"读取原文"本来就是两种权限、两件事。</p>
     *
     * <p>条数有上限（默认 {@value #DEFAULT_CHUNK_LIMIT}，最多 {@value #MAX_CHUNK_LIMIT}）：
     * 一次把整本资料的分段拉进界面既慢又没人看，还容易把受限预览堆在浏览器里。</p>
     *
     * @param versionId 版本ID（必填）
     * @param limit     最多返回几条（可空=默认；超过上限按上限）
     * @return 分段预览（按段序升序）
     */
    List<org.dromara.aigov.knowledge.domain.vo.AigKnowledgeChunkVo> listChunks(Long versionId, Integer limit);

    /**
     * 「谁在引用这个知识集合」（设计 §6.4「引用该版本的 Agent」）。
     *
     * <p>退役之前先看清楚影响面——这是维护人真正会做的动作。只给绑定 ID 不够，
     * 所以由 Agent 模块把 Agent 编码/名称/版本解析出来（解析属于它自己的表）。</p>
     *
     * @param collectionId 集合ID
     * @return 引用清单（含停用中的绑定，如实标注 enabled）；集合不存在时抛错
     */
    List<org.dromara.aigov.agent.domain.vo.AigAgentResourceReferenceVo> listReferencingAgents(
        Long collectionId);

    /**
     * 分段预览的默认条数。
     */
    int DEFAULT_CHUNK_LIMIT = 50;

    /**
     * 分段预览的条数上限。
     */
    int MAX_CHUNK_LIMIT = 200;

    /**
     * 推进一个处理阶段（设计 §6.4 的管道）。
     *
     * <p><b>幂等</b>：同一 {@code idempotencyKey} 重复调用不会重复推进（重试落到同一条任务）。
     * {@code FAILED} 状态下调用表示重试，从 {@code SCANNING} 重新开始。</p>
     *
     * @param versionId      版本ID
     * @param idempotencyKey 幂等键
     * @return 推进后的版本状态
     */
    AigKnowledgeStatusEnum advanceProcessing(Long versionId, String idempotencyKey);

    /**
     * 记录一次阶段失败：版本进入 {@code FAILED}，并写清失败阶段与原因。
     *
     * @param versionId      版本ID
     * @param idempotencyKey 幂等键
     * @param errorCode      失败分类
     * @param errorMessage   可读原因
     * @return 失败后的状态
     */
    AigKnowledgeStatusEnum failProcessing(Long versionId, String idempotencyKey,
                                          String errorCode, String errorMessage);

    /**
     * 退役一个版本：不再被新任务检索，历史结果仍按当时版本可追溯。
     *
     * @param versionId 版本ID
     * @return 退役后的状态
     */
    AigKnowledgeStatusEnum retire(Long versionId);

}
