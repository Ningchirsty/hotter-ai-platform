package org.dromara.aigov.workspace.scenario.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.enums.AigScenarioReportOutcomeEnum;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景任务回执实现（增量 16）。
 *
 * <h3>三条取舍</h3>
 * <ol>
 *     <li><b>只动 RUNNING 的任务</b>：终态任务的重复回执是幂等无操作（返回 false），
 *         不报错——回执会重发、会晚到；对未开始（QUEUED 等）的任务也先不动。</li>
 *     <li><b>{@code RUNNING} 结论不改变状态</b>：它只是进度，平台任务本来就在 RUNNING；
 *         收尾只认 SUCCEEDED/FAILED。</li>
 *     <li><b>认不出的结论直接拒绝</b>：回执是跨模块写操作，不"当成成功"。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigScenarioReportServiceImpl implements IAigScenarioReportService {

    private final IAigTaskService taskService;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean report(AigScenarioReportBo bo) {
        if (bo == null || bo.getPlatformTaskId() == null) {
            throw new ServiceException("场景回执缺少平台任务ID");
        }
        AigScenarioReportOutcomeEnum outcome = AigScenarioReportOutcomeEnum.find(bo.getOutcome());
        if (outcome == null) {
            throw new ServiceException("场景回执结论非法：" + bo.getOutcome());
        }
        AigTask task = taskService.getTask(bo.getPlatformTaskId());
        if (task == null) {
            throw new ServiceException("平台任务不存在：" + bo.getPlatformTaskId());
        }
        if (AigTaskStatusEnum.find(task.getStatus()) != AigTaskStatusEnum.RUNNING) {
            log.info("场景回执忽略（任务不在执行中）：platformTaskId={}, status={}, outcome={}",
                bo.getPlatformTaskId(), task.getStatus(), outcome.getCode());
            return false;
        }
        if (outcome == AigScenarioReportOutcomeEnum.RUNNING) {
            log.info("场景回执进度（不改变状态）：platformTaskId={}, message={}",
                bo.getPlatformTaskId(), StringUtils.substring(bo.getMessage(), 0, 200));
            return false;
        }
        String reason = StringUtils.substring(StringUtils.blankToDefault(bo.getMessage(), outcome.getDesc()), 0, 500);
        if (outcome == AigScenarioReportOutcomeEnum.SUCCEEDED) {
            taskService.transition(bo.getPlatformTaskId(), task.getVersion(), AigTaskStatusEnum.SUCCEEDED,
                "域回执：已完成（externalRef=" + StringUtils.blankToDefault(bo.getExternalRef(), "-") + "）",
                reportSnapshot(outcome, bo));
        } else {
            // 失败分类留空：域内失败不是"可编程的粗分类"，猜一个只会误导重试策略
            taskService.recordFailure(bo.getPlatformTaskId(), task.getVersion(), null, reason);
        }
        log.info("场景回执已收尾平台任务：platformTaskId={}, outcome={}, externalRef={}",
            bo.getPlatformTaskId(), outcome.getCode(), bo.getExternalRef());
        return true;
    }

    /**
     * 回执快照（写进任务路由快照，事后能回答"这个结论是谁给的"）。
     *
     * @param outcome 结论
     * @param bo      回执
     * @return JSON 文本；组装失败返回 null
     */
    private String reportSnapshot(AigScenarioReportOutcomeEnum outcome, AigScenarioReportBo bo) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("report", outcome.getCode());
        snapshot.put("externalRef", bo.getExternalRef());
        snapshot.put("message", StringUtils.substring(bo.getMessage(), 0, 500));
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("组装场景回执快照失败, platformTaskId={}", bo.getPlatformTaskId(), e);
            return null;
        }
    }

}
