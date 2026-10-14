package org.dromara.aigov.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识文档版本 aig_knowledge_document_version（设计 §8.2）。
 *
 * <p><b>原文不可变</b>：{@code sha256} 与 {@code source_ref} 一旦写入不再改写——改了就无法
 * 回答"当时检索到的是哪一份原文"。失效通过新版本 + 旧版本 {@code RETIRED} 表达，而不是原地改。</p>
 *
 * <p>{@code effective_from/to} 是闭区间（设计 §8.4），为空表示该侧不限。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_knowledge_document_version")
public class AigKnowledgeDocumentVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    @TableId(value = "version_id")
    private Long versionId;

    /**
     * 所属文档
     */
    private Long documentId;

    /**
     * 版本号（同文档内唯一）
     */
    private String version;

    /**
     * 原文哈希
     */
    private String sha256;

    /**
     * 原文件存储引用
     */
    private String sourceRef;

    /**
     * 生效开始
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束
     */
    private LocalDateTime effectiveTo;

    /**
     * 处理/生效状态
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
