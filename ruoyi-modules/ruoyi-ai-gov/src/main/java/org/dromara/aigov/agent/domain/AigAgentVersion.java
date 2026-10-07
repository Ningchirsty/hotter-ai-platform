package org.dromara.aigov.agent.domain;

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
 * Agent 版本 aig_agent_version（设计 §5.3 逐版本配置 + §5.4 发布状态机）。
 *
 * <p><b>本表是「配置即记录」的落点</b>：设计 §5.3 要求每个版本保存 Prompt/Workflow/输出 Schema、
 * 绑定 Skill、允许与禁止工具、Provider 能力与外发许可、知识/品牌/部门/角色范围、
 * 人工 Gate、黄金用例与回滚目标。这里把它们分成两类存：</p>
 * <ul>
 *     <li><b>会被程序读取并参与判定的</b>用独立列：{@link #promptTemplate}、
 *         {@link #inputSchema}/{@link #outputSchema}、{@link #providerCapability}、
 *         {@link #allowExternal}、{@link #allowedTools}/{@link #forbiddenTools}；</li>
 *     <li><b>暂时只做留档与页面展示的</b>放 {@link #configJson}：Workflow、知识/品牌/部门/角色范围。
 *         它们之所以先不建列，是因为检索与收窄逻辑还没实现——建了列却没人读，
 *         等于给后来的人一个「这里应该生效」的错觉。等 WP4 的知识库与作用域收窄落地时再抽列。</li>
 * </ul>
 *
 * <p><b>{@link #allowExternal} 的语义是「业务侧许可」，不是「一定会外发」</b>：
 * 它与路由策略{@code 取与}——两者都允许才可能外发，且最终以路由结论为准
 * （与 {@code aig_task.allow_external} 同口径）。</p>
 *
 * <p><b>并发</b>：发布状态推进用「按当前状态做条件更新」而不是通用乐观锁，
 * 见 {@code AigPackageVersion} 的说明。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_agent_version")
public class AigAgentVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent 版本ID
     */
    @TableId(value = "agent_version_id")
    private Long agentVersionId;

    /**
     * 所属 Agent
     */
    private Long agentId;

    /**
     * 版本号（同 Agent 内唯一）
     */
    private String version;

    /**
     * 发布状态机（{@code AigReleaseStatusEnum}）
     */
    private String releaseStatus;

    /**
     * 发布通道/可见范围（{@code AigReleaseChannelEnum}）
     */
    private String releaseChannel;

    /**
     * 适用业务场景（为空表示不限场景）
     */
    private String scenarioCode;

    /**
     * 输入 Schema
     */
    private String inputSchema;

    /**
     * 输出 Schema（结构化输出的字段定义）
     */
    private String outputSchema;

    /**
     * Prompt 模板
     */
    private String promptTemplate;

    /**
     * Prompt 变量定义
     */
    private String promptVariablesJson;

    /**
     * 版本配置汇总（Workflow / 知识范围 / 品牌范围 / 部门范围 / 角色范围 / 黄金用例 / 回滚目标）
     */
    private String configJson;

    /**
     * 允许工具清单
     */
    private String allowedTools;

    /**
     * 禁止工具清单
     */
    private String forbiddenTools;

    /**
     * 需要的 Provider 能力编码（路由只认能力，不认厂商）
     */
    private String providerCapability;

    /**
     * 业务侧是否允许外部调用（Y/N；与路由策略取与）
     */
    private String allowExternal;

    /**
     * 知识范围
     */
    private String knowledgeScopeJson;

    /**
     * 人工 Gate 配置
     */
    private String gateJson;

    /**
     * 来源 Package 版本（内置 Agent 为空）
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
     * Manifest/Schema 校验通过时间
     */
    private LocalDateTime validatedAt;

    /**
     * 沙箱运行通过时间
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 版本说明
     */
    private String remark;

}
