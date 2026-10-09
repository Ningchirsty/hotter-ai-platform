package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 人工评测录入入参（平台没有该对象的评测执行器时，由管理员产出证据）。
 *
 * <p><b>它解决的问题</b>：{@code runEvaluation} 要求目标对象有 {@code IAigEvaluationSubject}
 * 实现。而执行器由业务模块按需注册，平台侧的四个实现只认 {@code creative_*} 几个 Agent 编码。
 * 于是 Package 安装带入的第三方 Agent、以及 Package 版本本身，<b>结构上取不到任何评测结论</b>，
 * 而 {@code SANDBOX_TESTED → CANDIDATE} 又必须拿得出「黄金用例通过」的证据 ⇒ 它们永远到不了
 * 灰度。本入参是那条唯一可行路径：<b>管理员按用例给出结论，平台如实登记为「人工产出」。</b></p>
 *
 * <p><b>三条不放松的约束</b>（与机器评测完全一致，只是换了个产出方）：</p>
 * <ol>
 *     <li>只对 {@code SANDBOX_TESTED} 的版本取证——证据必须产生在门槛要求它的那个阶段；</li>
 *     <li>用例集合必须<b>等于</b>版本声明的黄金用例集合——能挑着录，就能用最容易过的用例
 *         换一个「黄金用例通过」；</li>
 *     <li>每条用例都要给出 PASS/FAIL，<b>不许留空</b>——空着等于"这条我没看"，
 *         而"没看"不能算通过。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Data
public class AigEvaluationManualRunBo {

    /**
     * 评测对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    private String targetType;

    /**
     * 评测对象版本ID
     */
    private Long targetVersionId;

    /**
     * 逐用例结论（必须与版本声明的黄金用例集合一一对应）
     */
    private List<AigEvaluationManualCaseBo> cases;

    /**
     * <b>评测方法与依据（必填）</b>：在哪个环境、用什么输入、按什么标准看的。
     *
     * <p>这一项不是客套：机器评测的可信度来自"判据在同样输入上判过了"，
     * 人工评测的可信度<b>只能</b>来自这句话——写不出方法与依据的人工 PASS，
     * 在评审眼里就是一句主张，不是一条证据。</p>
     */
    private String method;

    /**
     * 操作人（管理员；录入门槛与"能不能跑评测"同一权限，不由请求方自报身份）
     */
    private Long operatorId;

    /**
     * 平台侧是否发生了外部调用（Y/N；默认 N）。
     *
     * <p>平台自己没有外呼。若管理员是在外部环境（真机/付费模型）跑出来的，
     * 应如实填 Y，并在 {@link #method} 里写明——否则成本口径会把一次真实外呼记成零。</p>
     */
    private String externalCall;

    /**
     * 本次评测的实际成本（可空 = 未上报）。
     *
     * <p><b>刻意可空</b>：算不出成本时留空，不得用 0 冒充已知成本——0 的含义是
     * "确定没花钱"（确定性引擎），不是"不知道"。</p>
     */
    private BigDecimal costAmount;

    /**
     * 备注（追加写入运行的 remark）
     */
    private String remark;

    /**
     * 单条用例的人工结论。
     */
    @Data
    public static class AigEvaluationManualCaseBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 黄金用例编码
         */
        private String caseCode;

        /**
         * 结论（只接受 PASS / FAIL）
         */
        private String verdict;

        /**
         * 该条的证据引用（报告链接/截图/工单号，可空但建议填）
         */
        private String evidenceRef;

        /**
         * 该条的说明（可空）
         */
        private String note;

    }

}
