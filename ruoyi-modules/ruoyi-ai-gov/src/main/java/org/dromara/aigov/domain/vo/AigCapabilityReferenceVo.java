package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「谁在引用这个能力」的一行引用（设计见
 * {@code docs/aigov/design/能力域-业务流程与界面联动改造方案.md} §8 Q6）。
 *
 * <h3>为什么要有它</h3>
 * <p>改策略、换候选、停用能力之前，平台必须能回答"这会影响到谁"。此前只有资源（工具/知识）
 * 有引用清单（{@code listResourceReferences}），**能力维度没有** —— 于是改动只能"先改再看谁报错"。</p>
 *
 * <h3>为什么三种引用放在同一个列表</h3>
 * <p>会话框要一次说清"谁在用"：Agent 版本（声明了它）、历史任务（真的调用过它）、训练台草稿
 * （正在写着要用它）。三种来源的性质不同，但**都属于"改了会影响到"**；
 * 分成三个接口会让确认框要等三次请求。</p>
 *
 * @author ai-gov
 */
@Data
public class AigCapabilityReferenceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 引用类型：{@code AGENT_VERSION}（Agent 版本声明）/ {@code TASK}（历史任务）/ {@code DRAFT}（训练台草稿）
     */
    private String refType;

    /**
     * 人类可读的一行说明（确认框与表格直接用这句话）
     */
    private String refLabel;

    /**
     * Agent ID（仅 AGENT_VERSION）
     */
    private Long agentId;

    /**
     * Agent 编码（仅 AGENT_VERSION）
     */
    private String agentCode;

    /**
     * Agent 名称（仅 AGENT_VERSION）
     */
    private String agentName;

    /**
     * Agent 版本ID（仅 AGENT_VERSION）
     */
    private Long agentVersionId;

    /**
     * Agent 版本号（仅 AGENT_VERSION）
     */
    private String agentVersion;

    /**
     * 发布状态（仅 AGENT_VERSION；在跑的版本风险最高）
     */
    private String releaseStatus;

    /**
     * 计数（TASK / DRAFT 类型）
     */
    private Integer count;

    /**
     * 补充说明：声明来源（provider_capability / config_json）等
     */
    private String detail;

}
