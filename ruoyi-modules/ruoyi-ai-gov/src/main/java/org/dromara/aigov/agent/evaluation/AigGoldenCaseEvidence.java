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
 * @param adminCaseCodes  其中「结论由管理员人工评测录入」（{@code executed_by=ADMIN}）的用例编码。
 *                        门槛对两种来源一视同仁（都认 PASS），但<b>来源必须看得见</b>——
 *                        机器结论的可信度来自"平台判据在同样输入上判过了"，人工结论的可信度
 *                        来自"一个人看了并签了字"。把这条带在证据里，评审才不用逐条去翻运行明细。
 * @author ai-gov
 */
public record AigGoldenCaseEvidence(
    boolean satisfied,
    List<String> declaredCaseCodes,
    Map<String, String> caseVerdicts,
    String reason,
    List<String> adminCaseCodes
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
     * 构造「不满足」结论（无人工来源信息）。
     *
     * @param declared  声明的用例
     * @param verdicts  逐用例结论
     * @param reason    原因
     * @return 结论
     */
    public static AigGoldenCaseEvidence blocked(List<String> declared, Map<String, String> verdicts,
                                                String reason) {
        return new AigGoldenCaseEvidence(false, declared,
            verdicts == null ? Map.of() : new LinkedHashMap<>(verdicts), reason, List.of());
    }

    /**
     * 构造「满足」结论（无人工来源信息）。
     *
     * @param declared 声明的用例
     * @param verdicts 逐用例结论
     * @return 结论
     */
    public static AigGoldenCaseEvidence satisfied(List<String> declared, Map<String, String> verdicts) {
        return new AigGoldenCaseEvidence(true, declared,
            verdicts == null ? Map.of() : new LinkedHashMap<>(verdicts), null, List.of());
    }

    /**
     * 附上「结论由管理员人工评测录入」的用例编码。
     *
     * <p>做成 wither 而不是往上面两个工厂加入参：调用点有十几处（含既有测试），
     * 而这件事只有 {@code goldenCaseEvidence} 一处知道。加参数会把"谁产出的"这个问题
     * 塞给每一个只需要"过没过"的调用方。</p>
     *
     * @param adminCaseCodes 人工录入的用例编码（空则原样返回）
     * @return 新的证据对象
     */
    public AigGoldenCaseEvidence withAdminCases(List<String> adminCaseCodes) {
        if (adminCaseCodes == null || adminCaseCodes.isEmpty()) {
            return this;
        }
        return new AigGoldenCaseEvidence(satisfied, declaredCaseCodes, caseVerdicts, reason,
            List.copyOf(adminCaseCodes));
    }

    /**
     * 是否有用例的结论来自人工录入。
     *
     * @return 有则 true
     */
    public boolean hasAdminProducedCases() {
        return adminCaseCodes != null && !adminCaseCodes.isEmpty();
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
