package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioExecutionLink;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 训练台测试证据视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioExecutionLink.class)
public class AigStudioExecutionLinkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    private Long linkId;

    /**
     * 所属草稿
     */
    private Long draftId;

    /**
     * 被测试的修订
     */
    private Long revisionId;

    /**
     * 测试时该修订的内容哈希
     */
    private String contentHash;

    /**
     * 本次测试使用的 Agent 版本
     */
    private Long agentVersionId;

    /**
     * 真实一次执行的任务ID
     */
    private Long executionId;

    /**
     * 黄金用例运行ID
     */
    private Long evalRunId;

    /**
     * 调用链追踪ID
     */
    private String traceId;

    /**
     * 测试状态（{@code AigStudioTestStatusEnum} 的 code）
     */
    private String testStatus;

    /**
     * 结果摘要哈希
     */
    private String resultDigest;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
