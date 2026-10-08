package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.aigov.config.AigApprovalProperties;
import org.dromara.aigov.domain.AigCallApproval;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.bo.AigCallApprovalBo;
import org.dromara.aigov.domain.bo.AigCallApprovalDecideBo;
import org.dromara.aigov.domain.vo.AigCallApprovalSweepVo;
import org.dromara.aigov.enums.AigApprovalStatusEnum;
import org.dromara.aigov.mapper.AigCallApprovalMapper;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用授权审批的状态机与守卫测试（C3）。
 *
 * <p><b>为什么这些用例值得逐条钉住</b>：审批是发布/放行链条上的一环，而它出错的样子
 * 全都<b>不会报错</b>——自审放行、过期单被批准、并发下两条结论互相覆盖、
 * 授权到期时间算错，任何一种都只是安静地让一个不该发生的放行发生了。</p>
 *
 * <p>纯 Mockito：不加载 Spring，也不碰数据库。授权有效期的正确性用
 * 「{@code decidedAt} 到 {@code validUntil} 恰好是配置的时长」来断言，
 * 而不是写死一个时刻（那会在跨秒/跨分钟时偶发失败）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCallApprovalServiceImplTest {

    private static final String CAP = "image_generation";
    private static final String LEVEL = "INTERNAL";
    private static final long REQUESTER = 1001L;
    private static final long APPROVER = 2002L;

    private AigCallApprovalMapper approvalMapper;
    private AigCapabilityMapper capabilityMapper;
    private AigRoutePolicyMapper routePolicyMapper;
    private AigApprovalProperties properties;
    private AigCallApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        approvalMapper = mock(AigCallApprovalMapper.class);
        capabilityMapper = mock(AigCapabilityMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        properties = new AigApprovalProperties();
        service = new AigCallApprovalServiceImpl(approvalMapper, capabilityMapper,
            routePolicyMapper, properties);
        // 真实行为：MP 用 ASSIGN_ID 在 insert 时回填主键。桩里也回填，
        // 否则 apply() 的返回值恒为 null，测不到「返回新建的审批单ID」
        doAnswer(inv -> {
            ((AigCallApproval) inv.getArgument(0)).setApprovalId(9001L);
            return 1;
        }).when(approvalMapper).insert(any(AigCallApproval.class));
    }

    private static AigCapability capability(String status) {
        AigCapability item = new AigCapability();
        item.setCapabilityCode(CAP);
        item.setStatus(status);
        return item;
    }

    private static AigRoutePolicy policy(String requireApproval) {
        AigRoutePolicy item = new AigRoutePolicy();
        item.setCapabilityCode(CAP);
        item.setDataLevel(LEVEL);
        item.setRequireApproval(requireApproval);
        item.setStatus("0");
        return item;
    }

    private static AigCallApproval row(long id, Long requester, String status, LocalDateTime expireTime) {
        AigCallApproval row = new AigCallApproval();
        row.setApprovalId(id);
        row.setRequesterId(requester);
        row.setCapabilityCode(CAP);
        row.setDataLevel(LEVEL);
        row.setStatus(status);
        row.setExpireTime(expireTime);
        return row;
    }

    private static AigCallApprovalBo applyBo() {
        AigCallApprovalBo bo = new AigCallApprovalBo();
        bo.setCapabilityCode(CAP);
        bo.setDataLevel(LEVEL);
        bo.setReason("上线前联调需要");
        return bo;
    }

    private static AigCallApprovalDecideBo decideBo(boolean approved, String remark) {
        AigCallApprovalDecideBo bo = new AigCallApprovalDecideBo();
        bo.setApproved(approved);
        bo.setRemark(remark);
        return bo;
    }

    /** 把「申请前必须成立」的三个前提都打上桩（能力存在、策略要求审批、无重复待审）。 */
    private void stubApplicable() {
        when(capabilityMapper.selectOne(any())).thenReturn(capability("0"));
        when(routePolicyMapper.selectOne(any())).thenReturn(policy("Y"));
        when(approvalMapper.selectCount(any())).thenReturn(0L);
    }

    private AigCallApproval captureUpdate() {
        ArgumentCaptor<AigCallApproval> captor = ArgumentCaptor.forClass(AigCallApproval.class);
        verify(approvalMapper).update(captor.capture(), any(Wrapper.class));
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // 申请
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 申请成功后是 PENDING，且带上审批时限（默认 24h）")
    void applyCreatesPendingWithDeadline() {
        stubApplicable();

        Long id = service.apply(REQUESTER, "alice", applyBo());

        assertEquals(9001L, id, "要返回新建的审批单ID，页面据此跳转/提示");
        ArgumentCaptor<AigCallApproval> captor = ArgumentCaptor.forClass(AigCallApproval.class);
        verify(approvalMapper).insert(captor.capture());
        AigCallApproval saved = captor.getValue();
        assertEquals(AigApprovalStatusEnum.PENDING.getCode(), saved.getStatus());
        assertEquals(REQUESTER, saved.getRequesterId());
        assertEquals("alice", saved.getRequesterName());
        assertEquals(CAP, saved.getCapabilityCode());
        assertEquals(LEVEL, saved.getDataLevel());
        assertNotNull(saved.getExpireTime(), "必须带审批时限：没有它这张单可以永远挂着");
        long minutes = Duration.between(LocalDateTime.now(), saved.getExpireTime()).toMinutes();
        assertTrue(minutes >= 1439 && minutes <= 1440,
            "审批时限应约等于配置的 1440 分钟，实际 " + minutes);
        assertNull(saved.getValidUntil(), "申请阶段不该有授权有效期——那是批准时才产生的");
    }

    @Test
    @DisplayName("没有申请人 → 拒绝（不知道是谁提交的，审批就无从谈起）")
    void applyRejectsMissingRequester() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(null, "alice", applyBo()));

        assertTrue(e.getMessage().contains("申请人不能为空"), e.getMessage());
        verify(approvalMapper, never()).insert(any(AigCallApproval.class));
    }

    @Test
    @DisplayName("能力不存在或已停用 → 当场拒绝（为一个调不通的能力申请审批是无意义的）")
    void applyRejectsUnknownCapability() {
        stubApplicable();
        when(capabilityMapper.selectOne(any())).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", applyBo()));
        assertTrue(e.getMessage().contains("能力不存在或已停用"), e.getMessage());

        when(capabilityMapper.selectOne(any())).thenReturn(capability("1"));
        ServiceException e2 = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", applyBo()));
        assertTrue(e2.getMessage().contains("已停用"), e2.getMessage());
        verify(approvalMapper, never()).insert(any(AigCallApproval.class));
    }

    @Test
    @DisplayName("★ 该组合没有启用中的路由策略 → 说清「审批放行不了这种情况」")
    void applyRejectsWhenNoPolicy() {
        stubApplicable();
        when(routePolicyMapper.selectOne(any())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", applyBo()));

        assertTrue(e.getMessage().contains("没有启用中的路由策略"), e.getMessage());
        assertTrue(e.getMessage().contains("未配置"), "要把真正的拒绝原因说出来：" + e.getMessage());
        verify(approvalMapper, never()).insert(any(AigCallApproval.class));
    }

    @Test
    @DisplayName("策略并不要求审批 → 拒绝申请（直接调用即可，批了也是空转）")
    void applyRejectsWhenPolicyDoesNotRequireApproval() {
        stubApplicable();
        when(routePolicyMapper.selectOne(any())).thenReturn(policy("N"));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", applyBo()));

        assertTrue(e.getMessage().contains("不要求调用审批"), e.getMessage());
        verify(approvalMapper, never()).insert(any(AigCallApproval.class));
    }

    @Test
    @DisplayName("同一个人×能力×等级已有待审批单 → 拒绝重复提交（并提示可以撤回）")
    void applyRejectsDuplicatePending() {
        stubApplicable();
        when(approvalMapper.selectCount(any())).thenReturn(1L);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", applyBo()));

        assertTrue(e.getMessage().contains("已有一张待审批"), e.getMessage());
        assertTrue(e.getMessage().contains("撤回"), "要给出出路：" + e.getMessage());
        verify(approvalMapper, never()).insert(any(AigCallApproval.class));
    }

    @Test
    @DisplayName("非法数据等级 → 拒绝")
    void applyRejectsIllegalDataLevel() {
        AigCallApprovalBo bo = applyBo();
        bo.setDataLevel("SECRET");
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.apply(REQUESTER, "alice", bo));
        assertTrue(e.getMessage().contains("非法的数据等级"), e.getMessage());
    }

    // ------------------------------------------------------------------
    // 审批
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 申请人不得自审（分离职责）——这条在服务层，不靠页面藏按钮")
    void decideRejectsSelfApproval() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, REQUESTER, "alice", decideBo(true, null)));

        assertTrue(e.getMessage().contains("分离职责"), e.getMessage());
        verify(approvalMapper, never()).update(any(AigCallApproval.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("★ 已过审批时限 → 不得批准，并顺手置为「已超时」")
    void decideRejectsExpiredAndMarksExpired() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(),
                LocalDateTime.now().minusMinutes(1)));
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(1);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, APPROVER, "bob", decideBo(true, null)));

        assertTrue(e.getMessage().contains("超过审批时限"), e.getMessage());
        assertEquals(AigApprovalStatusEnum.EXPIRED.getCode(), captureUpdate().getStatus(),
            "既已超时就要把状态落成「已超时」，否则列表还显示「待审批」，人会以为是权限问题");
    }

    @Test
    @DisplayName("非「待审批」的单子不能再处理（终态不可重置）")
    void decideRejectsNonPending() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.APPROVED.getCode(), LocalDateTime.now().plusHours(1)));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, APPROVER, "bob", decideBo(true, null)));

        assertTrue(e.getMessage().contains("待审批"), e.getMessage());
        verify(approvalMapper, never()).update(any(AigCallApproval.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("★ 批准：状态变 APPROVED，授权有效期恰好是配置的 30 分钟")
    void decideApproveComputesValidUntil() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(1);

        service.decide(1L, APPROVER, "bob", decideBo(true, "同意，仅限本次联调"));

        AigCallApproval changed = captureUpdate();
        assertEquals(AigApprovalStatusEnum.APPROVED.getCode(), changed.getStatus());
        assertEquals(APPROVER, changed.getApproverId());
        assertEquals("bob", changed.getApproverName());
        assertEquals("同意，仅限本次联调", changed.getDecisionRemark());
        assertNotNull(changed.getDecidedAt());
        assertNotNull(changed.getValidUntil(), "批准必须算出授权有效期");
        assertEquals(30L, Duration.between(changed.getDecidedAt(), changed.getValidUntil()).toMinutes(),
            "授权有效期应恰好等于 aigov.approval.grant-ttl-minutes（默认 30 分钟）");
    }

    @Test
    @DisplayName("★ 驳回必须写明原因（「不同意」三个字不构成一次可追溯的审批）")
    void decideRejectRequiresRemark() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, APPROVER, "bob", decideBo(false, "   ")));

        assertTrue(e.getMessage().contains("驳回必须写明原因"), e.getMessage());
        verify(approvalMapper, never()).update(any(AigCallApproval.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("驳回：状态变 REJECTED，且不产生授权有效期")
    void decideRejectSetsStatusWithoutGrant() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(1);

        service.decide(1L, APPROVER, "bob", decideBo(false, "该能力不外发，改走本地模型"));

        AigCallApproval changed = captureUpdate();
        assertEquals(AigApprovalStatusEnum.REJECTED.getCode(), changed.getStatus());
        assertNull(changed.getValidUntil(), "驳回绝不能留下授权有效期");
        assertEquals("该能力不外发，改走本地模型", changed.getDecisionRemark());
    }

    @Test
    @DisplayName("★ 并发下第二次决定影响 0 行 → 报错，不覆盖别人的结论")
    void decideRejectsConcurrentDecision() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(0);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, APPROVER, "bob", decideBo(true, null)));

        assertTrue(e.getMessage().contains("并发"), e.getMessage());
    }

    @Test
    @DisplayName("必须明确批准还是驳回（漏传不能默认成驳回）")
    void decideRejectsMissingDecision() {
        AigCallApprovalDecideBo bo = new AigCallApprovalDecideBo();
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.decide(1L, APPROVER, "bob", bo));
        assertTrue(e.getMessage().contains("请明确"), e.getMessage());
    }

    // ------------------------------------------------------------------
    // 撤回
    // ------------------------------------------------------------------

    @Test
    @DisplayName("只能撤回自己的申请")
    void cancelRejectsOthersRequest() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.cancel(1L, 9999L));

        assertTrue(e.getMessage().contains("只能撤回自己"), e.getMessage());
        verify(approvalMapper, never()).update(any(AigCallApproval.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("撤回达成：状态变 CANCELLED（这样才不会堵住重新提交）")
    void cancelMarksCancelled() {
        when(approvalMapper.selectById(1L)).thenReturn(
            row(1L, REQUESTER, AigApprovalStatusEnum.PENDING.getCode(), LocalDateTime.now().plusHours(1)));
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(1);

        service.cancel(1L, REQUESTER);

        assertEquals(AigApprovalStatusEnum.CANCELLED.getCode(), captureUpdate().getStatus());
    }

    // ------------------------------------------------------------------
    // 授权判定（调用入口的热路径）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 没有调用人（调度/系统发起）→ 判为无授权，fail-closed")
    void hasValidGrantFalseWithoutCaller() {
        assertFalse(service.hasValidGrant(null, CAP, LEVEL));
        assertFalse(service.hasValidGrant(REQUESTER, "  ", LEVEL));
        assertFalse(service.hasValidGrant(REQUESTER, CAP, null));
        verify(approvalMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("有未过期的已批准授权 → true")
    void hasValidGrantTrue() {
        when(approvalMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasValidGrant(REQUESTER, CAP, LEVEL));
    }

    @Test
    @DisplayName("查不到（过期/被驳回/从未批准）→ false")
    void hasValidGrantFalseWhenNone() {
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.hasValidGrant(REQUESTER, CAP, LEVEL));
        when(approvalMapper.selectCount(any())).thenReturn(null);
        assertFalse(service.hasValidGrant(REQUESTER, CAP, LEVEL), "selectCount 返回 null 也不能当成 true");
    }

    // ------------------------------------------------------------------
    // 超时扫描
    // ------------------------------------------------------------------

    @Test
    @DisplayName("扫超时：只把 PENDING 置为 EXPIRED，并把条数报出来")
    void expireOverdueReportsCount() {
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(3);

        AigCallApprovalSweepVo vo = service.expireOverdue();

        assertEquals(3, vo.getExpired());
        assertNotNull(vo.getScannedAt());
        assertEquals(AigApprovalStatusEnum.EXPIRED.getCode(), captureUpdate().getStatus());
        verify(approvalMapper).update(any(AigCallApproval.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("没有超时单时如实报 0（「扫了一轮但什么都没做」要能看见）")
    void expireOverdueReportsZero() {
        when(approvalMapper.update(any(AigCallApproval.class), any(Wrapper.class))).thenReturn(0);

        assertEquals(0, service.expireOverdue().getExpired());
    }

}
