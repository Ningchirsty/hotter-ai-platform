package org.dromara.aigov.agent.evaluation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「黄金用例是否通过」的证据（设计 §5.4 进 STABLE 的前置证据、§13.2）。
 *
 * <p><b>为什么要有这样一个结论对象，而不是让门槛自己查一遍</b>：这条判据要同时回答
 * 「声明了哪些用例」「每条用例最近一次结果如何」「哪一条挡住了」，而写出结论的地方
 * 有两处（评测服务自己、发布推进的门槛校验）。判据写两遍必然走样——一份放行、一份拦住，
 * 谁也不知道以哪份为准。因此只在这里算一次，两处都用它。</p>
 *
 * @param satisfied       是否满足（全部用例最近一次运行 PASS 且人工复核通过）
 * @param declaredCaseCodes 版本声明的黄金用例编码（有序）
 * @param caseVerdicts    逐用例结论（编码 → PASS/FAIL/ERROR/RUNNING/NO_RUN/REVIEW_MANUAL/REVIEW_FAIL）
 * @param reason          不满足时的可读原因（满足时为 null）
 * @author ai-gov
 */
public record AigGoldenCaseEvidence(
    boolean satisfied,
    List<String> declaredCaseCodes,
    Map<String, String> caseVerdicts,
    String reason
) {

    /**
     * 逐用例结论：通过
     */
    public static final String VERDICT_PASS = "PASS";

    /**
     * 逐用例结论：没有任何评测运行
     */
    public static final String VERDICT_NO_RUN = "NO_RUN";

    /**
     * 逐用例结论：待人工复核
     */
    public static final String VERDICT_REVIEW_MANUAL = "REVIEW_MANUAL";

    /**
     * 逐用例结论：人工复核不通过
     */
    public static final String VERDICT_REVIEW_FAIL = "REVIEW_FAIL";

    /**
     * 逐用例结论：有一条比现存最近一次<b>更新</b>的运行被逻辑删除
     *
     * <p>这是「删记录让门槛放行」的痕迹：逻辑删除不会让历史消失，只会让它对普通查询不可见，
     * 而「最近一次运行」会因此回退到更早一次。</p>
     */
    public static final String VERDICT_DELETED_NEWER = "DELETED_NEWER";

    /**
     * 构造「不满足」结论。
     *
     * @param declared  声明的用例
     * @param verdicts  逐用例结论
     * @param reason    原因
     * @return 结论
     */
    public static AigGoldenCaseEvidence blocked(List<String> declared, Map<String, String> verdicts,
                                                String reason) {
        return new AigGoldenCaseEvidence(false, declared,
            verdicts == null ? Map.of() : new LinkedHashMap<>(verdicts), reason);
    }

    /**
     * 构造「满足」结论。
     *
     * @param declared 声明的用例
     * @param verdicts 逐用例结论
     * @return 结论
     */
    public static AigGoldenCaseEvidence satisfied(List<String> declared, Map<String, String> verdicts) {
        return new AigGoldenCaseEvidence(true, declared,
            verdicts == null ? Map.of() : new LinkedHashMap<>(verdicts), null);
    }

    /**
     * 逐用例结论的可读摘要（落错误消息）。
     *
     * @return 例如 {@code case-a=PASS、case-b=FAIL}
     */
    public String verdictSummary() {
        if (caseVerdicts == null || caseVerdicts.isEmpty()) {
            return "（无用例结论）";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : caseVerdicts.entrySet()) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

}
