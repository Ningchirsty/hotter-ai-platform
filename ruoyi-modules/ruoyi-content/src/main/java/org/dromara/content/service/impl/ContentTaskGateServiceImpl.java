package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpFactSnapshot;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.CpTask;
import org.dromara.content.enums.ContentCardStatusEnum;
import org.dromara.content.enums.ContentFactConfirmStatusEnum;
import org.dromara.content.helper.ContentGateEngine;
import org.dromara.content.mapper.CpFactSnapshotMapper;
import org.dromara.content.mapper.CpInteractionCardMapper;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.content.service.IContentGateRuleService;
import org.dromara.content.service.IContentTaskGateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 闸门重算服务实现。
 *
 * @author content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentTaskGateServiceImpl implements IContentTaskGateService {

    /**
     * 任务 Mapper
     */
    private final CpTaskMapper taskMapper;

    /**
     * 事实快照 Mapper
     */
    private final CpFactSnapshotMapper factSnapshotMapper;

    /**
     * 互动卡 Mapper
     */
    private final CpInteractionCardMapper cardMapper;

    /**
     * 闸门规则服务
     */
    private final IContentGateRuleService gateRuleService;

    /**
     * 闸门判定引擎
     */
    private final ContentGateEngine gateEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentGateEngine.GateResult recheckAndApply(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        ContentGateEngine.GateResult result = evaluate(taskId);

        // 落库。
        // ⚠️ 必须用 set() 显式赋值，不能 updateById(实体)：
        // MyBatis-Plus 默认 updateStrategy=NOT_NULL，实体里为 null 的字段不会进入 UPDATE 语句，
        // 于是状态转好后 blockReason 清不掉，前端会看到「已可开工」却仍显示上一轮的阻断原因。
        // set() 会生成 SET block_reason = NULL，语义正确。
        taskMapper.update(null, new LambdaUpdateWrapper<CpTask>()
            .eq(CpTask::getTaskId, taskId)
            .set(CpTask::getStatus, result.getStatus())
            .set(CpTask::getBlockReason, result.getBlockReason()));

        log.info("闸门重算完成, taskId={}, deliverableType={}, status={}, blockCount={}, conditionCount={}",
            taskId, task.getDeliverableType(), result.getStatus(),
            result.getBlockUnsatisfied().size(), result.getConditionUnsatisfied().size());
        return result;
    }

    @Override
    public ContentGateEngine.GateResult evaluate(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        CpTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }

        // 1. 取该交付类型下启用的规则
        List<CpGateRule> rules = gateRuleService.listEnabledRules(task.getDeliverableType());

        // 2. 已确认字段集合（只有 CONFIRMED 才算数；PENDING 候选不参与闸门）
        List<CpFactSnapshot> confirmed = factSnapshotMapper.selectList(
            new LambdaQueryWrapper<CpFactSnapshot>()
                .eq(CpFactSnapshot::getTaskId, taskId)
                .eq(CpFactSnapshot::getConfirmStatus, ContentFactConfirmStatusEnum.CONFIRMED.getCode()));
        Set<String> confirmedFields = new LinkedHashSet<>();
        for (CpFactSnapshot s : confirmed) {
            confirmedFields.add(s.getFieldCode());
        }

        // 3. 是否存在被显式阻断的卡片
        boolean hasBlockedCard = cardMapper.selectCount(new LambdaQueryWrapper<CpInteractionCard>()
            .eq(CpInteractionCard::getTaskId, taskId)
            .eq(CpInteractionCard::getStatus, ContentCardStatusEnum.BLOCKED.getCode())) > 0;

        // 4. 判定
        return gateEngine.evaluate(rules, confirmedFields, hasBlockedCard);
    }

}
