package org.dromara.aigov.knowledge.domain.vo;

import lombok.Data;

/**
 * 知识索引结果（设计 §6.4 的处理管道）。
 *
 * <p>把"用了哪个解析引擎/嵌入引擎/写了多少段/向量索引是谁"一起返回：出问题时第一个要回答的就是
 * "这份资料到底被谁处理的、处理成了什么"，而这些信息一旦不返回，就只剩翻日志一条路。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeIndexingVo {

    /**
     * 文档版本ID
     */
    private Long versionId;

    /**
     * 处理后的版本状态（READY / FAILED）
     */
    private String status;

    /**
     * 解析引擎 ID
     */
    private String parserEngineId;

    /**
     * 嵌入引擎 ID
     */
    private String embeddingEngineId;

    /**
     * 编码/格式说明
     */
    private String encoding;

    /**
     * 分段条数
     */
    private Integer chunkCount;

    /**
     * 向量索引引擎 ID
     */
    private String vectorIndexEngine;

    /**
     * 失败分类（成功时为 null）
     */
    private String errorCode;

    /**
     * 可读说明
     */
    private String message;

}
