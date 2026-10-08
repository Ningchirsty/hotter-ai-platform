package org.dromara.aigov.helper;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 调用审计上下文。
 * <p>承载一次调用的全部留痕信息；{@code inputHash}/{@code inputSummary} 由
 * {@link AigAuditRecorder} 依据审计等级与脱敏规则统一生成，调用方不得自行拼接。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAuditContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调用链ID
     */
    private String traceId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 调用人用户ID
     */
    private Long callerId;

    /**
     * 调用人账号
     */
    private String callerName;

    /**
     * 本次数据等级
     */
    private String dataLevel;

    /**
     * 场景编码（可为空）
     * <p>记录「这次调用属于哪个业务场景」。场景会影响候选收窄（§4.4 第 4 步），
     * 事后只看到「用的是哪家」而不知道「为什么是这家」时，这一列是唯一的线索。</p>
     */
    private String scenarioCode;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 供应商ID（必须跟着「本次实际执行的候选」走）
     * <p>有序 fallback 之后真正跑的是备选模型，供应商可能完全不同；
     * 审计若仍记主候选的供应商，就成了假账——费用与合规口径都会按错的供应商统计。</p>
     */
    private Long providerId;

    /**
     * 模型键
     */
    private String modelKey;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 本次调用所属的 Agent 版本ID（治理层发布的版本，可为空）
     * <p>与 {@link #modelVersion} 不同：那是「模型」版本，这里是「Agent」版本。
     * 灰度的达标判据要按 Agent 版本统计调用次数/失败率/严重错误，缺了这一维度就无从统计。</p>
     */
    private Long agentVersionId;

    /**
     * 部署类型
     */
    private String deploymentType;

    /**
     * 是否外发
     */
    private boolean externalCall;

    /**
     * 命中的路由策略 / 过滤原因明细
     */
    private List<String> policyHits = new ArrayList<>();

    /**
     * 能力审计等级（SUMMARY/FULL/HASH_ONLY），为空按 SUMMARY 处理
     */
    private String auditLevel;

    /**
     * 人工结论（PENDING/ACCEPTED/REJECTED/NOT_REQUIRED）
     */
    private String manualDecision;

    /**
     * 结果（0成功 1失败）
     */
    private String result;

    /**
     * 错误摘要
     */
    private String errorSummary;

    /**
     * 错误分类编码（{@link org.dromara.aigov.enums.AigErrorClassEnum} 的 code），成功时为空
     * <p><b>为什么不复用 error_summary</b>：那是一段给人看的文本，机器读不懂；
     * 而「这次失败是策略拒绝、还是上游临时不可用」是两件完全不同的事——
     * 前者说明版本/配置错了，后者只说明当时网络不好。灰度的达标判据「无严重错误」
     * 必须按分类统计，不能去解析中文文案（文案一改就错）。</p>
     * <p>只在<b>最终失败</b>时写入：主候选失败但备选成功了的那次调用是成功的，
     * 不该被算成严重错误。</p>
     */
    private String errorClass;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 本次成本
     */
    private BigDecimal cost;

    /**
     * 模型用量回执（JSON，由调用编排按调用器回填的 tokens/cost 组装）
     */
    private String usageJson;

    /**
     * 不可变输入快照引用（只存引用，不存副本）
     */
    private String inputSnapshotRef;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 输出引用（业务ID/对象键，<b>不存输出副本</b>）
     */
    private String outputRef;

    /**
     * 提示词原文：仅用于计算哈希，不写正文（仅取长度）
     */
    private String prompt;

    /**
     * 结构化载荷：仅用于计算哈希与字段名摘要，不写取值
     */
    private Map<String, Object> payload;

    /**
     * 调用时间（为空时由记录器取当前时间）
     */
    private LocalDateTime operateTime;

}
