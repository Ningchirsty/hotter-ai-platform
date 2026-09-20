package org.dromara.content.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpAsyncJob;
import org.dromara.content.enums.ContentAsyncJobStatusEnum;
import org.dromara.content.enums.ContentAsyncJobTypeEnum;
import org.dromara.content.mapper.CpAsyncJobMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内容生产异步作业执行器（应用内，基线 B9）。
 *
 * <p><b>不引入 SnailJob</b>：阶段1A 的作业是「读文件 + 规则比对」，属于 IO/CPU 混合的短任务，
 * 引入分布式调度框架会带来额外的部署与运维负担。作业状态落 {@code cp_async_job}，
 * 前端据它显示进度与失败原因。</p>
 *
 * <p><b>并发度刻意设为 1</b>：本地解析会把文件整份读入内存，与其它模块（视频生成等）
 * 共存时，多并发容易触发内存压力。串行化换来的是可预测的内存占用。</p>
 *
 * <p><b>为什么接收 {@link Runnable} 而不是直接依赖业务服务</b>：那样会形成
 * 「TaskService → 执行器 → TaskService」的循环依赖。这里只负责调度与作业记账，
 * 业务逻辑由调用方以 lambda 传入。</p>
 *
 * @author content
 */
@Slf4j
@Component
public class ContentAsyncExecutor {

    /**
     * 单线程执行器（见类注释：并发度 1 是刻意的）
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "content-async-job");
        t.setDaemon(true);
        return t;
    });

    /**
     * 作业 Mapper
     */
    private final CpAsyncJobMapper jobMapper;

    /**
     * 构造。
     *
     * @param jobMapper 作业 Mapper
     */
    public ContentAsyncExecutor(CpAsyncJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    /**
     * 提交一个作业。
     *
     * @param taskId   任务ID
     * @param jobType  作业类型
     * @param work     实际工作（不得抛出异常；抛出的异常会被记为作业失败）
     * @return 作业ID
     */
    public Long submit(Long taskId, ContentAsyncJobTypeEnum jobType, Runnable work) {
        CpAsyncJob job = new CpAsyncJob();
        job.setTaskId(taskId);
        job.setJobType(jobType.getCode());
        job.setStatus(ContentAsyncJobStatusEnum.QUEUED.getCode());
        job.setProgress(0);
        jobMapper.insert(job);

        final Long jobId = job.getJobId();
        executor.submit(() -> {
            markRunning(jobId);
            try {
                work.run();
                markFinished(jobId, ContentAsyncJobStatusEnum.SUCCESS, null);
            } catch (Exception e) {
                // 作业失败不得影响其它作业，也不得让异常逃逸到线程池
                log.error("内容异步作业失败, jobId={}, taskId={}, jobType={}, exception={}",
                    jobId, taskId, jobType.getCode(), e.getClass().getSimpleName());
                markFinished(jobId, ContentAsyncJobStatusEnum.FAILED,
                    StringUtils.blankToDefault(e.getMessage(), "作业执行失败"));
            }
        });
        return jobId;
    }

    /**
     * 标记作业开始。
     *
     * @param jobId 作业ID
     */
    private void markRunning(Long jobId) {
        CpAsyncJob update = new CpAsyncJob();
        update.setJobId(jobId);
        update.setStatus(ContentAsyncJobStatusEnum.RUNNING.getCode());
        update.setProgress(10);
        update.setStartedAt(LocalDateTime.now());
        jobMapper.updateById(update);
    }

    /**
     * 标记作业结束。
     *
     * @param jobId   作业ID
     * @param status  终态
     * @param message 失败原因（成功传 null）
     */
    private void markFinished(Long jobId, ContentAsyncJobStatusEnum status, String message) {
        CpAsyncJob update = new CpAsyncJob();
        update.setJobId(jobId);
        update.setStatus(status.getCode());
        update.setProgress(100);
        update.setMessage(message);
        update.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(update);
    }

}
