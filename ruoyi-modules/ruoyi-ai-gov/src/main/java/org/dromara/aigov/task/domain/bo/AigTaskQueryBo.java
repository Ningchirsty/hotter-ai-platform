package org.dromara.aigov.task.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.common.core.validate.QueryGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 任务查询条件。
 *
 * <p>时间范围兼容两种下发：显式 {@code beginTime/endTime} 与平台惯例的
 * {@code params[beginTime]}/{@code params[endTime]}（前端 {@code addDateRange}），
 * 与审计查询同一口径。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTask.class, reverseConvertGenerate = false)
public class AigTaskQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 业务域（CONTENT/CREATIVE/TALENT…）
     */
    private String projectType;

    /**
     * 业务对象ID
     */
    private Long projectId;

    /**
     * 状态
     */
    private String status;

    /**
     * 业务能力编码
     */
    private String capabilityCode;

    /**
     * 场景编码
     */
    private String scenarioCode;

    /**
     * 数据等级
     */
    private String dataLevel;

    /**
     * 是否外发（Y/N）
     */
    private String externalCall;

    /**
     * Provider 编码
     */
    private String providerCode;

    /**
     * 调用链追踪ID
     */
    private String traceId;

    /**
     * 提交人
     */
    private Long createBy;

    /**
     * 人工复核状态
     */
    private String reviewStatus;

    /**
     * 任务号（精确）
     */
    private String taskNo;

    /**
     * 起始时间
     */
    private LocalDateTime beginTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 平台惯例的时间范围载体
     */
    private Map<String, Object> params;

}
