package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domainservice.PlanItemStatusDomainService;
import org.dromara.hrtalent.domainservice.PlanRolloverDomainService;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlan;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitPlanItemStatusLog;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemStatusLogVo;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.dromara.hrtalent.enums.PlanStatusEnum;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.mapper.RecruitPlanApplicationRelMapper;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitPlanItemStatusLogMapper;
import org.dromara.hrtalent.mapper.RecruitPlanMapper;
import org.dromara.hrtalent.mapper.RecruitPlanRolloverMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 月度计划任务状态重算服务实现（SPEC-P2 §4.2 / SPEC-P3 §3.2 / §21.15）。
 *
 * <p>把 {@link PlanItemStatusDomainService} 的纯计算结果落库，并维护计划表头汇总人数。
 * 本实现<b>绝不</b>改写 {@code control_status} / {@code control_reason}：人工暂停或取消的结果
 * 不会被自动刷新覆盖。</p>
 *
 * <p><b>P3 补全</b>：{@code execution_status} 不再保持原值，而是按 §7.1.5 由应聘记录
 * （{@code hr_recruit_application.current_stage} / {@code current_status}）自动推导，
 * 推导逻辑统一位于领域服务，本类只负责<b>只读</b>装配候选人事实与持久化。</p>
 *
 * <p><b>状态变更日志（§7.1.5、§21.15）</b>：每次刷新时，若 {@code execution_status} 或
 * {@code completion_status} 实际发生变化，向 {@code hr_recruit_plan_item_status_log} 追加一条日志
 * （触发事件、原状态、新状态、刷新时间）；状态未变化则不写。日志表为追加型，只插入不更新不删除。</p>
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

    /**
     * 应聘记录 Mapper（只读，用于推导自动执行阶段；不得写入他人域的表）。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 岗位执行项 Mapper（只读，用于把计划任务下的岗位归属解析为应聘记录）。
     */
    private final RecruitJobMapper recruitJobMapper;

    /**
     * 计划任务与应聘记录关联 Mapper（只读，用于解析任务下当前有效的应聘记录）。
     */
    private final RecruitPlanApplicationRelMapper recruitPlanApplicationRelMapper;

    /**
     * 计划任务状态变更日志 Mapper（追加型：只插入，不更新、不删除，§7.1.5 / §21.15）。
     */
    private final RecruitPlanItemStatusLogMapper recruitPlanItemStatusLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshItemStatus(Long itemId) {
        // 旧签名保留：既有调用方（事件监听前的历史调用、手工校准、报到路径）无法给出具体业务事件，
        // 统一记为人工刷新；旧方法委托新方法，签名与语义保持不变。
        refreshItemStatus(itemId, TRIGGER_MANUAL_REFRESH);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshItemStatus(Long itemId, String triggerEvent) {
        if (itemId == null) {
            throw new ServiceException("计划任务ID不能为空");
        }
        RecruitPlanItem item = recruitPlanItemMapper.selectById(itemId);
        if (item == null) {
            throw new ServiceException("计划任务不存在或已删除");
        }
        // 刷新前快照：用于「状态未变化则不写日志」的判定（§7.1.5，避免无意义刷表）
        String fromExecutionStatus = item.getExecutionStatus();
        String fromCompletionStatus = item.getCompletionStatus();
        boolean rolledOver = hasSuccessfulRollover(itemId);
        // 自动执行阶段由应聘记录推导（§7.1.5），禁止调用方自行拼接状态值
        String executionStatus = resolveExecutionStatus(item).getCode();
        planItemStatusDomainService.applyRefresh(item, item.getCreditedArrivalQty(),
            executionStatus, rolledOver, LocalDateTime.now());
        // 只写派生字段，避免误改编号、月份、计划人数与人工控制状态
        recruitPlanItemMapper.update(null, new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, itemId)
            .set(RecruitPlanItem::getCreditedArrivalQty, item.getCreditedArrivalQty())
            .set(RecruitPlanItem::getRemainingQty, item.getRemainingQty())
            .set(RecruitPlanItem::getCompletionStatus, item.getCompletionStatus())
            .set(RecruitPlanItem::getExecutionStatus, item.getExecutionStatus())
            .set(RecruitPlanItem::getLastRefreshTime, item.getLastRefreshTime()));
        appendStatusChangeLog(item, triggerEvent, fromExecutionStatus, fromCompletionStatus);
    }

    @Override
    public List<RecruitPlanItemStatusLogVo> listStatusLogs(Long itemId) {
        if (itemId == null) {
            throw new ServiceException("计划任务ID不能为空");
        }
        // 只读：按刷新时间升序，同一时间戳内再按主键升序保证顺序稳定
        return recruitPlanItemStatusLogMapper.selectVoList(new LambdaQueryWrapper<RecruitPlanItemStatusLog>()
            .eq(RecruitPlanItemStatusLog::getItemId, itemId)
            .orderByAsc(RecruitPlanItemStatusLog::getRefreshTime)
            .orderByAsc(RecruitPlanItemStatusLog::getLogId));
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

    /* ------------------------------------------------------------------ 状态变更日志（追加型） ------------------------------------------------------------------ */

    /**
     * 追加一条计划任务状态变更日志（§7.1.5、§21.15「append status change log」）。
     *
     * <p><b>写入口径（勿改）</b>：只有当 {@code execution_status} 或 {@code completion_status}
     * <b>实际发生变化</b>时才写一条日志；两者都未变化时<b>不写</b>，
     * 避免事件重复投递或定时校准造成无意义的刷表。</p>
     *
     * <p><b>追加型约束</b>：本方法只 {@code insert}，不更新、不删除既有日志；
     * 且只写日志表自身，绝不写 {@code control_status} / {@code control_reason}（§7.1.5 硬约束）。</p>
     *
     * @param item                 已完成 {@code applyRefresh} 的计划任务实体
     * @param triggerEvent         触发事件稳定编码
     * @param fromExecutionStatus  刷新前的自动执行阶段
     * @param fromCompletionStatus 刷新前的完成状态
     */
    private void appendStatusChangeLog(RecruitPlanItem item, String triggerEvent,
                                       String fromExecutionStatus, String fromCompletionStatus) {
        String toExecutionStatus = item.getExecutionStatus();
        String toCompletionStatus = item.getCompletionStatus();
        if (Objects.equals(fromExecutionStatus, toExecutionStatus)
            && Objects.equals(fromCompletionStatus, toCompletionStatus)) {
            // 状态未发生变化：不写日志
            return;
        }
        RecruitPlanItemStatusLog statusLog = new RecruitPlanItemStatusLog();
        statusLog.setItemId(item.getItemId());
        statusLog.setPlanId(item.getPlanId());
        statusLog.setTriggerEvent(triggerEvent);
        statusLog.setFromExecutionStatus(fromExecutionStatus);
        statusLog.setToExecutionStatus(toExecutionStatus);
        statusLog.setFromCompletionStatus(fromCompletionStatus);
        statusLog.setToCompletionStatus(toCompletionStatus);
        statusLog.setRefreshTime(item.getLastRefreshTime());
        statusLog.setOperatorId(currentUserId());
        recruitPlanItemStatusLogMapper.insert(statusLog);
    }

    /**
     * 取当前登录用户ID；无登录态（如定时任务、事件提交后回调）时返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ 自动执行阶段装配（只读） ------------------------------------------------------------------ */

    /**
     * 装配该计划任务下的候选人事实并委托领域服务推导自动执行阶段（§7.1.5）。
     *
     * <p><b>「任务已启动」的判定口径</b>：以所属月度计划表头
     * {@code hr_recruit_plan.status = executing}（{@link PlanStatusEnum#EXECUTING}）判定。</p>
     *
     * <p><b>为什么这样判定</b>：{@code hr_recruit_plan_item} 建表语句中<b>没有</b>独立的「已启动 /
     * 已开始执行」标记列（DDL 缺口已上报主控，本实现不自行加列），因此「人工启动」只能取表头状态。
     * 这与设计文档 §7.1.4 对「待启动」的退出条件「出现有效招聘动作<b>或</b>人工启动」并不冲突：
     * 「出现有效招聘动作」由领域服务中「存在有效候选人即 {@code recruiting}」这条覆盖，
     * 「人工启动」由表头状态覆盖，两条合起来即完整语义。</p>
     *
     * @param item 计划任务实体
     * @return 自动执行阶段
     */
    private PlanExecutionStatusEnum resolveExecutionStatus(RecruitPlanItem item) {
        List<PlanItemStatusDomainService.ApplicationProgress> progresses = loadApplicationProgress(item.getItemId());
        boolean started = false;
        if (item.getPlanId() != null) {
            RecruitPlan plan = recruitPlanMapper.selectById(item.getPlanId());
            started = plan != null && PlanStatusEnum.EXECUTING.getCode().equals(plan.getStatus());
        }
        return planItemStatusDomainService.resolveExecutionStatus(started, progresses);
    }

    /**
     * 只读装载计划任务下的候选人推进快照。
     *
     * <p>来源有两处，按应聘记录去重合并：</p>
     * <ol>
     *     <li>{@code hr_recruit_plan_application_rel} 中 {@code effective_end} 为空的有效关联；</li>
     *     <li>{@code hr_recruit_job.plan_item_id} 指向该任务的岗位下的应聘记录（兼容未建立关联的存量数据）。</li>
     * </ol>
     *
     * <p>本方法只读他人域的表，不写入、不新建 Mapper。</p>
     *
     * @param itemId 计划任务ID
     * @return 候选人推进快照列表（可能为空）
     */
    private List<PlanItemStatusDomainService.ApplicationProgress> loadApplicationProgress(Long itemId) {
        if (itemId == null) {
            return List.of();
        }
        Set<Long> applicationIds = new LinkedHashSet<>();
        List<RecruitPlanApplicationRel> rels = recruitPlanApplicationRelMapper.selectList(
            new LambdaQueryWrapper<RecruitPlanApplicationRel>()
                .eq(RecruitPlanApplicationRel::getPlanItemId, itemId)
                .isNull(RecruitPlanApplicationRel::getEffectiveEnd));
        if (rels != null) {
            for (RecruitPlanApplicationRel rel : rels) {
                if (rel != null && rel.getApplicationId() != null) {
                    applicationIds.add(rel.getApplicationId());
                }
            }
        }
        List<Long> jobIds = new ArrayList<>();
        List<RecruitJob> jobs = recruitJobMapper.selectList(new LambdaQueryWrapper<RecruitJob>()
            .eq(RecruitJob::getPlanItemId, itemId));
        if (jobs != null) {
            for (RecruitJob job : jobs) {
                if (job != null && job.getJobId() != null) {
                    jobIds.add(job.getJobId());
                }
            }
        }
        // 按应聘记录去重，保留首次装载到的事实
        Map<Long, PlanItemStatusDomainService.ApplicationProgress> progressMap = new LinkedHashMap<>();
        collectApplications(applicationIds, null, progressMap);
        collectApplications(null, jobIds, progressMap);
        return new ArrayList<>(progressMap.values());
    }

    /**
     * 查询应聘记录并折算为候选人推进快照。
     *
     * @param applicationIds 应聘记录ID集合，可为空表示不按ID查询
     * @param jobIds         岗位ID集合，可为空表示不按岗位查询
     * @param progressMap    结果收集容器，按应聘记录ID去重
     */
    private void collectApplications(Set<Long> applicationIds, List<Long> jobIds,
                                     Map<Long, PlanItemStatusDomainService.ApplicationProgress> progressMap) {
        List<RecruitApplication> applications = new ArrayList<>();
        if (applicationIds != null && !applicationIds.isEmpty()) {
            applications.addAll(recruitApplicationMapper.selectList(
                new LambdaQueryWrapper<RecruitApplication>()
                    .in(RecruitApplication::getApplicationId, applicationIds)));
        }
        if (jobIds != null && !jobIds.isEmpty()) {
            applications.addAll(recruitApplicationMapper.selectList(
                new LambdaQueryWrapper<RecruitApplication>()
                    .in(RecruitApplication::getJobId, jobIds)));
        }
        for (RecruitApplication application : applications) {
            if (application == null || application.getApplicationId() == null) {
                continue;
            }
            progressMap.putIfAbsent(application.getApplicationId(),
                new PlanItemStatusDomainService.ApplicationProgress(application.getCurrentStage(),
                    application.getCurrentStatus(), application.getArrivalDate() != null));
        }
    }

}
