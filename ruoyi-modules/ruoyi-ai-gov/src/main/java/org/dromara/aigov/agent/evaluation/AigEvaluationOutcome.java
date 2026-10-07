package org.dromara.aigov.agent.evaluation;

import java.math.BigDecimal;

/**
 * 评测执行结果（设计 §13.2）。
 *
 * <p>{@code costKnown=false} 表示「本次没能算出成本」，这<u>不是</u>「成本为 0」：
 * 用例声明了成本范围时，平台无法用未知成本去证明「落在范围内」，因此会判不通过
 * （{@code aig_evaluation_case} 的列注释同样写着「算不出留空，禁止填 0 冒充」）。
 * 确定性的本地引擎可以如实上报 {@code costKnown=true, costAmount=0}——「真的是零成本」
 * 与「算不出来」必须能分开。</p>
 *
 * @param outputJson   实际产出（JSON 文本；判据按 JSON 路径求值）
 * @param costKnown    本次是否算出了成本
 * @param costAmount   实际成本（{@code costKnown=false} 时忽略该值）
 * @param latencyMs    端到端耗时（毫秒；算不出留空）
 * @param providerId   实际使用的 Provider（跟着实际候选走，便于按供应商复盘）
 * @param modelCode    实际使用的模型编码
 * @param externalCall 本次是否发生外部调用
 * @param traceId      调用链ID（关联 {@code aig_invocation_audit.trace_id}）
 * @author ai-gov
 */
public record AigEvaluationOutcome(
    String outputJson,
    boolean costKnown,
    BigDecimal costAmount,
    Long latencyMs,
    Long providerId,
    String modelCode,
    boolean externalCall,
    String traceId
) {
}
