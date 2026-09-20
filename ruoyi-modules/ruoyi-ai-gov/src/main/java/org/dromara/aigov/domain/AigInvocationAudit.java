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
     * 实际使用的模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 模型键（内部标识）
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
     * 人工结论（PENDING/ACCEPTED/REJECTED/NOT_REQUIRED）
     */
    private String manualDecision;

    /**
     * 调用时间
     */
    private LocalDateTime operateTime;

}
