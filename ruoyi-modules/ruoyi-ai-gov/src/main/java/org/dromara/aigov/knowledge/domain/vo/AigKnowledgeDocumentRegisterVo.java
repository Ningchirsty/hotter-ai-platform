package org.dromara.aigov.knowledge.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识文档版本登记结果（设计 §6.4）。
 *
 * <p>{@link #duplicateHint} 是"重复文件提示"，<b>不是拒绝</b>：设计 §6.4 要求"重复文件通过
 * 原文哈希提示，但不同业务范围下的同一文件不能直接共享授权"。因此命中重复时只提示，
 * 仍按本次集合建版本——共享授权必须由人显式决定，而不是被一次去重顺手做掉。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeDocumentRegisterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 版本ID
     */
    private Long versionId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 处理状态
     */
    private String status;

    /**
     * 是否命中"同哈希的既有版本"
     */
    private Boolean duplicateHint;

    /**
     * 命中的既有版本ID（可空）
     */
    private Long duplicateVersionId;

}
