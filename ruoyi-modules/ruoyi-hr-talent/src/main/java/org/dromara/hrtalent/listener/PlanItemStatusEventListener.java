package org.dromara.hrtalent.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.event.ApplicationStageChangedEvent;
import org.dromara.hrtalent.event.BackgroundResultChangedEvent;
import org.dromara.hrtalent.event.CandidateArrivedEvent;
import org.dromara.hrtalent.event.InterviewResultChangedEvent;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.mapper.RecruitPlanApplicationRelMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 月度计划任务状态刷新事件监听器（SPEC-P3 §0 线 F / 设计文档 §7.1.5、§21.6）。
 *
 * <p><b>职责</b>：消费应聘、面试、背调、报到四个领域的领域事件，解析出受影响的
 * {@code planItemId} 后调用 {@link IPlanItemStatusService#refreshItemStatus(Long, String)}，
 * 并把事件对应的触发编码写入状态变更日志（§7.1.5）；
 * 由计划状态领域服务统一推导 {@code execution_status}（§11.1：「计划状态刷新由领域服务统一执行，
 * 候选人、面试、背调、录用和报到服务不得各自直接拼接状态值」）。</p>
 *
 * <p><b>事务边界（§21.6、§21.7）</b>：监听器只在<b>业务事务提交后</b>执行
 * （{@link TransactionalEventListener} + {@link TransactionPhase#AFTER_COMMIT}）。
 * {@code fallbackExecution = true} 在这里<b>必须有</b>：本模块的事件生产方（应聘 / 面试 /
 * 背调 / 报到服务）已经用 {@code TransactionSynchronization#afterCommit} 在<b>事务提交之后</b>
 * 发布事件，此时线程上已无活动事务，而 {@code AFTER_COMMIT} 监听器在没有活动事务时默认
 * <b>既不执行也不报错</b>——不加该属性，四个事件会被静默丢弃，状态永远不刷新。</p>
 *
 * <p><b>失败隔离</b>：监听器内部异常一律捕获并记为 {@code log.error}（只记录事件名、
 * 业务ID与异常类型，禁止输出敏感信息），不得影响已提交的业务事务。</p>
 *
 * <p><b>边界</b>：本类只读他人域的表用于解析归属，不写入、不新建 Mapper；
 * 解析不到计划任务时记日志并跳过，不抛异常。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanItemStatusEventListener {

    /**
     * 月度计划任务状态重算服务。
     */
    private final IPlanItemStatusService planItemStatusService;

    /**
     * 计划任务与应聘记录关联 Mapper（只读）。
     */
    private final RecruitPlanApplicationRelMapper recruitPlanApplicationRelMapper;

    /**
     * 应聘记录 Mapper（只读）。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 岗位执行项 Mapper（只读）。
     */
    private final RecruitJobMapper recruitJobMapper;

    /**
     * 处理应聘阶段变化事件：候选人新增、流转、淘汰、放弃、重新激活、转入或移出计划任务。
     *
     * @param event 应聘阶段变化事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApplicationStageChanged(ApplicationStageChangedEvent event) {
        if (event == null) {
            return;
        }
        refresh("ApplicationStageChangedEvent", event.planItemId(), event.applicationId(),
            IPlanItemStatusService.TRIGGER_APPLICATION_STAGE_CHANGED);
    }

    /**
     * 处理面试结果变化事件：面试安排、取消或结果提交。
     *
     * @param event 面试结果变化事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInterviewResultChanged(InterviewResultChangedEvent event) {
        if (event == null) {
            return;
        }
        refresh("InterviewResultChangedEvent", event.planItemId(), event.applicationId(),
            IPlanItemStatusService.TRIGGER_INTERVIEW_RESULT_CHANGED);
    }

    /**
     * 处理背调结论变化事件。
     *
     * @param event 背调结论变化事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBackgroundResultChanged(BackgroundResultChangedEvent event) {
        if (event == null) {
            return;
        }
        // 背调事件不携带计划任务ID，一律按应聘记录解析归属
        refresh("BackgroundResultChangedEvent", null, event.applicationId(),
            IPlanItemStatusService.TRIGGER_BACKGROUND_RESULT_CHANGED);
    }

    /**
     * 处理候选人报到事件。
     *
     * @param event 候选人报到事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCandidateArrived(CandidateArrivedEvent event) {
        if (event == null) {
            return;
        }
        refresh("CandidateArrivedEvent", event.planItemId(), event.applicationId(),
            IPlanItemStatusService.TRIGGER_CANDIDATE_ARRIVED);
    }

    /* ------------------------------------------------------------------ 内部实现 ------------------------------------------------------------------ */

    /**
     * 解析受影响的计划任务并触发状态刷新，异常只记日志。
     *
     * @param eventName        事件名称（用于日志定位）
     * @param explicitPlanItemId 事件自带的计划任务ID，可为空
     * @param applicationId    应聘记录ID，可为空
     * @param triggerEvent     触发事件稳定编码（写入状态变更日志，§7.1.5）
     */
    private void refresh(String eventName, Long explicitPlanItemId, Long applicationId, String triggerEvent) {
        try {
            Long planItemId = resolvePlanItemId(explicitPlanItemId, applicationId);
            if (planItemId == null) {
                log.warn("计划任务状态刷新跳过：未能解析出计划任务, event={}, applicationId={}",
                    eventName, applicationId);
                return;
            }
            planItemStatusService.refreshItemStatus(planItemId, triggerEvent);
            log.debug("计划任务状态刷新完成, event={}, applicationId={}, planItemId={}",
                eventName, applicationId, planItemId);
        } catch (Exception e) {
            // 事件消费失败不得影响已提交的业务事务，只记录标识与异常类型
            log.error("计划任务状态刷新失败, event={}, applicationId={}, planItemId={}, exception={}",
                eventName, applicationId, explicitPlanItemId, e.getClass().getSimpleName());
        }
    }

    /**
     * 解析受影响的计划任务ID。
     *
     * <p>解析顺序（§7.1.5、SPEC-P3 §3.2）：事件自带 → {@code hr_recruit_plan_application_rel}
     * （{@code effective_end} 为空的有效关系）→ {@code hr_recruit_application.job_id} →
     * {@code hr_recruit_job.plan_item_id}。全部落空时返回 null，由调用方记日志并跳过。</p>
     *
     * @param explicitPlanItemId 事件自带的计划任务ID，可为空
     * @param applicationId      应聘记录ID，可为空
     * @return 计划任务ID，无法解析时返回 null
     */
    private Long resolvePlanItemId(Long explicitPlanItemId, Long applicationId) {
        if (explicitPlanItemId != null) {
            return explicitPlanItemId;
        }
        if (applicationId == null) {
            return null;
        }
        List<RecruitPlanApplicationRel> rels = recruitPlanApplicationRelMapper.selectList(
            new LambdaQueryWrapper<RecruitPlanApplicationRel>()
                .eq(RecruitPlanApplicationRel::getApplicationId, applicationId)
                .isNull(RecruitPlanApplicationRel::getEffectiveEnd));
        if (rels != null) {
            for (RecruitPlanApplicationRel rel : rels) {
                if (rel != null && rel.getPlanItemId() != null) {
                    return rel.getPlanItemId();
                }
            }
        }
        RecruitApplication application = recruitApplicationMapper.selectById(applicationId);
        if (application == null || application.getJobId() == null) {
            return null;
        }
        RecruitJob job = recruitJobMapper.selectById(application.getJobId());
        return job == null ? null : job.getPlanItemId();
    }

}
