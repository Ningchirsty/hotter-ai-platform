package org.dromara.scenario.api.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景任务派发结果（业务域 → aigov）。
 *
 * <p><b>未受理不是异常，是结论</b>：某域可能因为"快照读不懂""数据等级不允许"等原因拒绝受理。
 * 这种"业务拒绝"要如实回给任务层（任务落失败并写明原因），而不是抛异常让上层去猜。</p>
 *
 * @author ai-gov
 */
@Data
public class AigScenarioFlowResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否已受理（受理后由本域驱动后续执行）
     */
    private boolean accepted;

    /**
     * 本域自己的任务/作业引用（受理时给出，用于回执与排查）
     */
    private String externalRef;

    /**
     * 未受理时的可读原因（不得含密钥与受限原文）
     */
    private String message;

    /**
     * 域是否已在**受理时**就完成了交接（不需要后续回执）。
     *
     * <p>默认 {@code false}：域内是长跑作业，平台任务保持 RUNNING，由域通过回执服务收尾
     * （内容域、视频域都是这样）。少数域的"把作业建出来"就等于交接完成
     * （例：创作域建出一条创意项目，剩下的是设计部在项目里干活），由域把它置 {@code true}；
     * 任务层会在记录派发事实后**立即**把平台任务收尾为 SUCCEEDED。</p>
     *
     * <p>这是<b>域自己声明</b>的事实，平台不替它假设——没有这个声明，平台绝不自行置 SUCCEEDED。</p>
     */
    private boolean handoffComplete;

    /**
     * 受理。
     *
     * @param externalRef 本域任务/作业引用
     * @return 结果
     */
    public static AigScenarioFlowResult accepted(String externalRef) {
        AigScenarioFlowResult result = new AigScenarioFlowResult();
        result.setAccepted(true);
        result.setExternalRef(externalRef);
        return result;
    }

    /**
     * 受理，且声明"交接已在受理时完成"（平台任务可立即收尾）。
     *
     * @param externalRef 本域任务/作业引用
     * @return 结果
     */
    public static AigScenarioFlowResult acceptedWithHandoffComplete(String externalRef) {
        AigScenarioFlowResult result = accepted(externalRef);
        result.setHandoffComplete(true);
        return result;
    }

    /**
     * 未受理（业务拒绝）。
     *
     * @param message 可读原因
     * @return 结果
     */
    public static AigScenarioFlowResult rejected(String message) {
        AigScenarioFlowResult result = new AigScenarioFlowResult();
        result.setAccepted(false);
        result.setMessage(message);
        return result;
    }

}
