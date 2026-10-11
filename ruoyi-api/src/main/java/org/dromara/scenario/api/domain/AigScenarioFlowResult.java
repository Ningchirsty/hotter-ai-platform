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
