package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigAgentVersion;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 版本列表视图。
 *
 * <p><b>刻意裁掉长文本与结构化配置</b>：{@code inputSchema/outputSchema/promptTemplate/
 * configJson/allowedTools/forbiddenTools/knowledgeScopeJson/gateJson} 都不在列表视图里。
 * 理由不只是「省流量」——列表页若把它们都带出来，前端很容易顺手在前端做字段级判断，
 * 而那些判断本该由服务端按版本配置裁决。要看这些内容请走详情接口。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigAgentVersion.class)
public class AigAgentVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 所属 Agent
     */
    private Long agentId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * 适用业务场景
     */
    private String scenarioCode;

    /**
     * 需要的 Provider 能力编码
     */
    private String providerCapability;

    /**
     * 业务侧是否允许外部调用（Y/N）
     */
    private String allowExternal;

    /**
     * 来源 Package 版本
     */
    private Long packageVersionId;

    /**
     * 最近一次评测运行ID
     */
    private Long evaluationRunId;

    /**
     * 回滚目标版本
     */
    private Long rollbackTargetVersionId;

    /**
     * 校验通过时间
     */
    private LocalDateTime validatedAt;

    /**
     * 沙箱通过时间
     */
    private LocalDateTime sandboxTestedAt;

    /**
     * 人工批准人
     */
    private Long approvedBy;

    /**
     * 人工批准时间
     */
    private LocalDateTime approvedAt;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 版本说明
     */
    private String remark;

}
