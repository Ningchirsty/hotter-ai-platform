package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用逐次审计对象 aig_invocation_audit
 * <p><b>追加型审计表</b>：不继承 {@code BaseEntity}、没有 {@code del_flag}，
 * 与 {@code tl_sensitive_audit} 同口径；只落摘要与引用，不落受限原文。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("aig_invocation_audit")
public class AigInvocationAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID
     */
    @TableId(value = "audit_id")
    private Long auditId;

    /**
     * 调用链ID（一次业务动作一个）
     */
    private String traceId;

    /**
     * 业务能力编码
     */
    private String capabilityCode;

    /**
     * 调用人用户ID
     */
    private Long callerId;

    /**
     * 调用人账号（冗余，便于离线审计）
     */
    private String callerName;

    /**
     * 本次数据等级
     */
    private String dataLevel;

    /**
     * 场景编码（可为空）
     * <p>场景强制绑定会收窄候选（设计 §4.4 第 4 步）。审计里只留下「最终用了哪家」、
     * 不留「因为哪个场景才只剩这家」的话，事后无法回答「为什么这次没走默认首选」。</p>
     */
    private String scenarioCode;

    /**
     * 实际使用的模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 实际使用的供应商ID（sai_model_config.provider_id）
     * <p><b>为什么必须单独存一份</b>：模型会换归属（同一家网关把模型迁到另一个供应商、
     * 或模型下架后重建）。若审计只记模型ID，「这家供应商这个月花了多少、外发了多少次」
     * 只能靠 join 现查，而 join 出来的是<b>今天</b>的归属，不是当时那次的。
     * 费用与合规口径必须按「当时是谁」算。</p>
     */
    private Long providerId;

    /**
     * 模型键（内部标识）
     */
    private String modelKey;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 本次调用所属的 Agent 版本ID（治理层发布的 {@code aig_agent_version.id}，可为空）
     * <p><b>为什么需要它</b>：{@code model_version} 是模型版本，不是治理层发布的 Agent 版本。
     * 灰度（CANDIDATE→STABLE 的 CANARY 门槛）要按版本统计「被调用多少次 / 失败几次 /
     * 有无严重错误」，只有模型维度是统计不出来的——同一模型可能挂着多个 Agent 版本。</p>
     * <p>为空表示「本次没绑定到某个 Agent 版本」（例如直接调能力、不经任务），
     * <b>不是</b>「不知道」。</p>
     */
    private Long agentVersionId;

    /**
     * 部署类型
     */
    private String deploymentType;

    /**
     * 是否外发（Y是 N否）
     */
    private String externalCall;

    /**
     * 命中的路由策略摘要
     */
    private String policyHit;

    /**
     * 输入摘要哈希（不存原文）
     */
    private String inputHash;

    /**
     * 不可变输入快照引用（设计 §4.3 {@code input_snapshot_ref}）
     * <p>只存<b>引用</b>（对象键/业务ID），不存快照副本——审计表是逐次追加的，
     * 塞副本会让它迅速膨胀，且与「输入原文不落库」的原则冲突。
     * 它是事后复现一次结论的唯一入口：没有它，同一个 traceId 只能看到「用了什么模型」，
     * 看不到「当时喂进去的是什么」。</p>
     */
    private String inputSnapshotRef;

    /**
     * 输入摘要（仅在审计等级允许时写入，禁止写入人才个人资料）
     */
    private String inputSummary;

    /**
     * 输出引用（对象键/业务ID，不存完整输出副本）
     */
    private String outputRef;

    /**
     * 结果（0成功 1失败）
     */
    private String result;

    /**
     * 错误摘要
     */
    private String errorSummary;

    /**
     * 错误分类编码（{@code AigErrorClassEnum} 的 code），成功或未归类时为空
     * <p><b>为什么要单独一列而不是从 policy_hit 里解析</b>：分类信息此前确实被写进了
     * {@code policy_hit}（形如「错误分类=POLICY_DENIED（…）」），但 {@code policy_hit} 是
     * varchar(255) 且该片段是<b>最后追加</b>的——策略命中多时它正好最先被截掉，
     * 于是「有没有严重错误」会静默漏判。判据要可靠，就不能压在会被截断的文本上。</p>
     * <p>只在<b>最终失败</b>时写入：主候选失败、备选成功的那次调用是成功的，
     * 不该被算成严重错误。</p>
     */
    private String errorClass;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 本次成本（单位＝<b>美元 USD</b>，全平台唯一金额口径，见
     * {@link org.dromara.aigov.constant.AigConstants#COST_CURRENCY}）
     *
     * <p>由调用器回填；多数外部供应商不回执费用，<b>为空表示未知而非免费</b>——
     * 「没有回执」与「这次没花钱」是两件事，把前者当 0 会把账算少。</p>
     */
    private BigDecimal cost;

    /**
     * 模型用量回执（JSON；当前为 {@code {"tokensUsed":N,"cost":X}}）
     * <p><b>为什么不拆成列</b>：不同供应商回执的用量字段差别很大（总 token、
     * 输入/输出 token、图像张数、视频秒数、阶梯单价…）。每来一家就加一列，
     * 迁移会失控；而审计的读取方式是「按 traceId 取一行给人看」，不是按用量列聚合。
     * 恒为数字键值对，无字符串拼接与转义问题。</p>
     * <p>为空表示<b>该次调用没有拿到任何用量</b>（例如图像模型不回执 token），
     * 与「用量为 0」是两件事。</p>
     */
    private String usageJson;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 人工结论（PENDING/ACCEPTED/REJECTED/NOT_REQUIRED）
     */
    private String manualDecision;

    /**
     * 调用时间
     */
    private LocalDateTime operateTime;

}
