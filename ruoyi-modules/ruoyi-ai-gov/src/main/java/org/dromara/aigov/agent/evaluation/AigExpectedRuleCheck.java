package org.dromara.aigov.agent.evaluation;

import java.util.List;
import java.util.Map;

/**
 * 判据求值结论（设计 §13.2）。
 *
 * @param machineCheckable 用例是否声明了机器判据（{@code false} = 只有人工 Rubric）
 * @param passed           机器判据是否全部通过
 * @param failures         逐条失败原因（已标注是哪条判据）
 * @param detail           打分明细（落 {@code aig_evaluation_run.score_json}）
 * @author ai-gov
 */
public record AigExpectedRuleCheck(
    boolean machineCheckable,
    boolean passed,
    List<String> failures,
    Map<String, Object> detail
) {

    /**
     * 把失败原因压成一行摘要（落 {@code remark varchar(500)}；完整明细在 score_json）。
     *
     * @param maxLength 最大长度
     * @return 摘要；无失败时返回 null
     */
    public String failureSummary(int maxLength) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String failure : failures) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            if (sb.length() + failure.length() > maxLength) {
                sb.append("等 ").append(failures.size()).append(" 项不通过");
                break;
            }
            sb.append(failure);
        }
        return sb.length() > maxLength ? sb.substring(0, maxLength) : sb.toString();
    }

}
