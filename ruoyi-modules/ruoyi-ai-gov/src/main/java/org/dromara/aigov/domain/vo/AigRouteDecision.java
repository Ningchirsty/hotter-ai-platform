package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 路由决策结果
 * <p>不抛异常，一律用 {@code decision} 表达结果：
 * {@code MODEL}（命中模型）/ {@code MANUAL}（转人工）/ {@code DENIED}（策略拒绝）。</p>
 * <p>{@code policyHits} 是排障依据：命中的策略、被过滤的模型与<b>过滤原因</b>
 * （例如「策略禁止外发，已排除外部部署模型」）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRouteDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路由结论（MODEL/MANUAL/DENIED）
     */
    private String decision;

    /**
     * 命中的路由策略ID（{@code aig_route_policy.policy_id}）。
     *
     * <p><b>为什么要有这个字段</b>：此前它只出现在 {@code policyHits} 的文案里
     * （「命中路由策略 policyId=…」），想回答"上周三那次外发是谁批准的、命中了哪条策略"
     * 只能去翻文本。M-005 的决策账本（{@code aig_policy_decision_log}）要求这些值
     * <b>结构化可查</b>，因此把它们提升为字段，让决策对象自足。</p>
     *
     * <p>无策略命中时为 null（例如未配置该「能力 × 数据等级」的策略）。</p>
     */
    private Long policyId;

    /**
     * 本次生效的 {@code allow_external}（已含 STRICT 级强制置 N 之后的值）。
     *
     * <p>记的是<b>实际生效</b>的值而不是策略表里的原值：STRICT 资料即便策略写着 Y，
     * 引擎也会强制按不允许外发处理，账本必须反映真实生效的口径。</p>
     */
    private String allowExternal;

    /**
     * 本次策略的 {@code fallback_to_manual}（无候选时是否转人工）。
     */
    private String fallbackToManual;

    /**
     * 命中的模型ID
     */
    private Long modelId;

    /**
     * 命中的模型键
     */
    private String modelKey;

    /**
     * 命中的部署类型
     */
    private String deploymentType;

    /**
     * 命中的调用器 Bean 名称（无可用调用器时为 null，调用编排据此报错）
     */
    private String invoker;

    /**
     * 决策依据明细（含被过滤原因，便于预览与排障）
     */
    private List<String> policyHits = new ArrayList<>();

    /**
     * 有序候选列表（PRIMARY → GRAY → FALLBACK，同级按 priority 升序）。
     *
     * <p>第 1 项即上面 {@code modelId/modelKey/deploymentType/invoker} 记录的主候选；
     * 主候选调用失败且错误分类允许 fallback 时，调用编排按序顺延到下一项。</p>
     *
     * <p>为空表示本次决策不是 {@code MODEL}（被拒绝或转人工），调用编排会退化为
     * 「单一模型」路径，保持对旧调用方的兼容。</p>
     */
    private List<AigRouteCandidate> candidates = new ArrayList<>();

    /**
     * 结论说明
     */
    private String reason;

    // ------------------------------------------------------------------
    // 以下为调用编排所需的上下文（避免重复查库）
    // ------------------------------------------------------------------

    /**
     * 命中的能力ID
     */
    private Long capabilityId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 本次调用的策略是否要求<b>调用授权审批</b>（{@code aig_route_policy.require_approval='Y'}）。
     *
     * <p>路由引擎只<b>读出并转达</b>这个要求，不在路由里做审批判定：授权是按「人 × 能力 × 数据等级」
     * 授的，而路由引擎拿不到调用人（`decide` 的入参里没有调用人）。因此它把要求放在这里，
     * 由统一调用入口用同一处解析出来的调用人去核对授权——判定所需的两样东西必须在同一个地方碰面，
     * 否则「按 A 的授权放行了 B 的调用」这类错会静默发生。</p>
     *
     * <p>默认 false：没有策略（或策略为 {@code 'N'}）时行为与引入本字段之前<b>逐字不变</b>。</p>
     */
    private boolean approvalRequired;

    /**
     * 能力审计等级（SUMMARY/FULL/HASH_ONLY）
     */
    private String auditLevel;

    /**
     * 能力输出 Schema（JSON），用于校验模型输出
     */
    private String outputSchema;

    /**
     * 能力要求人工确认的结论点
     */
    private List<String> humanConfirmPoints = new ArrayList<>();

    /**
     * 追加一条决策依据。
     *
     * @param hit 依据文本
     */
    public void addHit(String hit) {
        this.policyHits.add(hit);
    }

}
