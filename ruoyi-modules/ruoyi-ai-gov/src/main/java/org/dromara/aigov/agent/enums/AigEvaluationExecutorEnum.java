package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测结论的产出方（{@code aig_evaluation_run.executed_by}）。
 *
 * <p><b>为什么必须有这一列，而不是把「谁跑的」写在 remark 里</b>：release 门槛
 * （{@code assertGoldenCaseEvidence}）只认 {@code result_status=PASS}，它不区分这条 PASS
 * 是平台执行器跑出来的，还是管理员人工评测后录入的。两种来源都合法，但<b>它们的可信度
 * 来源不同</b>——一个是"平台的判据在同样输入上判过了"，一个是"一个人看了并签了字"。
 * 评审必须能一眼分开，否则人工结论会伪装成机器结论。</p>
 *
 * <p>放进独立列的另一个原因：remark 是自由文本，会被人工复核<b>追加</b>
 * （见 {@code AigEvaluationServiceImpl#reviewRun}）——把机器可判的性质放在会被追加的文本里，
 * 等于放在一个会变的地方。列一旦写入不再变化。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigEvaluationExecutorEnum {

    /**
     * 平台评测执行器跑出来的（{@code IAigEvaluationSubject.execute}，判据由平台求值）
     */
    PLATFORM("PLATFORM", "平台执行器跑出来的"),

    /**
     * 管理员人工评测后录入的（平台没有该对象的执行器时，这是唯一能取到证据的途径）
     */
    ADMIN("ADMIN", "管理员人工评测后录入");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否属于「人工录入」。
     *
     * @return 人工录入返回 true
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigEvaluationExecutorEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigEvaluationExecutorEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
