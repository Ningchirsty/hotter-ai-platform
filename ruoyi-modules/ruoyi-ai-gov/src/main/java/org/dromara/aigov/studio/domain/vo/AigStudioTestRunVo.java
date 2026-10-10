package org.dromara.aigov.studio.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 训练台测试调用结果。
 *
 * <p><b>{@link #outputTruncated} 是刻意暴露的</b>：页面只拿到一段预览，
 * 若不说清楚"这是截断的"，人会以为模型只输出了这么点。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioTestRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 证据链ID（{@code aig_studio_execution_link.link_id}）
     */
    private Long linkId;

    /**
     * 草稿ID
     */
    private Long draftId;

    /**
     * 被测试的修订号（证明测的是哪一版内容）
     */
    private Integer revision;

    /**
     * 被测试内容的内容哈希
     */
    private String contentHash;

    /**
     * 测试状态（{@code AigStudioTestStatusEnum} 的 code）
     */
    private String testStatus;

    /**
     * 输出预览（可能被截断）
     */
    private String output;

    /**
     * 输出是否被截断
     */
    private Boolean outputTruncated;

    /**
     * 输出摘要哈希（sha256；同一输入不同输出时靠它核对）
     */
    private String resultDigest;

    /**
     * 调用链追踪ID（关联 aig_invocation_audit.trace_id）
     */
    private String traceId;

    /**
     * 实际选中的模型编码（路由结果，不是草稿声明的）
     */
    private String modelKey;

    /**
     * 部署类型（LOCAL/EXTERNAL…）
     */
    private String deploymentType;

    /**
     * 本次是否发生外部调用
     */
    private Boolean externalCall;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 错误码（成功为空）
     */
    private String errorCode;

    /**
     * 失败原因 / 策略说明
     */
    private String reason;

    /**
     * 策略命中说明（做了什么判定）
     */
    private String policyHits;

}
