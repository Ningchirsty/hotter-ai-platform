package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.content.domain.CpAsyncJob;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异步作业视图对象 cp_async_job
 *
 * @author content
 */
@Data
@AutoMapper(target = CpAsyncJob.class)
public class CpAsyncJobVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作业ID
     */
    private Long jobId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 作业类型
     */
    private String jobType;

    /**
     * 状态
     */
    private String status;

    /**
     * 进度 0-100
     */
    private Integer progress;

    /**
     * 失败原因（用户可读）
     */
    private String message;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

}
