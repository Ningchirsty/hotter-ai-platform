package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用逐次审计视图对象 aig_invocation_audit
 * <p>追加型审计表，<b>只读</b>；{@code inputSummary} 中不会出现人才个人资料。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigInvocationAudit.class)
public class AigInvocationAuditVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID
     */
    private Long auditId;

    /**
     * 调用链ID
     */
    private String traceId;

    /**
     * 业务能力编码
     */
    private String capabilityCode;

    /**
     * 业务能力名称（展示用，由服务层按 capabilityCode 回填）
     */
    private String capabilityName;

    /**
     * 调用人用户ID
     */
    private Long callerId;

    /**
     * 调用人账号
     */
    private String callerName;

    /**
     * 本次数据等级（字典 aig_data_level）
     */
    private String dataLevel;

    /**
     * 本次数据等级标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "dataLevel", other = "aig_data_level")
    private String dataLevelLabel;

    /**
     * 实际使用的模型ID
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
     * 部署类型（字典 aig_deployment_type）
     */
    private String deploymentType;

    /**
     * 部署类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "deploymentType", other = "aig_deployment_type")
    private String deploymentTypeLabel;

    /**
     * 是否外发（Y是 N否）
     */
    private String externalCall;

    /**
     * 命中的路由策略摘要
     */
    private String policyHit;

    /**
     * 输入摘要哈希
     */
    private String inputHash;

    /**
     * 输入摘要（仅审计等级允许时写入，不含受限内容）
     */
    private String inputSummary;

    /**
     * 输出引用
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
