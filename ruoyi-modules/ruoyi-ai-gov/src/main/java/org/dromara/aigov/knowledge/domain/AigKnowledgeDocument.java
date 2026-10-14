package org.dromara.aigov.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识文档 aig_knowledge_document（设计 §8.2）。
 *
 * <p>文档是"稳定的业务身份"（同一份资料的多个版本挂在它下面），版本才承载不可变原文。
 * 与 Agent/Agent 版本是同一套分层：定义稳定、版本不可变。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_knowledge_document")
public class AigKnowledgeDocument extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文档ID
     */
    @TableId(value = "document_id")
    private Long documentId;

    /**
     * 所属集合
     */
    private Long collectionId;

    /**
     * 文档名
     */
    private String sourceName;

    /**
     * 来源类型（PDF/DOCX/TXT/MARKDOWN）
     */
    private String sourceType;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
