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
