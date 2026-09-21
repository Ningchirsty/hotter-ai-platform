package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型连通性测试结果。
 * <p>
 * <b>安全约定</b>：响应与日志都<b>不得</b>出现密钥、完整请求体、完整响应体；
 * {@code endpointHost} 只保留主机名（便于判断打到了哪个地址），{@code detail} 截断到固定长度。
 * </p>
 *
 * @author ai-gov
 */
@Data
public class AigModelTestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否连通
     */
    private Boolean ok;

    /**
     * 探测方式：LOCAL_ENGINE / OPENAI_COMPATIBLE / SNAIL_AI / UNSUPPORTED
     */
    private String probe;

    /**
     * 目标地址主机（不含路径与查询串，便于排查打错地址）
     */
    private String endpointHost;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 结论摘要（给用户看的一句话）
     */
    private String message;

    /**
     * 细节（截断后的原因，例如 HTTP 状态码与错误摘要）
     */
    private String detail;

    /**
     * 测试后写入治理表的健康状态：HEALTHY / UNHEALTHY
     */
    private String healthStatus;

    /**
     * 测试时间
     */
    private LocalDateTime checkedAt;
}
