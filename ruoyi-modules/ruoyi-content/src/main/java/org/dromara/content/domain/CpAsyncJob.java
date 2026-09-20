package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异步作业对象 cp_async_job
 *
 * <p>阶段1A 用应用内执行器（基线 B9），作业状态落这张表，前端据它显示进度与失败原因。
 * 该表是纯运行态记录，因此<b>不继承 BaseEntity</b>、无逻辑删除——作业记录保留即可，
 * 便于排查「解析卡住了吗」。</p>
 *
 * @author content
 */
@Data
@TableName("cp_async_job")
public class CpAsyncJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作业ID
     */
    @TableId(value = "job_id")
    private Long jobId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 作业类型（PARSE/PRECHECK/PACKAGE，见 ContentAsyncJobTypeEnum）
     */
    private String jobType;

    /**
     * 状态（QUEUED/RUNNING/SUCCESS/FAILED，见 ContentAsyncJobStatusEnum）
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
