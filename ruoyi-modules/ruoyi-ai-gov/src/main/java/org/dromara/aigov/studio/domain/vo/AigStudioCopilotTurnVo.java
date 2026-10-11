package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioCopilotTurn;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话式 Copilot 轮次视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioCopilotTurn.class)
public class AigStudioCopilotTurnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对话轮次ID
     */
    private Long turnId;

    /**
     * 所属训练草稿
     */
    private Long draftId;

    /**
     * 对话基于的修订号
     */
    private Integer revision;

    /**
     * 对话基于的草稿内容哈希
     */
    private String contentHash;

    /**
     * 用户消息
     */
    private String userMessage;

    /**
     * 本次数据等级
     */
    private String dataLevel;

    /**
     * 模型原文（截断）
     */
    private String modelOutput;

    /**
     * 结论（SUCCEEDED / REJECTED / FAILED）
     */
    private String status;

    /**
     * 拒绝/失败原因
     */
    private String failureReason;

    /**
     * 产出的提案ID
     */
    private Long proposalId;

    /**
     * 网关调用链ID
     */
    private String traceId;

    /**
     * 命中的模型键
     */
    private String modelKey;

    /**
     * 部署类型
     */
    private String deploymentType;

    /**
     * 是否发生外发（Y/N）
     */
    private String externalCall;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
