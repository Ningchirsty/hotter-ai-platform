package org.dromara.hrtalent.domainservice;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.hrtalent.enums.ApplicationResultEnum;
import org.dromara.hrtalent.enums.CandidateStageEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应聘阶段机领域服务单元测试（SPEC-P3 §5 自检第 4 条）。
 *
 * <p>重点覆盖 §7.2 的<b>六条跳转前置校验</b>与阶段顺序约束。被测类是不访问数据库的纯函数，
 * 因此无需 Spring 上下文、无需 Mockito（本机 JVM 禁止 Mockito 自附加 Agent）。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class ApplicationStageDomainServiceTest {

    /**
     * 被测领域服务。
     */
    private ApplicationStageDomainService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationStageDomainService();
    }

    /**
     * 构造阶段跳转事实。
     *
     * @param scheduled      是否存在面试安排
     * @param firstConcluded 是否有一面结论
     * @param finalConcluded 是否有最终面试结论
     * @param backgroundOk   是否有背调结论
     * @param waived         是否已豁免背调且有授权原因
     * @param offerResult    邀约结果
     * @param planDate       计划报到日期
     * @return 阶段跳转事实
     */
    private ApplicationStageDomainService.StageFacts facts(boolean scheduled, boolean firstConcluded,
                                                           boolean finalConcluded, boolean backgroundOk,
                                                           boolean waived, String offerResult, LocalDate planDate) {
        return new ApplicationStageDomainService.StageFacts(scheduled, firstConcluded, finalConcluded,
            backgroundOk, waived, offerResult, planDate);
    }

    /**
     * 资料齐全的事实（用于验证「校验通过」的正向用例）。
     *
     * @return 阶段跳转事实
     */
    private ApplicationStageDomainService.StageFacts fullFacts() {
        return facts(true, true, true, true, true, "accepted", LocalDate.now().plusDays(7));
    }

    /* ------------------------------------------------------------------ 第 1 条：进入一面 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第1条：没有面试安排时不允许进入一面")
    void shouldRejectFirstInterviewWithoutSchedule() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.INVITE, CandidateStageEnum.FIRST_INTERVIEW, false, null,
            facts(false, false, false, false, false, null, null)));
        assertEquals("进入「一面」前必须先安排面试", ex.getMessage());
    }

    @Test
    @DisplayName("第1条：存在面试安排时允许进入一面")
    void shouldAllowFirstInterviewWithSchedule() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.INVITE, CandidateStageEnum.FIRST_INTERVIEW, false, null,
            facts(true, false, false, false, false, null, null));
        assertEquals(CandidateStageEnum.FIRST_INTERVIEW, plan.toStage());
        assertEquals(ApplicationStageDomainService.ACTION_MOVE, plan.actionType());
        assertEquals(ApplicationResultEnum.PROCESSING, plan.result());
    }

    /* ------------------------------------------------------------------ 第 2 条：进入二面 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第2条：没有一面结论时不允许进入二面")
    void shouldRejectSecondInterviewWithoutFirstConclusion() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.FIRST_INTERVIEW, CandidateStageEnum.SECOND_INTERVIEW, false, null,
            facts(true, false, false, false, false, null, null)));
        assertEquals("进入「二面」前必须先录入一面结论", ex.getMessage());
    }

    @Test
    @DisplayName("第2条：有一面结论时允许进入二面")
    void shouldAllowSecondInterviewWithFirstConclusion() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.FIRST_INTERVIEW, CandidateStageEnum.SECOND_INTERVIEW, false, null,
            facts(true, true, false, false, false, null, null));
        assertEquals(CandidateStageEnum.SECOND_INTERVIEW, plan.toStage());
    }

    /* ------------------------------------------------------------------ 第 3 条：进入待背调 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第3条：没有最终面试结论时不允许进入待背调")
    void shouldRejectBackgroundWithoutFinalInterviewConclusion() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.SECOND_INTERVIEW, CandidateStageEnum.BACKGROUND, false, null,
            facts(true, true, false, false, false, null, null)));
        assertEquals("进入「待背调」前必须先录入最终面试结论", ex.getMessage());
    }

    @Test
    @DisplayName("第3条：有最终面试结论时允许进入待背调")
    void shouldAllowBackgroundWithFinalInterviewConclusion() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.SECOND_INTERVIEW, CandidateStageEnum.BACKGROUND, false, null,
            facts(true, true, true, false, false, null, null));
        assertEquals(CandidateStageEnum.BACKGROUND, plan.toStage());
    }

    /* ------------------------------------------------------------------ 第 4 条：进入待录用 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第4条：既无背调结论也无免背调原因时不允许进入待录用")
    void shouldRejectOfferWithoutBackgroundConclusion() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.BACKGROUND, CandidateStageEnum.OFFER, false, null,
            facts(true, true, true, false, false, null, null)));
        assertEquals("进入「待录用」前必须有背调结论，或填写经授权的免背调原因", ex.getMessage());
    }

    @Test
    @DisplayName("第4条：有背调结论时允许进入待录用")
    void shouldAllowOfferWithBackgroundConclusion() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.BACKGROUND, CandidateStageEnum.OFFER, false, null,
            facts(true, true, true, true, false, null, null));
        assertEquals(CandidateStageEnum.OFFER, plan.toStage());
    }

    @Test
    @DisplayName("第4条：背调已豁免但未填写授权原因时不允许进入待录用")
    void shouldRejectOfferWhenWaivedWithoutReason() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.BACKGROUND, CandidateStageEnum.OFFER, false, null,
            facts(true, true, true, false, false, null, null)));
        assertEquals("进入「待录用」前必须有背调结论，或填写经授权的免背调原因", ex.getMessage());
    }

    @Test
    @DisplayName("第4条：经授权的免背调原因允许进入待录用")
    void shouldAllowOfferWithWaivedBackground() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.BACKGROUND, CandidateStageEnum.OFFER, false, null,
            facts(true, true, true, false, true, null, null));
        assertEquals(CandidateStageEnum.OFFER, plan.toStage());
    }

    /* ------------------------------------------------------------------ 第 5 条：进入待报到 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第5条：未记录邀约结果时不允许进入待报到")
    void shouldRejectPendingArrivalWithoutOfferResult() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.OFFER, CandidateStageEnum.PENDING_ARRIVAL, false, null,
            facts(true, true, true, true, false, null, LocalDate.now().plusDays(3))));
        assertEquals("进入「待报到」前必须记录邀约结果", ex.getMessage());
    }

    @Test
    @DisplayName("第5条：未记录计划报到日期时不允许进入待报到")
    void shouldRejectPendingArrivalWithoutPlanArrivalDate() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.OFFER, CandidateStageEnum.PENDING_ARRIVAL, false, null,
            facts(true, true, true, true, false, "accepted", null)));
        assertEquals("进入「待报到」前必须记录计划报到日期", ex.getMessage());
    }

    @Test
    @DisplayName("第5条：邀约结果与计划报到日期齐备时允许进入待报到")
    void shouldAllowPendingArrivalWithOfferAndPlanDate() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.OFFER, CandidateStageEnum.PENDING_ARRIVAL, false, null,
            facts(true, true, true, true, false, "accepted", LocalDate.now().plusDays(3)));
        assertEquals(CandidateStageEnum.PENDING_ARRIVAL, plan.toStage());
    }

    /* ------------------------------------------------------------------ 第 6 条：管理员例外跳转 ------------------------------------------------------------------ */

    @Test
    @DisplayName("第6条：管理员例外跳转必须填写原因")
    void shouldRejectOverrideWithoutReason() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.NEW, CandidateStageEnum.OFFER, true, "   ", facts(false, false, false, false, false, null, null)));
        assertEquals("管理员例外跳转必须填写原因", ex.getMessage());
    }

    @Test
    @DisplayName("第6条：管理员例外跳转并填写原因后可跳过前置校验")
    void shouldAllowOverrideWithReason() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.NEW, CandidateStageEnum.OFFER, true, "集团特批：急聘岗位免背调",
            ApplicationStageDomainService.StageFacts.none());
        assertEquals(CandidateStageEnum.OFFER, plan.toStage());
        assertEquals(ApplicationStageDomainService.ACTION_MOVE, plan.actionType());
    }

    @Test
    @DisplayName("第6条：管理员例外跳转允许阶段回退")
    void shouldAllowOverrideBackwardMove() {
        ApplicationStageDomainService.TransitionPlan plan = service.planMove(
            CandidateStageEnum.SECOND_INTERVIEW, CandidateStageEnum.RESUME_REVIEW, true, "简历版本更正",
            ApplicationStageDomainService.StageFacts.none());
        assertEquals(CandidateStageEnum.RESUME_REVIEW, plan.toStage());
    }

    /* ------------------------------------------------------------------ 阶段顺序 ------------------------------------------------------------------ */

    @Test
    @DisplayName("非例外跳转不允许阶段回退")
    void shouldRejectBackwardMoveWithoutOverride() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.SECOND_INTERVIEW, CandidateStageEnum.RESUME_REVIEW, false, null, fullFacts()));
        assertTrue(ex.getMessage().contains("应聘阶段只能向前流转"));
        assertTrue(ex.getMessage().contains("复试"));
    }

    @Test
    @DisplayName("目标阶段与当前阶段相同时拒绝")
    void shouldRejectSameStage() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.RESUME_REVIEW, CandidateStageEnum.RESUME_REVIEW, false, null, fullFacts()));
        assertEquals("目标阶段与当前阶段相同，无需流转", ex.getMessage());
    }

    @Test
    @DisplayName("进入已报到必须走报到接口")
    void shouldRejectArrivedByTransition() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.PENDING_ARRIVAL, CandidateStageEnum.ARRIVED, false, null, fullFacts()));
        assertTrue(ex.getMessage().contains("登记实际报到请使用报到接口"));
    }

    @Test
    @DisplayName("已报到候选人不能再次流转阶段")
    void shouldRejectMoveFromArrived() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planMove(
            CandidateStageEnum.ARRIVED, CandidateStageEnum.OFFER, true, "回退", fullFacts()));
        assertEquals("候选人已报到，不能再次流转阶段", ex.getMessage());
    }

    @Test
    @DisplayName("阶段顺序判定：只有严格向后才算前进")
    void shouldCheckForwardOrder() {
        assertTrue(service.isForward(CandidateStageEnum.NEW, CandidateStageEnum.RESUME_REVIEW));
        assertTrue(service.isForward(CandidateStageEnum.NEW, CandidateStageEnum.ARRIVED));
        assertFalse(service.isForward(CandidateStageEnum.NEW, CandidateStageEnum.NEW));
        assertFalse(service.isForward(CandidateStageEnum.OFFER, CandidateStageEnum.INVITE));
    }

    /* ------------------------------------------------------------------ 结果流转 ------------------------------------------------------------------ */

    @Test
    @DisplayName("淘汰必须填写原因")
    void shouldRejectRejectWithoutReason() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planOutcome(
            CandidateStageEnum.FIRST_INTERVIEW, ApplicationResultEnum.REJECTED, null, "  "));
        assertEquals("淘汰候选人必须填写原因", ex.getMessage());
    }

    @Test
    @DisplayName("淘汰流转：阶段不变、结果为未通过、动作 reject")
    void shouldPlanRejectOutcome() {
        ApplicationStageDomainService.TransitionPlan plan = service.planOutcome(
            CandidateStageEnum.FIRST_INTERVIEW, ApplicationResultEnum.REJECTED, "skill_mismatch", "技术不匹配");
        assertEquals(ApplicationStageDomainService.ACTION_REJECT, plan.actionType());
        assertEquals(CandidateStageEnum.FIRST_INTERVIEW, plan.fromStage());
        assertEquals(CandidateStageEnum.FIRST_INTERVIEW, plan.toStage());
        assertEquals(ApplicationResultEnum.REJECTED, plan.result());
    }

    @Test
    @DisplayName("候选人放弃必须填写原因")
    void shouldRejectWithdrawWithoutReason() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planOutcome(
            CandidateStageEnum.OFFER, ApplicationResultEnum.WITHDRAWN, null, null));
        assertEquals("登记候选人放弃必须填写原因", ex.getMessage());
    }

    @Test
    @DisplayName("暂缓与人才保留允许不填原因")
    void shouldAllowPauseAndTalentPoolWithoutReason() {
        ApplicationStageDomainService.TransitionPlan pause = service.planOutcome(
            CandidateStageEnum.SECOND_INTERVIEW, ApplicationResultEnum.PAUSED, null, null);
        assertEquals(ApplicationStageDomainService.ACTION_PAUSE, pause.actionType());
        assertEquals(ApplicationResultEnum.PAUSED, pause.result());
        ApplicationStageDomainService.TransitionPlan pool = service.planOutcome(
            CandidateStageEnum.SECOND_INTERVIEW, ApplicationResultEnum.TALENT_POOL, null, null);
        assertEquals(ApplicationStageDomainService.ACTION_TALENT_POOL, pool.actionType());
        assertEquals(ApplicationResultEnum.TALENT_POOL, pool.result());
    }

    @Test
    @DisplayName("processing/passed 不能作为结果流转的目标")
    void shouldRejectProcessingAsOutcome() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.planOutcome(
            CandidateStageEnum.OFFER, ApplicationResultEnum.PROCESSING, null, null));
        assertTrue(ex.getMessage().contains("不支持的应聘结果流转"));
        ServiceException passed = assertThrows(ServiceException.class, () -> service.planOutcome(
            CandidateStageEnum.OFFER, ApplicationResultEnum.PASSED, null, null));
        assertTrue(passed.getMessage().contains("不支持的应聘结果流转"));
    }

    /* ------------------------------------------------------------------ 结束态 ------------------------------------------------------------------ */

    @Test
    @DisplayName("已通过/已淘汰/已放弃属于结束态，暂缓与人才保留不是")
    void shouldCheckEndedStatus() {
        assertTrue(service.isEnded(ApplicationResultEnum.PASSED.getCode()));
        assertTrue(service.isEnded(ApplicationResultEnum.REJECTED.getCode()));
        assertTrue(service.isEnded(ApplicationResultEnum.WITHDRAWN.getCode()));
        assertFalse(service.isEnded(ApplicationResultEnum.PROCESSING.getCode()));
        assertFalse(service.isEnded(ApplicationResultEnum.PAUSED.getCode()));
        assertFalse(service.isEnded(ApplicationResultEnum.TALENT_POOL.getCode()));
        assertFalse(service.isEnded("unknown"));
    }

    @Test
    @DisplayName("已结束的应聘不能再流转阶段")
    void shouldRejectTransitionWhenEnded() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.validateNotEnded(ApplicationResultEnum.REJECTED.getCode()));
        assertTrue(ex.getMessage().contains("本次应聘已结束"));
        service.validateNotEnded(ApplicationResultEnum.PROCESSING.getCode());
        service.validateNotEnded(ApplicationResultEnum.PAUSED.getCode());
    }

    /* ------------------------------------------------------------------ 编码解析与结论判定 ------------------------------------------------------------------ */

    @Test
    @DisplayName("未知阶段与未知结果编码被拒绝")
    void shouldRejectUnknownCodes() {
        ServiceException stageEx = assertThrows(ServiceException.class, () -> service.requireStage("screening"));
        assertEquals("未知的应聘阶段：screening", stageEx.getMessage());
        ServiceException resultEx = assertThrows(ServiceException.class, () -> service.requireResult("done"));
        assertEquals("未知的应聘结果：done", resultEx.getMessage());
        assertNotNull(service.requireStage(CandidateStageEnum.NEW.getCode()));
        assertNotNull(service.requireResult(ApplicationResultEnum.PROCESSING.getCode()));
    }

    @Test
    @DisplayName("面试结论判定：非空且不是待反馈才算结论")
    void shouldJudgeInterviewConclusion() {
        assertFalse(service.isInterviewResultConcluded(null));
        assertFalse(service.isInterviewResultConcluded("  "));
        assertFalse(service.isInterviewResultConcluded("pending"));
        assertTrue(service.isInterviewResultConcluded("pass"));
        assertTrue(service.isInterviewResultConcluded("fail"));
        assertTrue(service.isInterviewResultConcluded("reserve"));
        assertTrue(service.isInterviewResultConcluded("absent"));
    }

    @Test
    @DisplayName("背调结论判定与免背调授权原因判定")
    void shouldJudgeBackgroundConclusion() {
        assertFalse(service.isBackgroundResultConcluded(null));
        assertFalse(service.isBackgroundResultConcluded("pending"));
        assertTrue(service.isBackgroundResultConcluded("pass"));
        assertTrue(service.isBackgroundResultConcluded("fail"));
        assertFalse(service.isBackgroundWaivedWithReason("waived", null));
        assertFalse(service.isBackgroundWaivedWithReason("waived", "  "));
        assertFalse(service.isBackgroundWaivedWithReason("pass", "业务特批"));
        assertTrue(service.isBackgroundWaivedWithReason("waived", "业务特批免背调"));
    }

    @Test
    @DisplayName("空事实不满足任何前置条件")
    void shouldHaveEmptyFacts() {
        ApplicationStageDomainService.StageFacts none = ApplicationStageDomainService.StageFacts.none();
        assertFalse(none.interviewScheduled());
        assertFalse(none.firstInterviewConcluded());
        assertFalse(none.finalInterviewConcluded());
        assertFalse(none.backgroundConcluded());
        assertFalse(none.backgroundWaivedWithReason());
        assertEquals(null, none.offerResult());
        assertEquals(null, none.planArrivalDate());
    }

}
