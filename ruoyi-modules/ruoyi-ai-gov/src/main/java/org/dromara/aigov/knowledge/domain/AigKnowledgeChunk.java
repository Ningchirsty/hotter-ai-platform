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
 * 知识分段 aig_knowledge_chunk（设计 §8.2、§7.4）。
 *
 * <p><b>正文不落库</b>：完整正文在 {@code text_ref}（有权限的存储引用），本表只存受限长度的
 * {@code text_excerpt} 作为引用预览。理由与设计 §10.2 一致：受限原文应走受控存储引用，
 * 而不是散落在业务表里。</p>
 *
 * <p>{@code locator} 记录页码/标题/行列，{@code index_ref} 是索引里的键——两者共同让
 * "引用能点回当前用户有权读取的来源"。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_knowledge_chunk")
public class AigKnowledgeChunk extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分段ID
     */
    @TableId(value = "chunk_id")
    private Long chunkId;

    /**
     * 文档版本
     */
    private Long documentVersionId;

    /**
     * 段序（同版本内唯一）
     */
    private Integer ordinal;

    /**
     * 来源定位（页码/标题/行列）
     */
    private String locator;

    /**
     * 正文存储引用
     */
    private String textRef;

    /**
     * 引用预览（受限长度）
     */
    private String textExcerpt;

    /**
     * 索引引用
     */
    private String indexRef;

    /**
     * 片段数据等级
     */
    private String dataLevel;

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
