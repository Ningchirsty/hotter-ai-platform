package org.dromara.aigov.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.feedback.domain.AigResultFeedback;
import org.dromara.aigov.feedback.domain.bo.AigFeedbackHandleBo;
import org.dromara.aigov.feedback.domain.bo.AigResultFeedbackBo;
import org.dromara.aigov.feedback.enums.AigFeedbackVerdictEnum;
import org.dromara.aigov.feedback.mapper.AigResultFeedbackMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结果反馈服务测试（设计 §6.9）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigResultFeedbackServiceImplTest {

    private static final long FEEDBACK_ID = 8001L;

    private AigResultFeedbackMapper feedbackMapper;
    private AigResultFeedbackServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigResultFeedback.class);
    }

    @BeforeEach
    void setUp() {
        feedbackMapper = mock(AigResultFeedbackMapper.class);
        service = new AigResultFeedbackServiceImpl(feedbackMapper);
        when(feedbackMapper.insert(any(AigResultFeedback.class))).thenAnswer(invocation -> {
            invocation.<AigResultFeedback>getArgument(0).setFeedbackId(FEEDBACK_ID);
            return 1;
        });
        when(feedbackMapper.updateById(any(AigResultFeedback.class))).thenReturn(1);
    }

    @Test
    @DisplayName("★ 队列按动作过滤：负责用例的人要能问出「哪些是要我加入用例的」")
    void queueFiltersByAction() {
        when(feedbackMapper.selectList(any())).thenReturn(java.util.List.of());

        service.listQueue("CONFIRMED", "ADD_CASE", null);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AigResultFeedback>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(feedbackMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("action_code"), "必须真的按动作收窄：" + sql);
        assertTrue(sql.contains("verdict"), "结论过滤不能丢：" + sql);
    }

    @Test
    @DisplayName("★ 队列：结论/动作/原因三个过滤互相独立（空=不限，不是「等于空」）")
    void queueFiltersAreOptional() {
        when(feedbackMapper.selectList(any())).thenReturn(java.util.List.of());

        service.listQueue(null, null, "MISSING_SOURCE");
        service.listQueue("CONFIRMED", null, null);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AigResultFeedback>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(feedbackMapper, org.mockito.Mockito.times(2)).selectList(captor.capture());
        String withReason = captor.getAllValues().get(0).getSqlSegment();
        String onlyVerdict = captor.getAllValues().get(1).getSqlSegment();
        assertTrue(withReason.contains("reason_code"), "按原因过滤应生效：" + withReason);
        assertFalse(withReason.contains("action_code"), "没给动作就不该收窄动作：" + withReason);
        assertFalse(onlyVerdict.contains("reason_code"), "没给原因就不该收窄原因：" + onlyVerdict);
    }

    @Test
    @DisplayName("★ 队列：拼错的动作/原因要报错（当成「没匹配」会被读成「队列是空的」）")
    void queueRejectsUnknownFilters() {
        assertThrows(ServiceException.class, () -> service.listQueue("CONFIRMED", "ADD_CASE_X", null));
        assertThrows(ServiceException.class, () -> service.listQueue("CONFIRMED", null, "NO_SUCH"));
        assertThrows(ServiceException.class, () -> service.listQueue("NO_SUCH_VERDICT", null, null));
        verify(feedbackMapper, org.mockito.Mockito.never()).selectList(any());
    }

    @Test
    @DisplayName("★ 分页队列：与全量版共用同一份过滤条件（拼错编码仍报错，不静默变成空队列）")
    void queryQueuePageSharesFilterWithListQueue() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AigResultFeedback> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 10);
        page.setRecords(java.util.List.of(new AigResultFeedback()));
        page.setTotal(23);
        when(feedbackMapper.selectPage(any(), any())).thenReturn(page);

        // ⚠️ PageQuery 的构造器是 (pageSize, pageNum) —— 顺序不能想当然
        org.dromara.common.core.domain.PageResult<AigResultFeedback> result =
            service.queryQueuePage("CONFIRMED", "ADD_CASE", null, new PageQuery(10, 2));

        assertEquals(23, result.getTotal());
        assertEquals(1, result.getRows().size());

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AigResultFeedback>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(feedbackMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("verdict"), "过滤条件里应带上结论：" + sql);
        assertTrue(sql.contains("action_code"), "过滤条件里应带上后续动作：" + sql);

        // 分页版不能把"筛错了"变成"空队列"：非法编码仍要抛
        assertThrows(ServiceException.class,
            () -> service.queryQueuePage("CONFIRMED", "ADD_CASE_X", null, new PageQuery(10, 1)));
        assertThrows(ServiceException.class,
            () -> service.queryQueuePage("NO_SUCH_VERDICT", null, null, new PageQuery(10, 1)));
    }

    private static AigResultFeedbackBo bo(String reason) {
        AigResultFeedbackBo bo = new AigResultFeedbackBo();
        bo.setReasonCode(reason);
        bo.setTaskId(11L);
        bo.setResultId(22L);
        bo.setAgentVersionId(33L);
        return bo;
    }

    @Test
    @DisplayName("★ 提交：非法原因拒绝；未关联任务/结果拒绝（无法定位的反馈不可处理）")
    void submitValidates() {
        assertThrows(ServiceException.class, () -> service.submit(bo("NO_SUCH"), 9L));

        AigResultFeedbackBo orphan = bo("USEFUL");
        orphan.setTaskId(null);
        orphan.setResultId(null);
        assertThrows(ServiceException.class, () -> service.submit(orphan, 9L));
    }

    @Test
    @DisplayName("提交：进入问题队列（PENDING）并记录关联与反馈人")
    void submitEntersQueue() {
        assertEquals(FEEDBACK_ID, service.submit(bo("FACT_ERROR"), 9L));

        ArgumentCaptor<AigResultFeedback> captor = ArgumentCaptor.forClass(AigResultFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        AigResultFeedback saved = captor.getValue();
        assertEquals(AigFeedbackVerdictEnum.PENDING.getCode(), saved.getVerdict());
        assertEquals("FACT_ERROR", saved.getReasonCode());
        assertEquals(22L, saved.getResultId());
        assertEquals(9L, saved.getReporterId());
    }

    @Test
    @DisplayName("★ 处理：确认问题必须给出动作（否则问题被确认了却没人接着处理）")
    void confirmRequiresAction() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(new AigResultFeedback());
        AigFeedbackHandleBo confirmNoAction = new AigFeedbackHandleBo();
        confirmNoAction.setVerdict("CONFIRMED");
        assertThrows(ServiceException.class, () -> service.handle(FEEDBACK_ID, confirmNoAction, 9L));
    }

    @Test
    @DisplayName("处理：确认 + 动作 → 记录结论、动作、处理人与时间")
    void confirmWithActionIsRecorded() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(new AigResultFeedback());
        AigFeedbackHandleBo bo = new AigFeedbackHandleBo();
        bo.setVerdict("CONFIRMED");
        bo.setActionCode("ADD_KNOWLEDGE");

        AigResultFeedback handled = service.handle(FEEDBACK_ID, bo, 7L);

        assertEquals(AigFeedbackVerdictEnum.CONFIRMED.getCode(), handled.getVerdict());
        assertEquals("ADD_KNOWLEDGE", handled.getActionCode());
        assertEquals(7L, handled.getHandledBy());
        assertNotNull(handled.getHandledAt());
    }

    @Test
    @DisplayName("处理：驳回清空动作；PENDING 不是可提交的处理结论")
    void rejectClearsActionAndPendingIsRejected() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(new AigResultFeedback());
        AigFeedbackHandleBo pending = new AigFeedbackHandleBo();
        pending.setVerdict("PENDING");
        assertThrows(ServiceException.class, () -> service.handle(FEEDBACK_ID, pending, 7L));

        AigFeedbackHandleBo reject = new AigFeedbackHandleBo();
        reject.setVerdict("REJECTED");
        AigResultFeedback handled = service.handle(FEEDBACK_ID, reject, 7L);
        assertEquals(AigFeedbackVerdictEnum.REJECTED.getCode(), handled.getVerdict());
        assertEquals(null, handled.getActionCode());
    }

    @Test
    @DisplayName("处理：反馈不存在 → 可读拒绝")
    void handleMissingFeedback() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(null);
        AigFeedbackHandleBo bo = new AigFeedbackHandleBo();
        bo.setVerdict("REJECTED");
        assertThrows(ServiceException.class, () -> service.handle(FEEDBACK_ID, bo, 7L));
    }

}
