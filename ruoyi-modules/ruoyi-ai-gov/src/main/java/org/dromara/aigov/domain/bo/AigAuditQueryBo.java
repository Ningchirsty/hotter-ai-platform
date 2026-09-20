package org.dromara.aigov.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 调用审计查询业务对象 aig_invocation_audit
 * <p>审计表为<b>只读追加型</b>；本对象仅承载查询条件。</p>
 * <p>时间范围沿用平台惯例：前端经 {@code addDateRange} 下发
 * {@code params[beginTime]} / {@code params[endTime]}，因此 {@link #params} 必须保留。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAuditQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调用链ID
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
     * 调用人账号（模糊匹配）
     */
    private String callerName;

    /**
     * 本次数据等级（PUBLIC/INTERNAL/RESTRICTED）
     */
    private String dataLevel;

    /**
     * 实际使用的模型ID
     */
    private Long modelId;

    /**
     * 模型键（精确匹配，前端按此过滤）
     */
    private String modelKey;

    /**
     * 是否外发（Y是 N否）
     */
    private String externalCall;

    /**
     * 结果（0成功 1失败）
     */
    private String result;

    /**
     * 人工结论（PENDING/ACCEPTED/REJECTED/NOT_REQUIRED）
     */
    private String manualDecision;

    /**
     * 调用时间起（含）；也可由 {@code params[beginTime]} 下发
     */
    private LocalDateTime beginTime;

    /**
     * 调用时间止（含）；也可由 {@code params[endTime]} 下发
     */
    private LocalDateTime endTime;

    /**
     * 请求参数（平台通用扩展位：beginTime / endTime 时间范围）
     */
    private Map<String, Object> params;

}
