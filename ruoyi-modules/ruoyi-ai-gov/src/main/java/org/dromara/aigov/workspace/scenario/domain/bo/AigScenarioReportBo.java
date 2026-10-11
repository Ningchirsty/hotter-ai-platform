package org.dromara.aigov.workspace.scenario.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景任务回执入参（业务域 → 平台；增量 16）。
 *
 * @author ai-gov
 */
@Data
public class AigScenarioReportBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 平台任务ID（派发时给业务域的那个）
     */
    private Long platformTaskId;

    /**
     * 结论（{@code AigScenarioReportOutcomeEnum} 的 code）
     */
    private String outcome;

    /**
     * 业务域自己的任务/作业引用（可空，便于两边对账）
     */
    private String externalRef;

    /**
     * 可读说明（失败时是原因；不得含密钥与受限原文）
     */
    private String message;

}
