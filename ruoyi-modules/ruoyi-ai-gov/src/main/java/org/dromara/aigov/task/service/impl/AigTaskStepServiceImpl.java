package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepKindEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskStepMapper;
import org.dromara.aigov.task.service.AigTaskStepTransition;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务步骤账本实现（设计 §8.2、§6.5）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskStepServiceImpl implements IAigTaskStepService {

    private static final String STATUS_NORMAL = "0";

    private static final int FIRST_ATTEMPT = 1;

    private final AigTaskStepMapper stepMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recordPlannedSteps(Long taskId, AigExecutionPlan plan) {
        if (taskId == null || plan == null) {
            return 0;
        }
        List<AigTaskStep> planned = new ArrayList<>();
        int sequence = 0;
        // 子 Agent 顺序调用：一步一个版本
        for (Long versionId : plan.getSubAgentVersionIds()) {
            planned.add(newStep(taskId, ++sequence, AigTaskStepKindEnum.SUB_AGENT, versionId,
                plan.getInputHash()));
        }
        // 顺序流程节点：节点本身不是"资源版本"，resource_version_id 留空
        for (AigFlowNode ignored : plan.getFlowNodes()) {
            planned.add(newStep(taskId, ++sequence, AigTaskStepKindEnum.FLOW_NODE, null,
                plan.getInputHash()));
        }
        int created = 0;
        for (AigTaskStep step : planned) {
            // 幂等：同一 (task, sequence) 的首次尝试已存在就不再写（重复登记会把步骤翻倍）
            Long existing = stepMapper.selectCount(new LambdaQueryWrapper<AigTaskStep>()
                .eq(AigTaskStep::getTaskId, taskId)
                .eq(AigTaskStep::getSequence, step.getSequence())
                .eq(AigTaskStep::getAttempt, FIRST_ATTEMPT));
            if (existing != null && existing > 0L) {
                continue;
            }
            stepMapper.insert(step);
            created++;
        }
        if (created > 0) {
            log.info("登记任务步骤, taskId={}, created={}", taskId, created);
        }
        return created;
    }

    @Override
    public List<AigTaskStep> listSteps(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return stepMapper.selectList(new LambdaQueryWrapper<AigTaskStep>()
            .eq(AigTaskStep::getTaskId, taskId)
            .orderByAsc(AigTaskStep::getSequence));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTaskStep startStep(Long stepId) {
        AigTaskStep step = requireStep(stepId);
        AigTaskStepStatusEnum from = AigTaskStepStatusEnum.find(step.getStatus());
        if (from == AigTaskStepStatusEnum.RUNNING) {
            // 幂等：重复"开始"不报错，也不改状态
            return step;
        }
        if (!AigTaskStepTransition.canMove(from, AigTaskStepStatusEnum.RUNNING)) {
            throw new ServiceException("步骤状态不允许开始：" + step.getStatus()
                + "（stepId=" + stepId + "）");
        }
        return casTransition(step, from, AigTaskStepStatusEnum.RUNNING, null, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTaskStep finishStep(Long stepId, AigTaskStepStatusEnum status, String outputRef,
                                  String approvalRef, String errorCode, String errorMessage) {
        AigTaskStep step = requireStep(stepId);
        AigTaskStepStatusEnum from = AigTaskStepStatusEnum.find(step.getStatus());
        if (from != null && from.isTerminal()) {
            // 幂等：不覆盖"它曾经失败过/成功过"的事实
            return step;
        }
        if (!AigTaskStepTransition.canMove(from, status)) {
            throw new ServiceException("步骤状态不允许从 " + step.getStatus() + " 迁移到 "
                + (status == null ? null : status.getCode()) + "（stepId=" + stepId + "）");
        }
        return casTransition(step, from, status, outputRef, approvalRef, errorCode, errorMessage);
    }

    private AigTaskStep requireStep(Long stepId) {
        if (stepId == null) {
            throw new ServiceException("步骤ID不能为空");
        }
        AigTaskStep step = stepMapper.selectById(stepId);
        if (step == null) {
            throw new ServiceException("任务步骤不存在或已删除：" + stepId);
        }
        return step;
    }

    private AigTaskStep casTransition(AigTaskStep step, AigTaskStepStatusEnum from,
                                      AigTaskStepStatusEnum to, String outputRef, String approvalRef,
                                      String errorCode, String errorMessage) {
        AigTaskStep update = new AigTaskStep();
        update.setStepId(step.getStepId());
        update.setStatus(to.getCode());
        update.setOutputRef(outputRef);
        update.setApprovalRef(approvalRef);
        update.setErrorCode(errorCode);
        update.setErrorMessage(StringUtils.substring(errorMessage, 0, 500));
        int rows = stepMapper.update(update, Wrappers.<AigTaskStep>lambdaUpdate()
            .eq(AigTaskStep::getStepId, step.getStepId())
            .eq(AigTaskStep::getStatus, from.getCode()));
        if (rows == 0) {
            throw new ServiceException("步骤状态已被其它操作改变，请刷新后重试：stepId=" + step.getStepId());
        }
        step.setStatus(to.getCode());
        step.setOutputRef(outputRef);
        step.setApprovalRef(approvalRef);
        step.setErrorCode(errorCode);
        step.setErrorMessage(StringUtils.substring(errorMessage, 0, 500));
        log.info("任务步骤状态迁移, stepId={}, {} -> {}", step.getStepId(), from.getCode(), to.getCode());
        return step;
    }

    private static AigTaskStep newStep(Long taskId, int sequence, AigTaskStepKindEnum kind,                                       Long resourceVersionId, String inputHash) {
        AigTaskStep step = new AigTaskStep();
        step.setTaskId(taskId);
        step.setSequence(sequence);
        step.setKind(kind.getCode());
        step.setResourceVersionId(resourceVersionId);
        step.setStatus(AigTaskStepStatusEnum.PLANNED.getCode());
        step.setInputHash(inputHash);
        step.setAttempt(FIRST_ATTEMPT);
        step.setDelFlag(STATUS_NORMAL);
        return step;
    }

}
