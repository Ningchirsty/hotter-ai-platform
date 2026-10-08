package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.config.AigTaskSchedulerProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.enums.AigTaskExecutionModeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务调度器行为测试。
 *
 * <p><b>为什么状态过滤值必须逐条钉住</b>：调度器的全部工作就是「按状态捞出该动的任务」。
 * 状态值写错（比如把 {@code RETRY_WAIT} 写成 {@code RETRYING}）时它会安静地一条都捞不到，
 * 表现为「重试功能上了但任务永远不动」——而日志里连一行错误都没有。
 * 因此这里直接从 wrapper 的<b>参数值</b>断言状态字面量：
 * MyBatis-Plus 的 SQL 片段里只有占位符，光看 SQL 是看不出值的。</p>
 *
 * <p>另一类必须钉住的是「抢输怎么办」：多实例下两个调度器会扫到同一个任务，
 * 抢输的一方必须计入 skipped 而不是报错、更不能覆盖对方的结论。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskSchedulerImplTest {

    private AigTaskMapper taskMapper;
    private IAigTaskService taskService;
    private AigTaskSchedulerProperties properties;
    private AigTaskSchedulerImpl scheduler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigTask.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(AigTaskMapper.class);
        taskService = mock(IAigTaskService.class);
        properties = new AigTaskSchedulerProperties();
        scheduler = new AigTaskSchedulerImpl(taskMapper, taskService, properties);
    }

    private static AigTask task(long taskId, String status, int version) {
        AigTask task = new AigTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setVersion(version);
        task.setAttemptNo(1);
        task.setMaxAttempt(3);
        return task;
    }

    /**
     * 取第 n 次 selectList 的查询参数值集合。
     *
     * <p><b>必须先调用一次 {@code getSqlSegment()}</b>：MyBatis-Plus 的参数值是<b>惰性</b>写进
     * {@code paramNameValuePairs} 的——只有生成 SQL 片段时才会把条件里的实际值登记进去。
     * 直接读参数表会得到空集合，于是断言看起来像「条件没加」，其实是读早了。</p>
     *
     * @param index 第几次（从 0 起）
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    private Collection<Object> paramsOf(int index) {
        ArgumentCaptor<LambdaQueryWrapper<AigTask>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper, times(2)).selectList(captor.capture());
        LambdaQueryWrapper<AigTask> wrapper = captor.getAllValues().get(index);
        // 触发参数物化
        wrapper.getSqlSegment();
        return (Collection<Object>) wrapper.getParamNameValuePairs().values();
    }

    @Test
    @DisplayName("重试驱动：把 RETRY_WAIT 任务重新入队到 QUEUED")
    void drivesRetryWaitBackToQueued() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "RETRY_WAIT", 4)), List.of());

        AigTaskSweepVo result = scheduler.sweep();

        assertEquals(1, result.getRetryCandidate());
        assertEquals(1, result.getRetried());
        verify(taskService).transition(eq(1L), eq(4), eq(AigTaskStatusEnum.QUEUED), any(), eq(null));
    }

    @Test
    @DisplayName("重试驱动的状态过滤必须是 RETRY_WAIT（写错就会一条都捞不到，且不报错）")
    void retryQueryFiltersByRetryWait() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        scheduler.sweep();

        assertTrue(paramsOf(0).contains(AigTaskStatusEnum.RETRY_WAIT.getCode()),
            "重试扫描必须按 RETRY_WAIT 过滤；实际参数=" + paramsOf(0));
    }

    @Test
    @DisplayName("★ 两个清扫都只碰「平台执行」的任务：业务域执行的任务被重排会再跑一遍、被判超时会报假账")
    void sweepsOnlyConsiderPlatformTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        scheduler.sweep();

        assertTrue(paramsOf(0).contains(AigTaskExecutionModeEnum.PLATFORM.getCode()),
            "重试重排必须带 execution_mode=PLATFORM；实际参数=" + paramsOf(0));
        assertTrue(paramsOf(1).contains(AigTaskExecutionModeEnum.PLATFORM.getCode()),
            "超时清扫必须带 execution_mode=PLATFORM；实际参数=" + paramsOf(1));
    }

    @Test
    @DisplayName("超时扫描：在途（DISPATCHED/RUNNING）任务按超时记失败，且归类为可重试的 TIMEOUT")
    void sweepsStuckInFlightTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of(task(2L, "RUNNING", 7)));

        AigTaskSweepVo result = scheduler.sweep();

        assertEquals(1, result.getTimeoutCandidate());
        assertEquals(1, result.getTimedOut());
        // 归类为 TIMEOUT（可重试）而不是判死：超时是典型可恢复错误，重试往往就能过
        verify(taskService).recordFailure(eq(2L), eq(7), eq(AigErrorClassEnum.TIMEOUT), any());
    }

    @Test
    @DisplayName("超时扫描的状态过滤必须含已派发与执行中两种（漏一种就有一批任务永远不会超时）")
    void timeoutQueryCoversBothInFlightStatuses() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        scheduler.sweep();

        Collection<Object> params = paramsOf(1);
        assertTrue(params.contains(AigTaskStatusEnum.DISPATCHED.getCode())
                && params.contains(AigTaskStatusEnum.RUNNING.getCode()),
            "漏掉任一在途状态都会让那批任务永远不被判超时；实际参数=" + params);
    }

    @Test
    @DisplayName("抢输的一方计入 skipped 且不报错：多实例同时扫到同一任务时必须允许「什么都不做」")
    void concurrentLoserCountsAsSkipped() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "RETRY_WAIT", 4), task(3L, "RETRY_WAIT", 2)),
            List.of());
        when(taskService.transition(eq(1L), anyInt(), any(), any(), eq(null))).thenReturn(null);
        when(taskService.transition(eq(3L), anyInt(), any(), any(), eq(null)))
            .thenThrow(new ServiceException("任务状态已被并发修改，请重新读取后重试"));

        AigTaskSweepVo result = scheduler.sweep();

        assertEquals(1, result.getRetried());
        assertEquals(1, result.getSkipped(), "并发冲突必须计入 skipped；当成错误会让人以为调度坏了");
        assertTrue(result.getFailures().isEmpty(), "并发冲突不是失败，不该进 failures：" + result.getFailures());
    }

    @Test
    @DisplayName("单条处理失败不影响其余：逐条记原因，不整轮中断")
    void oneBadTaskDoesNotAbortTheSweep() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "RETRY_WAIT", 4), task(3L, "RETRY_WAIT", 2)),
            List.of());
        when(taskService.transition(eq(1L), anyInt(), any(), any(), eq(null)))
            .thenThrow(new IllegalStateException("数据库连接断了"));
        when(taskService.transition(eq(3L), anyInt(), any(), any(), eq(null))).thenReturn(null);

        AigTaskSweepVo result = scheduler.sweep();

        assertEquals(1, result.getRetried(), "后面的任务仍要被处理");
        assertEquals(1, result.getFailures().size(), "失败要逐条给出，便于定位个别异常");
        assertTrue(result.getFailures().get(0).contains("1"), "失败原因要带上任务ID；实际=" + result.getFailures());
    }

    @Test
    @DisplayName("无候选时不做任何推进（不做无意义的写库）")
    void emptySweepDoesNothing() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        AigTaskSweepVo result = scheduler.sweep();

        assertEquals(0, result.getRetried());
        assertEquals(0, result.getTimedOut());
        verify(taskService, never()).transition(any(), any(), any(), any(), any());
        verify(taskService, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("批量上限为 0 时按 1 处理，不能生成 limit 0 而永远扫不到东西")
    void zeroBatchSizeFallsBackToOne() {
        properties.setBatchSize(0);
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        scheduler.sweep();

        ArgumentCaptor<LambdaQueryWrapper<AigTask>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper, times(2)).selectList(captor.capture());
        String segment = captor.getAllValues().get(0).getSqlSegment();
        assertTrue(segment.contains("limit 1"),
            "批量上限配成 0 时必须退化为 limit 1（limit 0 会让扫描永远捞不到任务）；实际=" + segment);
        assertTrue(!segment.contains("limit 0"), "绝不能出现 limit 0；实际=" + segment);
    }

    @Test
    @DisplayName("扫描结果对象不为空且各项可读（供接口直接返回）")
    void sweepReturnsReadableResult() {
        when(taskMapper.selectList(any())).thenReturn(List.of(), List.of());

        AigTaskSweepVo result = scheduler.sweep();

        assertNotNull(result);
        assertNotNull(result.getFailures());
        assertTrue(result.getFailures().isEmpty());
    }

}
