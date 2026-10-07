package org.dromara.aigov.task.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 执行 AI 任务结果。
 *
 * <p>同时给出「任务结局」与「调用细节」：任务状态说明这件事的归宿，
 * traceId / modelKey / invoker 说明它是怎么跑出来的。分开看都解释不清一次执行。</p>
 *
 * <p><b>输出原样返回给调用方，不代为落库</b>：治理层不持有资产存储，也不该为此反向依赖业务模块
 * （出图结果是含 base64 的信封，落成资产是业务域的事）。任务结果的候选记录由调用方
 * 用 {@code IAigTaskService#recordResult} 写入——这也让「写候选」与「选候选」保持在同一层。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskExecuteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 执行后的任务状态
     */
    private String status;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 调用链ID（关联 aig_invocation_audit，一条 traceId 串起任务与逐次审计）
     */
    private String traceId;

    /**
     * 实际使用的模型键
     */
    private String modelKey;

    /**
     * 部署类型
     */
    private String deploymentType;

    /**
     * 实际执行的调用器
     */
    private String invoker;

    /**
     * 是否发生外部调用
     */
    private boolean externalCall;

    /**
     * 模型输出（成功时；原样，不落库）
     */
    private String output;

    /**
     * 失败原因（失败时）
     */
    private String reason;

    /**
     * 错误分类编码（失败时；上层据此决定重试/转人工/停在失败）
     */
    private String errorCode;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

}
