package org.dromara.ai.video.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.enums.AigScenarioReportOutcomeEnum;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 视频任务 → 平台任务的回执（增量 17，与内容域的 {@code ContentScenarioReporter} 同形）。
 *
 * <h3>口径</h3>
 * <ul>
 *     <li>{@code SUCCEEDED} → <b>SUCCEEDED</b>：成片已落盘并通过实测断言；</li>
 *     <li>{@code FAILED} / {@code TIMEOUT} / {@code CANCELED} → <b>FAILED</b>：如实失败并带原因
 *         （否则平台任务会一直等一个永远不会完成的作业）；</li>
 *     <li>{@code QUEUED} / {@code RUNNING} → <b>不上报</b>：作业还在跑（交接已受理，结论未出）。</li>
 * </ul>
 *
 * <h3>为什么吞异常只记日志</h3>
 * <p>本方法在视频侧的状态流转之后被调用。回执失败不该影响视频任务自己的状态落库——
 * 那会把"回执通道抖了一下"放大成"视频任务状态没落库"。失败只 WARN 留痕，
 * 平台任务随后由既有清扫按 TIMEOUT 处理（正好是"没收到回执"的信号）。</p>
 *
 * <h3>为什么用 {@link ObjectProvider}</h3>
 * <p>视频模块可以在没有 aigov 的部署里单独启用（例如只跑视频链路的联调环境）。
 * 取不到回执服务时安静跳过，而不是因为少一个协作 Bean 让整个应用起不来。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "video", name = "enabled", havingValue = "true")
public class VideoScenarioReporter {

    private final ObjectProvider<IAigScenarioReportService> reportServiceProvider;

    /**
     * 视频任务状态变化后调用；只有"由派发创建"的任务（{@code platform_task_id} 非空）才回报。
     *
     * @param platformTaskId 平台任务ID（{@code video_task.platform_task_id}）
     * @param videoTaskId    视频任务ID（回执里的 {@code externalRef}）
     * @param status         视频任务当前状态
     * @param detail         可读说明（失败原因；不得含密钥与受限原文）
     */
    public void report(Long platformTaskId, long videoTaskId, VideoTaskStatus status, String detail) {
        if (platformTaskId == null || status == null) {
            return;
        }
        AigScenarioReportOutcomeEnum outcome;
        if (status == VideoTaskStatus.SUCCEEDED) {
            outcome = AigScenarioReportOutcomeEnum.SUCCEEDED;
        } else if (status.isTerminal()) {
            // FAILED / TIMEOUT / CANCELED：都是"作业没有产出可交付结果"
            outcome = AigScenarioReportOutcomeEnum.FAILED;
        } else {
            // QUEUED / RUNNING：还没结论，不上报
            return;
        }

        IAigScenarioReportService reportService = reportServiceProvider.getIfAvailable();
        if (reportService == null) {
            log.debug("场景回执服务不可用，跳过视频任务回执：platformTaskId={}", platformTaskId);
            return;
        }

        AigScenarioReportBo bo = new AigScenarioReportBo();
        bo.setPlatformTaskId(platformTaskId);
        bo.setOutcome(outcome.getCode());
        bo.setExternalRef(String.valueOf(videoTaskId));
        bo.setMessage(detail == null || detail.isBlank()
            ? "视频任务 " + videoTaskId + " 状态：" + status.name()
            : detail);
        try {
            reportService.report(bo);
        } catch (Exception e) {
            log.warn("场景回执失败（视频任务状态已落库，平台任务可由清扫兜底）：platformTaskId={}, "
                    + "videoTaskId={}, outcome={}, 异常={}",
                platformTaskId, videoTaskId, outcome.getCode(), e.getClass().getSimpleName());
        }
    }

}
