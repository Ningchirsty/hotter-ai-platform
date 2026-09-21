package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.domainservice.PlanItemStatusDomainService;
import org.dromara.hrtalent.domainservice.PlanRolloverDomainService;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 月度计划任务状态重算服务实现（SPEC-P2 §4.2 / §21.15）。
 *
 * <p>把 {@link PlanItemStatusDomainService} 的纯计算结果落库，并维护计划表头汇总人数。
 * 本实现<b>绝不</b>改写 {@code control_status} / {@code control_reason}：人工暂停或取消的结果
 * 不会被自动刷新覆盖。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanItemStatusServiceImpl implements IPlanItemStatusService {

    /**
     * 结转记录表中代表成功的结果编码。
     */
    private static final String ROLLOVER_RESULT_SUCCESS = PlanRolloverDomainService.RESULT_SUCCESS;

    /**
     * 计划任务 Mapper。
     */
    private final RecruitPlanItemMapper recruitPlanItemMapper;

    /**
     * 计划表头 Mapper。
     */
    private final RecruitPlanMapper recruitPlanMapper;

    /**
     * 结转记录 Mapper。
     */
    private final RecruitPlanRolloverMapper recruitPlanRolloverMapper;

    /**
     * 计划任务状态领域服务。
     */
    private final PlanItemStatusDomainService planItemStatusDomainService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshItemStatus(Long itemId) {
        if (itemId == null) {
            throw new ServiceException("计划任务ID不能为空");
        }
        RecruitPlanItem item = recruitPlanItemMapper.selectById(itemId);
        if (item == null) {
            throw new ServiceException("计划任务不存在或已删除");
        }
        boolean rolledOver = hasSuccessfulRollover(itemId);
        planItemStatusDomainService.applyRefresh(item, item.getCreditedArrivalQty(),
            item.getExecutionStatus(), rolledOver, LocalDateTime.now());
        // 只写派生字段，避免误改编号、月份、计划人数与人工控制状态
        recruitPlanItemMapper.update(null, new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, itemId)
            .set(RecruitPlanItem::getCreditedArrivalQty, item.getCreditedArrivalQty())
            .set(RecruitPlanItem::getRemainingQty, item.getRemainingQty())
            .set(RecruitPlanItem::getCompletionStatus, item.getCompletionStatus())
            .set(RecruitPlanItem::getExecutionStatus, item.getExecutionStatus())
            .set(RecruitPlanItem::getLastRefreshTime, item.getLastRefreshTime()));
    }

    @Override
    public void refreshItemsOfPlan(Long planId) {
        if (planId == null) {
            throw new ServiceException("计划ID不能为空");
        }
        List<RecruitPlanItem> items = recruitPlanItemMapper.selectList(new LambdaQueryWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getPlanId, planId));
        for (RecruitPlanItem item : items) {
            refreshItemStatus(item.getItemId());
        }
        refreshPlanTotals(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshPlanTotals(Long planId) {
        if (planId == null) {
            return;
        }
        RecruitPlan plan = recruitPlanMapper.selectById(planId);
        if (plan == null) {
            log.warn("重算计划汇总时表头不存在, planId={}", planId);
            return;
        }
        List<RecruitPlanItem> items = recruitPlanItemMapper.selectList(new LambdaQueryWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getPlanId, planId));
        int totalPlan = 0;
        int totalCredited = 0;
        int totalRemaining = 0;
        for (RecruitPlanItem item : items) {
            totalPlan += item.getPlanQty() == null ? 0 : item.getPlanQty();
            totalCredited += item.getCreditedArrivalQty() == null ? 0 : item.getCreditedArrivalQty();
            totalRemaining += item.getRemainingQty() == null ? 0 : item.getRemainingQty();
        }
        recruitPlanMapper.update(null, new LambdaUpdateWrapper<RecruitPlan>()
            .eq(RecruitPlan::getPlanId, planId)
            .set(RecruitPlan::getTotalPlanQty, totalPlan)
            .set(RecruitPlan::getTotalCreditedQty, totalCredited)
            .set(RecruitPlan::getTotalRemainingQty, totalRemaining));
    }

    /**
     * 判断该任务是否已成功生成过目标月份结转任务。
     *
     * @param itemId 计划任务ID
     * @return 是否已成功结转
     */
    private boolean hasSuccessfulRollover(Long itemId) {
        return recruitPlanRolloverMapper.selectCount(new LambdaQueryWrapper<RecruitPlanRollover>()
            .eq(RecruitPlanRollover::getSourceItemId, itemId)
            .eq(RecruitPlanRollover::getResult, ROLLOVER_RESULT_SUCCESS)) > 0;
    }

}
