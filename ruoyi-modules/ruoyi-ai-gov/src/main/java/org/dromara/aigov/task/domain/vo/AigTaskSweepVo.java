package org.dromara.aigov.task.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次调度扫描的结果。
 *
 * <p>必须把「扫描了几条、推进了几条、因为并发冲突跳过了几条、失败了几条」分开报。
 * 只报「处理了 N 条」的话，多实例部署里「两台都在扫、每台都以为自己处理了」
 * 这种重复推进会被掩盖成正常数字。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskSweepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 命中的待重试任务数
     */
    private int retryCandidate;

    /**
     * 实际重排（RETRY_WAIT → QUEUED）成功数
     */
    private int retried;

    /**
     * 命中的超时任务数
     */
    private int timeoutCandidate;

    /**
     * 实际记为失败的超时任务数
     */
    private int timedOut;

    /**
     * 命中的「待策略预检」任务数（{@code DRAFT} + 平台执行）
     */
    private int policyCandidate;

    /**
     * 完成策略预检的任务数（含被判为「拒绝」「转人工」的——它们也算检查过）
     */
    private int policyChecked;

    /**
     * 因乐观锁冲突被跳过的数量（说明有别的实例/请求同时改了同一个任务）
     */
    private int skipped;

    /**
     * 处理失败的任务ID与原因（逐条给出，便于排查个别异常而不是只看总数）
     */
    private List<String> failures = new ArrayList<>();

}
