package org.dromara.aigov.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.feedback.metrics.AigOperationsMetrics;
import org.dromara.aigov.feedback.service.IAigOperationsMetricsService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskResult;
import org.dromara.aigov.task.enums.AigCandidateStatusEnum;
import org.dromara.aigov.task.enums.AigTaskPolicyResultEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.mapper.AigTaskResultMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运营指标聚合实现（设计 §6.9）。
 *
 * <p><b>本轮能算的与不能算的（写在代码里，避免读者以为指标是全的）</b>：</p>
 * <ul>
 *     <li>能算：完成任务数、累计成果（= 经确认的结果）、首次通过率、返工率、策略阻断；</li>
 *     <li>暂不能算：<b>引用有效率</b>——引用明细目前冻结在任务的 {@code route_snapshot}
 *         （JSON），没有可聚合的列；<b>模型成本</b>——需要审计账本的金额聚合，随审计口径接入；</li>
 *     <li>因此这两项以 0 呈现，而不是用别的数字顶替（顶替出来的比率会看起来正常但完全错误）。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigOperationsMetricsServiceImpl implements IAigOperationsMetricsService {

    /**
     * 结题状态：一次执行真正走完并以成功/复核/拒绝收尾的任务（失败不计入"完成"）
     */
    private static final List<String> COMPLETED_STATUSES = List.of(
        AigTaskStatusEnum.SUCCEEDED.getCode(),
        AigTaskStatusEnum.APPROVED.getCode(),
        AigTaskStatusEnum.REJECTED.getCode());

    private final AigTaskMapper taskMapper;
    private final AigTaskResultMapper taskResultMapper;

    @Override
    public AigOperationsMetrics.Result compute() {
        long completed = count(new LambdaQueryWrapper<AigTask>()
            .in(AigTask::getStatus, COMPLETED_STATUSES));

        long firstPass = count(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getStatus, AigTaskStatusEnum.APPROVED.getCode())
            .le(AigTask::getAttemptNo, 1));

        long rework = count(new LambdaQueryWrapper<AigTask>()
            .gt(AigTask::getAttemptNo, 1));

        long policyBlocked = count(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getPolicyResult, AigTaskPolicyResultEnum.REJECT.getCode()));

        long confirmed = countResults(new LambdaQueryWrapper<AigTaskResult>()
            .eq(AigTaskResult::getCandidateStatus, AigCandidateStatusEnum.APPROVED.getCode()));

        AigOperationsMetrics.Result result = AigOperationsMetrics.compute(new AigOperationsMetrics.Input(
            completed, confirmed, firstPass, rework,
            // 引用有效率与模型成本暂无可聚合数据源，如实置 0（见类注释）
            0L, 0L, policyBlocked, BigDecimal.ZERO));
        log.info("运营指标计算完成: completed={}, confirmed={}, firstPass={}, rework={}, policyBlocked={}",
            completed, confirmed, firstPass, rework, policyBlocked);
        return result;
    }

    private long count(LambdaQueryWrapper<AigTask> wrapper) {
        Long counted = taskMapper.selectCount(wrapper);
        return counted == null ? 0L : counted;
    }

    private long countResults(LambdaQueryWrapper<AigTaskResult> wrapper) {
        Long counted = taskResultMapper.selectCount(wrapper);
        return counted == null ? 0L : counted;
    }

}
