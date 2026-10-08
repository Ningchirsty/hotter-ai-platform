package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigUserQuota;
import org.dromara.aigov.domain.bo.AigUserQuotaBo;
import org.dromara.aigov.domain.vo.AigUserQuotaUsageVo;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.mapper.AigUserQuotaMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用人均配额实现测试（C3）。
 *
 * <p>钉住的是「配额到底拦不拦、拦得准不准、会不会误伤」这几件事：</p>
 * <ol>
 *     <li><b>无配额行 = 不限</b>：新表上线不改变任何既有调用行为（否则一次上线就是一次全量限流）；</li>
 *     <li><b>达到上限即拦</b>：上限是「允许的最大次数」，用满就该停；</li>
 *     <li><b>含失败调用</b>：只算成功会让「反复失败重试」成为绕开配额的路子；</li>
 *     <li><b>无调用人不拦</b>：调度/系统发起没有「人」可归属（也不能因此报错）；</li>
 *     <li><b>停用的配额不参与判定</b>；</li>
 *     <li><b>「清成不限」真的能清</b>：null 是「不限」的表示，而 {@code updateById} 会跳过 null。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigUserQuotaServiceImplTest {

    private static final Long USER_ID = 77L;

    private AigUserQuotaMapper quotaMapper;
    private AigInvocationAuditMapper auditMapper;
    private AigUserQuotaServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigUserQuota.class);
    }

    @BeforeEach
    void setUp() {
        quotaMapper = mock(AigUserQuotaMapper.class);
        auditMapper = mock(AigInvocationAuditMapper.class);
        service = new AigUserQuotaServiceImpl(quotaMapper, auditMapper);
    }

    /**
     * 造一行配额。
     *
     * @param daily   日上限（null=不限）
     * @param monthly 月上限（null=不限）
     * @param status  状态
     * @return 配额
     */
    private static AigUserQuota quota(Integer daily, Integer monthly, String status) {
        AigUserQuota entity = new AigUserQuota();
        entity.setQuotaId(9001L);
        entity.setUserId(USER_ID);
        entity.setUserName("zhangsan");
        entity.setDailyLimit(daily);
        entity.setMonthlyLimit(monthly);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("无配额行 = 不限：不拦（新表上线不改变既有调用行为）")
    void noQuotaRowMeansUnlimited() {
        when(quotaMapper.selectOne(any())).thenReturn(null);

        service.assertWithinQuota(USER_ID);

        AigUserQuotaUsageVo usage = service.usage(USER_ID);
        assertFalse(usage.limited(), "没有配额行时不是「受限」");
        assertFalse(usage.exceeded());
    }

    @Test
    @DisplayName("★ 达到日上限即拦：报出「今日 已用/上限」并说明单位是调用次数")
    void dailyLimitBlocksAtLimit() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(5, null, "0"));
        when(auditMapper.countByCallerSince(eq(USER_ID), any())).thenReturn(5L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assertWithinQuota(USER_ID));

        assertTrue(error.getMessage().contains("配额已用尽"), error.getMessage());
        assertTrue(error.getMessage().contains("今日 5/5"), error.getMessage());
        assertTrue(error.getMessage().contains("调用次数"), "必须说明单位，否则用户会以为是钱：" + error.getMessage());
    }

    @Test
    @DisplayName("未达上限放行（4/5 次）")
    void underLimitPasses() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(5, null, "0"));
        when(auditMapper.countByCallerSince(eq(USER_ID), any())).thenReturn(4L);

        service.assertWithinQuota(USER_ID);
    }

    @Test
    @DisplayName("月上限单独生效（日不限、月已满）")
    void monthlyLimitBlocks() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(null, 100, "0"));
        when(auditMapper.countByCallerSince(eq(USER_ID), any())).thenReturn(100L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.assertWithinQuota(USER_ID));

        assertTrue(error.getMessage().contains("本月 100/100"), error.getMessage());
        assertFalse(error.getMessage().contains("今日"), "没配日上限时不该报今日：" + error.getMessage());
    }

    @Test
    @DisplayName("计数含失败调用：超限判定只看审计行数，不看成败")
    void countIncludesFailures() {
        // 5 次里含失败——审计行数就是 5，因此应拦（只算成功会让反复重试绕开配额）
        when(quotaMapper.selectOne(any())).thenReturn(quota(5, null, "0"));
        when(auditMapper.countByCallerSince(eq(USER_ID), any())).thenReturn(5L);

        assertThrows(ServiceException.class, () -> service.assertWithinQuota(USER_ID));
        // 超限判定要查两个周期（日 + 月），因此是 2 次——不是「重复查询」而是两次不同的周期起点
        verify(auditMapper, times(2)).countByCallerSince(eq(USER_ID), any());
    }

    @Test
    @DisplayName("配额行被停用：不参与判定（等同于不限）")
    void disabledQuotaDoesNotLimit() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(1, 1, "1"));
        when(auditMapper.countByCallerSince(eq(USER_ID), any())).thenReturn(999L);

        service.assertWithinQuota(USER_ID);
    }

    @Test
    @DisplayName("无调用人（调度/系统发起）：不判也不查库——没有「人」可归属")
    void nullCallerIsNotLimited() {
        service.assertWithinQuota(null);
        service.assertWithinQuota(0L);

        verify(quotaMapper, never()).selectOne(any());
        verify(auditMapper, never()).countByCallerSince(any(), any());
    }

    @Test
    @DisplayName("用量视图：带上限、已用与周期起点（自然日 00:00 / 自然月 1 日 00:00）")
    void usageCarriesLimitsAndPeriods() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(10, 200, "0"));
        when(auditMapper.countByCallerSince(eq(USER_ID), eq(LocalDate.now().atStartOfDay())))
            .thenReturn(3L);
        when(auditMapper.countByCallerSince(eq(USER_ID), eq(LocalDate.now().withDayOfMonth(1).atStartOfDay())))
            .thenReturn(30L);

        AigUserQuotaUsageVo usage = service.usage(USER_ID);

        assertEquals(10, usage.dailyLimit());
        assertEquals(3L, usage.dailyUsed());
        assertEquals(LocalDate.now().atStartOfDay(), usage.dailyFrom());
        assertEquals(200, usage.monthlyLimit());
        assertEquals(30L, usage.monthlyUsed());
        assertEquals(LocalDate.now().withDayOfMonth(1).atStartOfDay(), usage.monthlyFrom());
        assertTrue(usage.limited());
        assertFalse(usage.exceeded());
        assertEquals("zhangsan", usage.userName());
    }

    @Test
    @DisplayName("用量视图：超限时 exceeded=true")
    void usageFlagsExceeded() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(10, null, "0"));
        when(auditMapper.countByCallerSince(any(), any())).thenReturn(11L);

        assertTrue(service.usage(USER_ID).exceeded());
    }

    @Test
    @DisplayName("保存：没有行则新增（一人一行）；有行则更新")
    void saveCreatesThenUpdates() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        AigUserQuotaBo create = new AigUserQuotaBo();
        create.setUserId(USER_ID);
        create.setUserName("zhangsan");
        create.setDailyLimit(10);
        create.setMonthlyLimit(200);
        service.save(create);
        verify(quotaMapper).insert(any(AigUserQuota.class));

        // 接下来是「更新」阶段：清掉上一个阶段的调用记录，否则 never() 会把新增阶段的 insert 也算进来
        clearInvocations(quotaMapper);
        when(quotaMapper.selectOne(any())).thenReturn(quota(10, 200, "0"));
        AigUserQuotaBo update = new AigUserQuotaBo();
        update.setUserId(USER_ID);
        update.setDailyLimit(20);
        update.setMonthlyLimit(300);
        Long id = service.save(update);

        assertEquals(9001L, id, "一人一行：更新既有行而不是再插一行");
        verify(quotaMapper).updateById(any(AigUserQuota.class));
        verify(quotaMapper, never()).insert(any(AigUserQuota.class));
    }

    @Test
    @DisplayName("★ 把上限清成「不限」：null 必须显式写入（updateById 会跳过 null，否则静默失败）")
    void saveClearsLimitsToUnlimited() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(10, 200, "0"));
        AigUserQuotaBo bo = new AigUserQuotaBo();
        bo.setUserId(USER_ID);
        bo.setDailyLimit(null);
        bo.setMonthlyLimit(300);

        service.save(bo);

        // 第一次：实体更新；第二次：显式 set null（清成不限）——两次都要发生
        verify(quotaMapper).updateById(any(AigUserQuota.class));
        verify(quotaMapper).update(eq(null), any());
    }

    @Test
    @DisplayName("删除：不存在报错；存在则逻辑删除（含义是回到「不限」，不是禁止调用）")
    void removeSemantics() {
        when(quotaMapper.selectById(9001L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.remove(9001L))
            .getMessage().contains("不存在"));

        AigUserQuota existing = quota(10, 200, "0");
        when(quotaMapper.selectById(9001L)).thenReturn(existing);
        service.remove(9001L);
        verify(quotaMapper).deleteById(9001L);
    }

    @Test
    @DisplayName("入参：用户为空报错；用量查询用户为空报错")
    void validatesInput() {
        assertThrows(ServiceException.class, () -> service.save(new AigUserQuotaBo()));
        assertTrue(assertThrows(ServiceException.class, () -> service.usage(null))
            .getMessage().contains("用户不能为空"));
        assertThrows(ServiceException.class, () -> service.remove(null));
    }

    @Test
    @DisplayName("周期起点是自然日/自然月，不是「最近24小时/最近30天」")
    void periodIsCalendarBased() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(1, 1, "0"));
        when(auditMapper.countByCallerSince(any(), any())).thenReturn(0L);

        LocalDateTime expectedDay = LocalDate.now().atStartOfDay();
        LocalDateTime expectedMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        service.usage(USER_ID);

        verify(auditMapper).countByCallerSince(USER_ID, expectedDay);
        verify(auditMapper).countByCallerSince(USER_ID, expectedMonth);
    }

    // ------------------------------------------------------------------
    // 以下三条是生产上真实发生过的缺陷的回归测试（R43）。
    // 每条都先在真库/真 HTTP 上复现过，再钉在这里——它们都不是"理论风险"。
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★分页清单能返回：内联 selectVoPage 会 ClassCastException（Page 不能转 Collection）")
    void queryPageReturnsRows() {
        AigUserQuotaVo vo = new AigUserQuotaVo();
        vo.setQuotaId(9001L);
        vo.setUserId(USER_ID);
        Page<AigUserQuotaVo> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(vo));
        // 显式类型见证：selectVoPage 的返回类型是自由类型变量，不写死会让同样的推断问题
        // 在测试里也复现（那正是这个方法要钉住的东西）
        when(quotaMapper.<Page<AigUserQuotaVo>>selectVoPage(any(), any())).thenReturn(page);

        PageResult<AigUserQuotaVo> result = service.queryPage(new AigUserQuotaBo(), new PageQuery(10, 1));

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRows().size());
        assertEquals(9001L, result.getRows().iterator().next().getQuotaId());
    }

    @Test
    @DisplayName("★删除后重新配置：复活同一行，绝不 INSERT（唯一键是 user_id，插新行会撞键 → 409）")
    void saveRevivesLogicallyDeletedRow() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        AigUserQuota tombstone = quota(10, 200, "0");
        tombstone.setDelFlag("1");
        when(quotaMapper.selectAnyByUser(USER_ID)).thenReturn(tombstone);

        AigUserQuotaBo bo = new AigUserQuotaBo();
        bo.setUserId(USER_ID);
        bo.setUserName("zhangsan");
        bo.setDailyLimit(3);
        bo.setMonthlyLimit(30);

        Long id = service.save(bo);

        assertEquals(9001L, id, "一人一行：复活墓碑那一行，而不是插新行");
        verify(quotaMapper).restoreById(9001L);
        verify(quotaMapper, never()).insert(any(AigUserQuota.class));
        verify(quotaMapper).updateById(any(AigUserQuota.class));
    }

    @Test
    @DisplayName("★没有墓碑时才新增（不能因为查过墓碑就每次都走复活）")
    void saveInsertsWhenNoRowAtAll() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.selectAnyByUser(USER_ID)).thenReturn(null);

        AigUserQuotaBo bo = new AigUserQuotaBo();
        bo.setUserId(USER_ID);
        bo.setDailyLimit(3);

        service.save(bo);

        verify(quotaMapper).insert(any(AigUserQuota.class));
        verify(quotaMapper, never()).restoreById(any());
    }

    @Test
    @DisplayName("★改额度：非空的新上限必须写进实体（只清 null 会漏掉新值——界面填 9、库里还是 5）")
    void saveWritesNonNullLimits() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(5, 50, "0"));

        AigUserQuotaBo bo = new AigUserQuotaBo();
        bo.setUserId(USER_ID);
        bo.setDailyLimit(9);
        bo.setMonthlyLimit(90);

        service.save(bo);

        ArgumentCaptor<AigUserQuota> captor = ArgumentCaptor.forClass(AigUserQuota.class);
        verify(quotaMapper).updateById(captor.capture());
        assertEquals(9, captor.getValue().getDailyLimit(), "日上限必须带上，否则改额度静默失效");
        assertEquals(90, captor.getValue().getMonthlyLimit(), "月上限必须带上");
        // 两个都非空时不需要额外的显式 set（那是给 null=不限 用的）
        verify(quotaMapper, never()).update(eq(null), any());
    }

}
