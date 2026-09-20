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
     * 模型ID
     */
    private Long modelId;

    /**
     * 模型键
     */
    private String modelKey;

    /**
     * 模型版本
     */
    private String modelVersion;

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
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 本次成本
     */
    private BigDecimal cost;

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
