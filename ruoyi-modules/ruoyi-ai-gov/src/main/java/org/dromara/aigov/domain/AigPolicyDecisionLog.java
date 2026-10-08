package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 调用策略决策账本。
 *
 * <h3>它回答的问题</h3>
 * <p>{@code aig_invocation_audit} 记的是「调用<b>发生了什么</b>」（耗时/成本/结果/重试）；
 * 本表记的是「<b>为什么</b>放行 / 为什么拒绝」——策略身份、生效的外发口径、是否要求审批、
 * 被排除的候选与原因。ADR-006 要求「外发管控在执行点强制且<b>可举证</b>」，此前
 * {@code decide()} 的结论只进内存 + {@code policyHits} 文本，被拒时除了应用日志
 * 没有可查询的结构化证据，无法回答"上周三那次外发是谁批准的、命中了哪条策略"。</p>
 *
 * <h3>为什么没有 BaseEntity 的字段</h3>
 * <p>本表是<b>追加型账本，不做逻辑删除</b>——与 {@code aig_invocation_audit} 同口径
 * （见 {@code script/sql/aig_ai_gov.sql} 开头的约定）。因此刻意<b>不继承</b> {@code BaseEntity}：
 * 带上 {@code del_flag}/{@code create_by}/{@code update_time} 会误导后来者以为可以改、可以删。</p>
 *
 * <h3>写入点</h3>
 * <p>只在统一调用入口 {@code AigInvokeServiceImpl#invoke} 写，<b>不在</b>
 * {@code AigRouteServiceImpl.decide()} 里写——后者是纯判定（无调用人、无 DB 写），
 * 保持它纯净才能被单测直接驱动。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_policy_decision_log")
public class AigPolicyDecisionLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 决策ID
     */
    @TableId(value = "decision_id")
    private Long decisionId;

    /**
     * 调用链ID（与 {@code aig_invocation_audit.trace_id} 对齐，用于把「为什么」接到「发生了什么」）
     */
    private String traceId;

    /**
     * 业务能力编码
     */
    private String capabilityCode;

    /**
     * 本次数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     */
    private String dataLevel;

    /**
     * 决策结论（MODEL 命中模型 / MANUAL 转人工 / DENIED 策略拒绝）
     */
    private String decision;

    /**
     * 选中的模型ID（DENIED/MANUAL 时为空）
     */
    private Long modelId;

    /**
     * 选中的模型键（冗余，便于离线审计）
     */
    private String modelKey;

    /**
     * 选中模型的部署类型
     */
    private String deploymentType;

    /**
     * 命中的路由策略ID（为空=未配置该「能力 × 数据等级」的策略）
     */
    private Long policyId;

    /**
     * 本次<b>实际生效</b>的 {@code allow_external}。
     *
     * <p>记的是经 STRICT 级强制置 N 之后的生效值，<b>不是</b>策略表原值——
     * 记原值会在严格级资料上给出相反的答案。</p>
     */
    private String allowExternal;

    /**
     * 策略是否要求调用授权审批（Y/N）
     */
    private String approvalRequired;

    /**
     * 本次是否真的外发（Y/N）。DENIED/MANUAL 恒为 N；MODEL 按选中模型部署类型判定。
     */
    private String externalCall;

    /**
     * 细因（见 {@code contract/error-codes.json} 的 reasonCodes）
     */
    private String reasonCode;

    /**
     * 结论说明（decision 的一句话原因）
     */
    private String reason;

    /**
     * 被排除候选与原因摘要（来自 policyHits，截断保存）
     */
    private String excludedJson;

    /**
     * 调用人用户ID（调度/系统发起为空）
     */
    private Long callerId;

    /**
     * 本次归属的 Agent 版本ID（为空=本次未绑定版本，非「不知道」）
     */
    private Long agentVersionId;

    /**
     * 关联任务（一次业务动作一个）
     */
    private Long taskId;

    /**
     * 请求摘要哈希（同幂等键不同摘要要能看出来）
     */
    private String requestDigest;

    /**
     * 决策时间
     */
    private LocalDateTime operateTime;

}
