package org.dromara.hrtalent.domainservice;

import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.enums.PlanCompletionStatusEnum;
import org.dromara.hrtalent.enums.PlanControlStatusEnum;
import org.dromara.hrtalent.enums.PlanExecutionStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 月度计划任务三维状态与主展示状态领域规则单元测试（SPEC-P2 §4.2 / §7.1.5）。
 *
 * <p>本类只验证 {@link PlanItemStatusDomainService} 的纯函数，不依赖 Spring 容器与数据库。</p>
 *
 * @author hr-talent
 */
@Tag("dev")
class PlanItemStatusDomainServiceTest {

    /**
     * 被测领域服务（无状态，可安全共享）。
     */
    private final PlanItemStatusDomainService domainService = new PlanItemStatusDomainService();

    @Test
    @DisplayName("剩余人数 = max(计划人数 - 已计入到岗人数, 0)，且不得为负")
    void shouldCalculateRemainingQtyNeverNegative() {
        assertEquals(3, domainService.resolveRemainingQty(3, 0));
        assertEquals(1, domainService.resolveRemainingQty(3, 2));
        assertEquals(0, domainService.resolveRemainingQty(3, 3));
        assertEquals(0, domainService.resolveRemainingQty(3, 5));
        assertEquals(0, domainService.resolveRemainingQty(null, 5));
        assertEquals(4, domainService.resolveRemainingQty(4, null));
        assertEquals(0, domainService.resolveRemainingQty(-2, null));
    }

    @Test
    @DisplayName("部分完成标记：到岗大于 0 且仍有剩余才成立")
    void shouldFlagPartiallyCompleted() {
        assertTrue(domainService.isPartiallyCompleted(1, 2));
        assertFalse(domainService.isPartiallyCompleted(0, 2));
        assertFalse(domainService.isPartiallyCompleted(2, 0));
        assertFalse(domainService.isPartiallyCompleted(null, 2));
    }

    @Test
    @DisplayName("主展示状态优先级：已取消 > 暂停 > 已完成 > 已结转 > 待报到 > 待录用 > 面试中 > 招聘中 > 待启动")
    void shouldResolveDisplayStatusByPriority() {
        String cancelled = PlanControlStatusEnum.CANCELLED.getCode();
        String paused = PlanControlStatusEnum.PAUSED.getCode();
        String normal = PlanControlStatusEnum.NORMAL.getCode();
        String completed = PlanCompletionStatusEnum.COMPLETED.getCode();
        String rolledOver = PlanCompletionStatusEnum.ROLLED_OVER.getCode();
        String unfinished = PlanCompletionStatusEnum.UNFINISHED.getCode();

        // 已取消最高优先级：即使已结转、仍在待报到，展示状态也是已取消
        assertEquals(PlanItemStatusDomainService.DisplayStatus.CANCELLED,
            domainService.resolveDisplayStatus(cancelled,
                PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), rolledOver, 0));
        // 暂停高于已完成
        assertEquals(PlanItemStatusDomainService.DisplayStatus.PAUSED,
            domainService.resolveDisplayStatus(paused,
                PlanExecutionStatusEnum.RECRUITING.getCode(), completed, 0));
        // 已完成高于已结转
        assertEquals(PlanItemStatusDomainService.DisplayStatus.COMPLETED,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), completed, 0));
        // 已结转高于待报到
        assertEquals(PlanItemStatusDomainService.DisplayStatus.ROLLED_OVER,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), rolledOver, 2));
        // 剩余为 0 时即使完成度字段没变也按已完成展示
        assertEquals(PlanItemStatusDomainService.DisplayStatus.COMPLETED,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.RECRUITING.getCode(), unfinished, 0));
        // 执行阶段回退顺序
        assertEquals(PlanItemStatusDomainService.DisplayStatus.PENDING_ARRIVAL,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), unfinished, 2));
        assertEquals(PlanItemStatusDomainService.DisplayStatus.OFFER,
            domainService.resolveDisplayStatus(normal, PlanExecutionStatusEnum.OFFER.getCode(), unfinished, 2));
        assertEquals(PlanItemStatusDomainService.DisplayStatus.INTERVIEWING,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.INTERVIEWING.getCode(), unfinished, 2));
        assertEquals(PlanItemStatusDomainService.DisplayStatus.RECRUITING,
            domainService.resolveDisplayStatus(normal,
                PlanExecutionStatusEnum.RECRUITING.getCode(), unfinished, 2));
        assertEquals(PlanItemStatusDomainService.DisplayStatus.PENDING,
            domainService.resolveDisplayStatus(normal, PlanExecutionStatusEnum.PENDING.getCode(), unfinished, 2));
        // 未知执行阶段编码一律落到待启动（fail-safe）
        assertEquals(PlanItemStatusDomainService.DisplayStatus.PENDING,
            domainService.resolveDisplayStatus(normal, "unknown_stage", unfinished, 2));
        assertEquals("待启动", PlanItemStatusDomainService.DisplayStatus.labelOf("pending"));
        assertEquals("not_exists", PlanItemStatusDomainService.DisplayStatus.labelOf("not_exists"));
    }

    @Test
    @DisplayName("部分完成标签不覆盖执行阶段：到岗 1 人、剩余 2 人时仍展示待报到")
    void shouldNotOverrideExecutionStageByPartialCompleted() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setPlanQty(3);
        item.setCreditedArrivalQty(1);
        item.setRemainingQty(2);
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        item.setExecutionStatus(PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode());
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());

        assertEquals(PlanItemStatusDomainService.DisplayStatus.PENDING_ARRIVAL,
            domainService.resolveDisplayStatus(item));
        assertTrue(domainService.isPartiallyCompleted(item.getCreditedArrivalQty(), item.getRemainingQty()));
    }

    @Test
    @DisplayName("完成度按人数与结转结果计算")
    void shouldResolveCompletionStatus() {
        assertEquals(PlanCompletionStatusEnum.COMPLETED, domainService.resolveCompletionStatus(3, 0, false));
        assertEquals(PlanCompletionStatusEnum.ROLLED_OVER, domainService.resolveCompletionStatus(1, 2, true));
        assertEquals(PlanCompletionStatusEnum.PARTIAL_COMPLETED, domainService.resolveCompletionStatus(1, 2, false));
        assertEquals(PlanCompletionStatusEnum.UNFINISHED, domainService.resolveCompletionStatus(0, 3, false));
        // 剩余为 0 优先判定已完成，即使已生成过结转任务
        assertEquals(PlanCompletionStatusEnum.COMPLETED, domainService.resolveCompletionStatus(3, 0, true));
    }

    @Test
    @DisplayName("自动刷新绝不覆盖人工控制状态与原因，并刷新最后刷新时间")
    void shouldNotOverrideManualControlStatusOnRefresh() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setPlanId(1001L);
        item.setItemNo("ITM20260801-001");
        item.setPlanMonth("2026-08");
        item.setPlanQty(5);
        item.setCreditedArrivalQty(0);
        item.setRemainingQty(5);
        item.setControlStatus(PlanControlStatusEnum.PAUSED.getCode());
        item.setControlReason("编制冻结，暂停招聘");
        item.setExecutionStatus(PlanExecutionStatusEnum.RECRUITING.getCode());
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());

        LocalDateTime refreshTime = LocalDateTime.of(2026, 8, 31, 2, 30);
        PlanItemStatusDomainService.DisplayStatus display =
            domainService.applyRefresh(item, 2, PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), false, refreshTime);

        assertEquals(PlanItemStatusDomainService.DisplayStatus.PAUSED, display);
        assertEquals(PlanControlStatusEnum.PAUSED.getCode(), item.getControlStatus());
        assertEquals("编制冻结，暂停招聘", item.getControlReason());
        assertEquals(3, item.getRemainingQty());
        assertEquals(2, item.getCreditedArrivalQty());
        assertEquals(PlanCompletionStatusEnum.PARTIAL_COMPLETED.getCode(), item.getCompletionStatus());
        assertEquals(PlanExecutionStatusEnum.PENDING_ARRIVAL.getCode(), item.getExecutionStatus());
        assertEquals(refreshTime, item.getLastRefreshTime());
        // 计划人数与月份不接受自动刷新改写
        assertEquals(5, item.getPlanQty());
        assertEquals("2026-08", item.getPlanMonth());
        assertEquals("ITM20260801-001", item.getItemNo());
    }

    @Test
    @DisplayName("已取消任务刷新后仍为已取消，完成度按人数照常计算")
    void shouldKeepCancelledStatusOnRefresh() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setPlanQty(2);
        item.setCreditedArrivalQty(0);
        item.setControlStatus(PlanControlStatusEnum.CANCELLED.getCode());
        item.setControlReason("需求撤销");
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());

        PlanItemStatusDomainService.DisplayStatus display =
            domainService.applyRefresh(item, 0, null, false, LocalDateTime.now());

        assertEquals(PlanItemStatusDomainService.DisplayStatus.CANCELLED, display);
        assertEquals(PlanControlStatusEnum.CANCELLED.getCode(), item.getControlStatus());
        assertEquals("需求撤销", item.getControlReason());
        assertEquals(2, item.getRemainingQty());
    }

    @Test
    @DisplayName("已成功结转的任务完成度为已结转、展示为已结转")
    void shouldMarkRolledOverOnRefresh() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setPlanQty(3);
        item.setCreditedArrivalQty(1);
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        item.setExecutionStatus(PlanExecutionStatusEnum.RECRUITING.getCode());

        PlanItemStatusDomainService.DisplayStatus display =
            domainService.applyRefresh(item, 1, null, true, LocalDateTime.now());

        assertEquals(PlanItemStatusDomainService.DisplayStatus.ROLLED_OVER, display);
        assertEquals(PlanCompletionStatusEnum.ROLLED_OVER.getCode(), item.getCompletionStatus());
        assertEquals(2, item.getRemainingQty());
    }

    @Test
    @DisplayName("终态判定：已完成、已结转与已取消不再允许修改")
    void shouldDetectFinalizedItem() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setControlStatus(PlanControlStatusEnum.NORMAL.getCode());
        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        assertFalse(domainService.isFinalized(item));

        item.setCompletionStatus(PlanCompletionStatusEnum.COMPLETED.getCode());
        assertTrue(domainService.isFinalized(item));

        item.setCompletionStatus(PlanCompletionStatusEnum.ROLLED_OVER.getCode());
        assertTrue(domainService.isFinalized(item));

        item.setCompletionStatus(PlanCompletionStatusEnum.UNFINISHED.getCode());
        item.setControlStatus(PlanControlStatusEnum.CANCELLED.getCode());
        assertTrue(domainService.isFinalized(item));

        assertTrue(domainService.isFinalized(null));
        assertEquals("计划任务已完成、取消或结转，不能继续修改", domainService.finalizedMessage());
    }

    @Test
    @DisplayName("结转开关只认 1")
    void shouldCheckCarryoverEnabled() {
        RecruitPlanItem item = new RecruitPlanItem();
        item.setCarryoverEnabled("1");
        assertTrue(domainService.isCarryoverEnabled(item));
        item.setCarryoverEnabled("0");
        assertFalse(domainService.isCarryoverEnabled(item));
        assertFalse(domainService.isCarryoverEnabled(null));
    }

}
