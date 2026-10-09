package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 能力调用结果视图对象
 * <p>同时用于 {@code /invoke} 与 {@code /dryRun}；{@code output} 只在真实调用成功后返回，
 * <b>不写入任何业务事实表</b>。</p>
 *
 * @author ai-gov
 */
@Data
public class AigInvokeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调用链ID（一次业务动作一个）
     */
    private String traceId;

    /**
     * 路由结论（MODEL命中模型 / MANUAL转人工 / DENIED策略拒绝）
     */
    private String decision;

    /**
     * 命中的模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 模型键
     */
    private String modelKey;

    /**
     * 部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）
     */
    private String deploymentType;

    /**
     * 实际执行的调用器名称（如 OpenAiCompatibleInvoker / SnailAiChatInvoker / LocalRuleModelInvoker）。
     *
     * <p>部署类型只说明「哪一类模型」，调用器才说明「走的哪条链路」——
     * {@code EXTERNAL_API} 由治理层直连供应商端点，{@code GROUP} / {@code EXTERNAL_ENTERPRISE}
     * 走集团 snail-ai（实际模型由 Agent 决定）。排障时先用它定位，再去看对应调用器的日志。</p>
     */
    private String invoker;

    /**
     * 是否外发（true 表示数据离开本地环境，已写入审计）
     */
    private Boolean externalCall;

    /**
     * 模型输出（结构化 JSON 字符串）；仅真实调用成功且通过 output_schema 校验时返回
     */
    private String output;

    /**
     * 必须人工确认的结论点
     */
    private List<String> pendingConfirm;

    /**
     * 决策/失败原因
     */
    private String reason;

    /**
     * 结构化的错误分类编码（取值同 {@code AigErrorClassEnum}）；成功时为空。
     *
     * <p><b>为什么必须下发，而不是让上层从 {@link #reason} 文本里猜</b>：任务编排层要据此决定
     * 「向同一家重试 / 换候选 / 转人工 / 停在失败」，而错误分类内部早就算好了
     * （错误码 → HTTP 状态码 → 文本兜底 三级判定）。
     * 让上层去解析中文文案等于把已确定的结论重新猜一遍——文案一改就错，且错得没有痕迹。</p>
     */
    private String errorCode;

    /**
     * 结论<b>细因</b>编码（取值同 {@code AigPolicyReasonCodeEnum}，契约 reasonCodes 的镜像）；认不出时为空。
     *
     * <p>与 {@link #errorCode} 的分工：错误码决定「怎么处置」（重试/换候选/转人工），
     * 细因说明「具体卡在哪」（未配置策略、未绑定模型…）。任务层把两者一起落到
     * {@code aig_task.policy_reason} 与事件载荷里，运维不必再去翻路由日志。</p>
     */
    private String reasonCode;

    /**
     * 策略命中明细（路由 7 步的逐条判定，含「哪些候选模型因何被排除」）。
     * <p>与写入 {@code aig_invocation_audit.policy_hit} 的内容同源。
     * {@code dryRun} 正是「上线前预演」入口，只回一句 reason 无法解释
     * 「我期望的模型为什么没被选中」，故一并下发。</p>
     */
    private List<String> policyHits;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

}
