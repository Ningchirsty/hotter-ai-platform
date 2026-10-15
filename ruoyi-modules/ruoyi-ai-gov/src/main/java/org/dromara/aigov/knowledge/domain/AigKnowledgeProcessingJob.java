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
 * 知识处理任务 aig_knowledge_processing_job（设计 §8.2、§6.4）。
 *
 * <p><b>幂等键是必须的</b>：处理步骤会被重试（网络、解析失败）。若重试各自建一条任务，
 * 结果是"同一份文档被解析/索引了多次"——重复的索引不会报错，只会让检索出现重复片段。
 * {@code (document_version_id, idempotency_key)} 唯一即可保证重试落到同一条。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_knowledge_processing_job")
public class AigKnowledgeProcessingJob extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理任务ID
     */
    @TableId(value = "job_id")
    private Long jobId;

    /**
     * 文档版本
     */
    private Long documentVersionId;

    /**
     * 处理阶段（AigKnowledgeStatusEnum 的阶段值）
     */
    private String stage;

    /**
     * 阶段状态（AigKnowledgeJobStatusEnum）
     */
    private String status;

    /**
     * 第几次尝试
     */
    private Integer attempt;

    /**
     * 失败分类
     */
    private String errorCode;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 幂等键
     */
    private String idempotencyKey;

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
