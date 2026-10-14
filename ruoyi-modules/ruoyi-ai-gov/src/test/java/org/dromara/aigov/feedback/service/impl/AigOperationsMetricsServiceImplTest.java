package org.dromara.aigov.feedback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.feedback.metrics.AigOperationsMetrics;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskResult;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.mapper.AigTaskResultMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运营指标聚合测试（设计 §6.9）。
 *
 * <p>钉住"累计成果取经确认的结果"与"算不出来的项如实置 0"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigOperationsMetricsServiceImplTest {

    private AigTaskMapper taskMapper;
    private AigTaskResultMapper taskResultMapper;
    private AigOperationsMetricsServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigTask.class);
        TableInfoHelper.initTableInfo(assistant, AigTaskResult.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(AigTaskMapper.class);
        taskResultMapper = mock(AigTaskResultMapper.class);
        service = new AigOperationsMetricsServiceImpl(taskMapper, taskResultMapper);
    }

    @Test
    @DisplayName("★ 聚合：完成任务数/首次通过/返工/策略阻断/累计成果按序取数，口径与纯函数一致")
    void aggregatesCounts() {
        // 顺序：completed → firstPass → rework → policyBlocked
        when(taskMapper.selectCount(any())).thenReturn(100L, 60L, 20L, 7L);
        when(taskResultMapper.selectCount(any())).thenReturn(50L);

        AigOperationsMetrics.Result result = service.compute();

        assertEquals(100L, result.completedTasks());
        assertEquals(50L, result.confirmedResults(), "累计成果 = 经确认的结果");
        assertEquals(0.5, result.adoptionRate());
        assertEquals(0.6, result.firstPassRate());
        assertEquals(0.2, result.reworkRate());
        assertEquals(7L, result.policyBlocked());
        assertEquals(0d, result.citationValidRate(), "引用有效率暂无可聚合数据源，如实为 0");
        assertEquals(BigDecimal.ZERO, result.modelCost());
    }

    @Test
    @DisplayName("空库：count 为空视为 0，不抛异常")
    void emptyDatabaseIsSafe() {
        when(taskMapper.selectCount(any())).thenReturn(null);
        when(taskResultMapper.selectCount(any())).thenReturn(null);

        AigOperationsMetrics.Result result = service.compute();

        assertEquals(0L, result.completedTasks());
        assertEquals(0d, result.adoptionRate());
    }

    @Test
    @DisplayName("★ 故障分类聚合：按错误分类编码计数")
    void faultClassCounts() {
        AigTask a = new AigTask();
        a.setErrorCode("TIMEOUT");
        AigTask b = new AigTask();
        b.setErrorCode("TIMEOUT");
        AigTask c = new AigTask();
        c.setErrorCode("POLICY_DENIED");
        when(taskMapper.selectList(any())).thenReturn(List.of(a, b, c));

        Map<String, Long> counts = service.faultClassCounts();

        assertEquals(2L, counts.get("TIMEOUT"));
        assertEquals(1L, counts.get("POLICY_DENIED"));
    }

}
