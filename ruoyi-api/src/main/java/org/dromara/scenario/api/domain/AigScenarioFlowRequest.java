package org.dromara.scenario.api.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景任务派发请求（aigov → 业务域）。
 *
 * <p><b>只带执行必需的事实</b>：平台任务身份、场景引用、数据等级/业务域、输入快照与提交人。
 * 刻意不带平台的治理配置（路由策略、模型、配额）——那些是 aigov 的判断，各域不需要也不该看到。</p>
 *
 * @author ai-gov
 */
@Data
public class AigScenarioFlowRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 平台任务ID（各域回执要带上它，便于把两边的记录对上）
     */
    private Long taskId;

    /**
     * 平台任务编号（对外展示）
     */
    private String taskNo;

    /**
     * 场景编码
     */
    private String scenarioCode;

    /**
     * 场景版本（{@code scenario://code@version} 里的 version；执行的是确认过的这一版）
     */
    private String scenarioVersion;

    /**
     * 适配器编码（本次由哪条链路执行；即 {@code AigScenarioFlowPort#adapter()}）
     */
    private String adapter;

    /**
     * 数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     */
    private String dataLevel;

    /**
     * 业务域（creative/content/video…）
     */
    private String projectType;

    /**
     * 业务项目ID（可空）
     */
    private Long projectId;

    /**
     * 输入快照（任务的不可变输入；各域据此开工）
     */
    private String snapshotJson;

    /**
     * 提交人用户ID（各域据此做自己的归属/权限校验）
     */
    private Long requesterId;

    /**
     * 提交人用户名（可空）
     */
    private String requesterName;

}
