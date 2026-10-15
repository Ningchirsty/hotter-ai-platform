package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.config.AigSubAgentReconcileProperties;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepKindEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskStepMapper;
import org.dromara.aigov.task.service.IAigSubAgentReconcileService;
import org.dromara.aigov.task.service.IAigSubAgentRunner;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 子 Agent 对账实现（设计 §6.5、§7.6）。
 *
 * <p>一轮：取"执行中的子 Agent 步骤"（限流取前 N 条）→ 按父任务去重 → 逐个对账。
 * <b>单个父任务失败不影响其它父任务</b>：一轮里一条坏数据不该让整轮停摆。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigSubAgentReconcileServiceImpl implements IAigSubAgentReconcileService {

    private final AigTaskStepMapper stepMapper;

    private final IAigSubAgentRunner runner;

    private final AigSubAgentReconcileProperties properties;

    @Override
    public int sweep() {
        List<AigTaskStep> running = stepMapper.selectList(new LambdaQueryWrapper<AigTaskStep>()
            .select(AigTaskStep::getTaskId)
            .eq(AigTaskStep::getKind, AigTaskStepKindEnum.SUB_AGENT.getCode())
            .eq(AigTaskStep::getStatus, AigTaskStepStatusEnum.RUNNING.getCode())
            .orderByAsc(AigTaskStep::getTaskId)
            .last("limit " + Math.max(1, properties.getBatchSize())));
        if (running.isEmpty()) {
            return 0;
        }
        Set<Long> parentTaskIds = new LinkedHashSet<>();
        for (AigTaskStep step : running) {
            if (step.getTaskId() != null) {
                parentTaskIds.add(step.getTaskId());
            }
        }
        int finished = 0;
        for (Long parentTaskId : parentTaskIds) {
            try {
                finished += runner.reconcile(parentTaskId);
            } catch (Exception e) {
                // 单条失败不影响其它：对账是可重复的，下一轮还会再看到它
                log.error("对账父任务失败（继续其它父任务）, parentTaskId={}", parentTaskId, e);
            }
        }
        if (finished > 0) {
            log.info("子 Agent 对账完成：本轮收尾 {} 个父步骤，涉及 {} 个父任务", finished, parentTaskIds.size());
        }
        return finished;
    }

}
