package org.dromara.aigov.agent.service.impl;

import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.evaluation.AigCanaryEvidence;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.config.AigCanaryProperties;
import org.dromara.aigov.domain.vo.AigErrorClassCountVo;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 灰度证据取数测试（哪一行算「这个版本的调用」、窗口从哪开始）。
 *
 * <p>判据本身在 {@link org.dromara.aigov.agent.evaluation.AigCanaryEvidenceTest} 里测；
 * 这里只测<b>取数</b>这段容易出错的地方：窗口起点是不是真的取自账本里进入 CANDIDATE 的时间、
 * 前提缺失时是不是<b>如实报告而不是抛异常</b>（查询接口抛异常会变成 500，
 * 页面就看不到「为什么不达标」）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCanaryEvidenceServiceTest {

    private static final long VERSION_ID = 4242L;

    private AigInvocationAuditMapper auditMapper;
    private AigReleaseEventMapper releaseEventMapper;
    private AigCanaryEvidenceService service;

    @BeforeEach
    void setUp() {
        auditMapper = mock(AigInvocationAuditMapper.class);
        releaseEventMapper = mock(AigReleaseEventMapper.class);
        service = new AigCanaryEvidenceService(auditMapper, releaseEventMapper, new AigCanaryProperties());
    }

    private static AigReleaseEvent candidateEvent(LocalDateTime when) {
        AigReleaseEvent event = new AigReleaseEvent();
        event.setEventId(7L);
        event.setTargetType("AGENT_VERSION");
        event.setTargetVersionId(VERSION_ID);
        event.setFromStatus("SANDBOX_TESTED");
        event.setToStatus("CANDIDATE");
        event.setOperateTime(when);
        return event;
    }

    private static AigErrorClassCountVo count(String errorClass, long count) {
        AigErrorClassCountVo vo = new AigErrorClassCountVo();
        vo.setErrorClass(errorClass);
        vo.setErrorCount(count);
        return vo;
    }

    @Test
    @DisplayName("★ 窗口起点取自账本里「进入 CANDIDATE」的时间，并原样传给统计（不是「最近 N 天」）")
    void windowStartsAtCandidateTransition() {
        LocalDateTime entered = LocalDateTime.now().minusDays(2);
        when(releaseEventMapper.selectOne(any())).thenReturn(candidateEvent(entered));
        when(auditMapper.countByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(100L);
        when(auditMapper.countFailedByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(2L);
        when(auditMapper.listErrorClassCountsSince(eq(VERSION_ID), any())).thenReturn(List.of());

        AigCanaryEvidence evidence = service.canaryEvidence("AGENT_VERSION", VERSION_ID);

        assertTrue(evidence.satisfied(), evidence.reason());
        assertEquals(entered, evidence.windowFrom(), "窗口起点就是进入 CANDIDATE 的时刻");
        // 三次取数都必须用同一个窗口起点：起点不一致会得出「调用 100 次、失败 30 次」
        // 这种自相矛盾的数字
        verify(auditMapper).countByAgentVersionSince(VERSION_ID, entered);
        verify(auditMapper).countFailedByAgentVersionSince(VERSION_ID, entered);
        verify(auditMapper).listErrorClassCountsSince(VERSION_ID, entered);
        assertNotNull(evidence.windowTo(), "窗口终点是查询时刻");
    }

    @Test
    @DisplayName("没有「进入 CANDIDATE」的记录 → 如实不达标，且说明要先推进到 CANDIDATE")
    void noCandidateEventBlocks() {
        when(releaseEventMapper.selectOne(any())).thenReturn(null);

        AigCanaryEvidence evidence = service.canaryEvidence("AGENT_VERSION", VERSION_ID);

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("CANDIDATE"), evidence.reason());
        assertEquals(0L, evidence.totalInvocations(), "窗口都没有，不该去查数");
    }

    @Test
    @DisplayName("事件存在但时间缺失 → 不达标（不能拿 null 当窗口起点去查全表）")
    void candidateEventWithoutTimeBlocks() {
        when(releaseEventMapper.selectOne(any())).thenReturn(candidateEvent(null));

        AigCanaryEvidence evidence = service.canaryEvidence("AGENT_VERSION", VERSION_ID);

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("CANDIDATE"), evidence.reason());
    }

    @Test
    @DisplayName("对象类型非法/版本ID为空 → 返回不达标证据而不是抛异常（查询接口抛出去会变成 500）")
    void badTargetIsReportedNotThrown() {
        AigCanaryEvidence badType = service.canaryEvidence("NOT_A_TYPE", VERSION_ID);
        assertFalse(badType.satisfied());
        assertTrue(badType.reason().contains("无法确定灰度窗口"), badType.reason());

        AigCanaryEvidence nullId = service.canaryEvidence("AGENT_VERSION", null);
        assertFalse(nullId.satisfied());
        assertTrue(nullId.reason().contains("无法确定灰度窗口"), nullId.reason());
    }

    @Test
    @DisplayName("★ 严重错误只统计严重分类：同一次取数里的限流/超时不算严重")
    void severeCountsOnlySevereClasses() {
        when(releaseEventMapper.selectOne(any()))
            .thenReturn(candidateEvent(LocalDateTime.now().minusHours(1)));
        when(auditMapper.countByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(100L);
        when(auditMapper.countFailedByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(5L);
        when(auditMapper.listErrorClassCountsSince(eq(VERSION_ID), any())).thenReturn(List.of(
            count("TIMEOUT", 3L), count("RATE_LIMITED", 1L), count("AUTH_FAILED", 1L)));

        AigCanaryEvidence evidence = service.canaryEvidence("AGENT_VERSION", VERSION_ID);

        assertEquals(1L, evidence.severeErrorCount(), "只有 AUTH_FAILED 算严重");
        assertEquals(1L, evidence.severeByClass().get("AUTH_FAILED"));
        assertFalse(evidence.severeByClass().containsKey("TIMEOUT"));
        assertFalse(evidence.satisfied(), "有 1 个严重错误，哪怕失败率没超标也不能转正式");
        assertTrue(evidence.reason().contains("出现严重错误"), evidence.reason());
    }

    @Test
    @DisplayName("取数为空（无调用）→ 不达标且失败率为 0，不抛异常")
    void emptyAggregatesDoNotBlowUp() {
        when(releaseEventMapper.selectOne(any()))
            .thenReturn(candidateEvent(LocalDateTime.now().minusHours(1)));
        when(auditMapper.countByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(0L);
        when(auditMapper.countFailedByAgentVersionSince(eq(VERSION_ID), any())).thenReturn(0L);
        when(auditMapper.listErrorClassCountsSince(eq(VERSION_ID), any())).thenReturn(null);

        AigCanaryEvidence evidence = service.canaryEvidence("AGENT_VERSION", VERSION_ID);

        assertFalse(evidence.satisfied());
        assertEquals(0d, evidence.failureRate(), 1e-9);
        assertTrue(evidence.reason().contains("调用次数不足"), evidence.reason());
    }

}
