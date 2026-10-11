package org.dromara.content.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.enums.AigScenarioReportOutcomeEnum;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpTask;
import org.dromara.content.enums.ContentTaskStatusEnum;
import org.dromara.content.mapper.CpTaskMapper;
import org.springframework.stereotype.Component;

/**
 * 内容任务 → 平台任务的回执（增量 16）。
 *
 * <h3>口径（阶段1A 可达状态里选出来的，可复核）</h3>
 * <ul>
 *     <li>{@code READY} / {@code CONDITIONAL_READY} → <b>SUCCEEDED</b>：资料已就绪、内容链路已接手，
 *         平台任务的"交给既有链路"这件事到此完成（<b>不等于</b>内容交付完成——阶段1A 还没有交付态）；</li>
 *     <li>{@code PENDING_CONFIRM} → <b>FAILED</b>：快照给的资料不足以开工，如实失败并带闸门原因
 *         （否则平台会一直等一个永远不会开工的任务）；</li>
 *     <li>其它（{@code DRAFT}/{@code PARSING}）→ <b>不上报</b>：作业还在跑。</li>
 * </ul>
 * <p>阶段1B 出现 {@code CONFIRMED}/{@code DELIVERED} 后，这张映射要重新评审——
 * 到那时"交接完成"应当改成"交付完成"。</p>
 *
 * <h3>为什么吞异常只记日志</h3>
 * <p>本方法在内容侧的写事务里被调用。回执失败（平台侧异常）不该让一次合法的内容状态变更回滚——
 * 那会把"回执通道抖了一下"放大成"内容任务状态没落库"。失败会 WARN 留痕，
 * 平台任务随后由既有清扫按 TIMEOUT 处理（正好是"没收到回执"的信号）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentScenarioReporter {

    private final CpTaskMapper taskMapper;
    private final IAigScenarioReportService reportService;

    /**
     * 内容任务状态变化后调用；只有"由派发创建"的任务（{@code platform_task_id} 非空）才回报。
     *
     * @param contentTaskId 内容任务ID
     */
    public void reportIfDispatched(Long contentTaskId) {
        if (contentTaskId == null) {
            return;
        }
        CpTask task = taskMapper.selectById(contentTaskId);
        if (task == null || task.getPlatformTaskId() == null) {
            return;
        }
        ContentTaskStatusEnum status = ContentTaskStatusEnum.find(task.getStatus());
        AigScenarioReportOutcomeEnum outcome;
        String message;
        if (status == ContentTaskStatusEnum.READY || status == ContentTaskStatusEnum.CONDITIONAL_READY) {
            outcome = AigScenarioReportOutcomeEnum.SUCCEEDED;
            message = "内容任务已接手并可作业（" + task.getTaskNo() + "，contentTaskId=" + contentTaskId + "）";
        } else if (status == ContentTaskStatusEnum.PENDING_CONFIRM) {
            outcome = AigScenarioReportOutcomeEnum.FAILED;
            message = StringUtils.blankToDefault(task.getBlockReason(), "内容闸门未通过：资料不足（待补料）");
        } else {
            return;
        }
        AigScenarioReportBo bo = new AigScenarioReportBo();
        bo.setPlatformTaskId(task.getPlatformTaskId());
        bo.setOutcome(outcome.getCode());
        bo.setExternalRef(String.valueOf(contentTaskId));
        bo.setMessage(message);
        try {
            reportService.report(bo);
        } catch (Exception e) {
            log.warn("场景回执失败（内容任务状态已落库，平台任务可由清扫兜底）：platformTaskId={}, "
                    + "contentTaskId={}, outcome={}, 异常={}",
                task.getPlatformTaskId(), contentTaskId, outcome.getCode(), e.getClass().getSimpleName());
        }
    }

}
