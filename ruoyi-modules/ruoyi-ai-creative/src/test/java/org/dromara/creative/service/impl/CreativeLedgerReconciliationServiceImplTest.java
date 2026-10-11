package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTask;
import org.dromara.content.mapper.CpTaskMapper;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.CreativeLedgerReconciliationVo;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 创作域候选侧父子对账测试（增量 19）。
 *
 * <p>钉住对账的三条口径：①父边来自项目 {@code platform_task_id}，非派发项目父任务为 null
 * 且<b>不算缺失</b>；②逐候选按 {@code CreativeTaskStatusMapper} 的同一张映射表核对，
 * 不一致给出可读漂移原因；③未登记 / 任务读不到 / 状态无法映射分别如实归类，不猜成"一致"。</p>
 *
 * @author creative
 */
class CreativeLedgerReconciliationServiceImplTest {

    private CpTaskMapper taskMapper;
    private DpGenerationMapper generationMapper;
    private IAigTaskService aigTaskService;
    private CreativeLedgerReconciliationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, DpGeneration.class);
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(CpTaskMapper.class);
        generationMapper = mock(DpGenerationMapper.class);
        aigTaskService = mock(IAigTaskService.class);
        service = new CreativeLedgerReconciliationServiceImpl(taskMapper, generationMapper, aigTaskService);
    }

    @Test
    @DisplayName("非派发项目：父任务为 null、不算缺失；候选照常对账")
    void nonDispatchedProjectHasNoParentButNoMissingFlag() {
        when(taskMapper.selectById(701L)).thenReturn(project(null));
        when(generationMapper.selectList(any())).thenReturn(List.of(candidate(1L, 1, "SUCCEEDED", 9101L)));
        when(aigTaskService.getTask(9101L)).thenReturn(task(9101L, "SUCCEEDED"));

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertNull(vo.getParent());
        assertNull(vo.getPlatformTaskId());
        assertFalse(vo.getSummary().parentMissing());
        assertEquals(1, vo.getSummary().total());
        assertEquals(1, vo.getSummary().consistent());
    }

    @Test
    @DisplayName("★派发项目：父任务是场景派发的那条，externalRef 从派发快照取出")
    void dispatchedProjectExposesParentWithExternalRef() {
        when(taskMapper.selectById(701L)).thenReturn(project(88L));
        when(aigTaskService.getTask(88L)).thenReturn(platformTask(88L));
        when(generationMapper.selectList(any())).thenReturn(List.of());

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertEquals(88L, vo.getPlatformTaskId());
        assertNotNull(vo.getParent());
        assertEquals("AIG-88", vo.getParent().taskNo());
        assertEquals("CREATIVE_FULL", vo.getParent().scenarioCode());
        assertEquals("701", vo.getParent().externalRef(), "externalRef 应解析自派发快照");
        assertEquals(0, vo.getSummary().total());
    }

    @Test
    @DisplayName("★漂移：候选已成功但任务还在跑 → 报不一致并给出可读原因")
    void driftBetweenCandidateAndLedgerIsReported() {
        when(taskMapper.selectById(701L)).thenReturn(project(88L));
        when(aigTaskService.getTask(88L)).thenReturn(platformTask(88L));
        when(generationMapper.selectList(any())).thenReturn(List.of(
            candidate(1L, 1, "SUCCEEDED", 9101L),
            candidate(2L, 2, "FAILED", 9102L)));
        when(aigTaskService.getTask(9101L)).thenReturn(task(9101L, "RUNNING"));
        when(aigTaskService.getTask(9102L)).thenReturn(task(9102L, "FAILED"));

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertEquals(2, vo.getSummary().total());
        assertEquals(1, vo.getSummary().drifted());
        assertEquals(1, vo.getSummary().consistent());
        CreativeLedgerReconciliationVo.ChildRow drifted = vo.getChildren().get(0);
        assertEquals(Boolean.FALSE, drifted.consistent());
        assertEquals("SUCCEEDED", drifted.expectedLedgerStatus());
        assertTrue(drifted.drift().contains("RUNNING"), drifted.drift());
        assertNull(vo.getChildren().get(1).drift());
    }

    @Test
    @DisplayName("未登记候选：标记 unregistered 并说明治理台看不到它")
    void unregisteredCandidateIsFlagged() {
        when(taskMapper.selectById(701L)).thenReturn(project(88L));
        when(aigTaskService.getTask(88L)).thenReturn(platformTask(88L));
        when(generationMapper.selectList(any())).thenReturn(List.of(candidate(1L, 1, "SUCCEEDED", null)));

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertEquals(1, vo.getSummary().unregistered());
        CreativeLedgerReconciliationVo.ChildRow row = vo.getChildren().get(0);
        assertNull(row.ledgerTaskId());
        assertTrue(row.drift().contains("未登记"), row.drift());
    }

    @Test
    @DisplayName("人工结论：候选 APPROVED/REJECTED 与任务同名状态一致")
    void humanDecisionMapsToSameNamedTaskStatus() {
        when(taskMapper.selectById(701L)).thenReturn(project(null));
        when(generationMapper.selectList(any())).thenReturn(List.of(
            candidate(1L, 1, "APPROVED", 9101L),
            candidate(2L, 2, "REJECTED", 9102L)));
        when(aigTaskService.getTask(9101L)).thenReturn(task(9101L, "APPROVED"));
        when(aigTaskService.getTask(9102L)).thenReturn(task(9102L, "REJECTED"));

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertEquals(2, vo.getSummary().consistent());
        assertEquals("APPROVED", vo.getChildren().get(0).expectedLedgerStatus());
        assertEquals("REJECTED", vo.getChildren().get(1).expectedLedgerStatus());
    }

    @Test
    @DisplayName("父任务读不到：parentMissing 为 true（数据缺失要显式暴露）")
    void missingParentIsFlagged() {
        when(taskMapper.selectById(701L)).thenReturn(project(88L));
        when(aigTaskService.getTask(88L)).thenThrow(new ServiceException("任务不存在"));
        when(generationMapper.selectList(any())).thenReturn(List.of());

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertNull(vo.getParent());
        assertTrue(vo.getSummary().parentMissing());
        assertFalse(vo.getSummary().drifted() > 0);
    }

    @Test
    @DisplayName("登记任务读不到 / 候选状态无法映射：如实归类，不猜成一致")
    void unreadableLedgerTaskAndUnknownStatusAreNotAssumedConsistent() {
        when(taskMapper.selectById(701L)).thenReturn(project(null));
        when(generationMapper.selectList(any())).thenReturn(List.of(
            candidate(1L, 1, "SUCCEEDED", 9101L),
            candidate(2L, 2, "SOMETHING_NEW", 9102L)));
        when(aigTaskService.getTask(9101L)).thenThrow(new ServiceException("任务不存在"));
        when(aigTaskService.getTask(9102L)).thenReturn(task(9102L, "RUNNING"));

        CreativeLedgerReconciliationVo vo = service.reconcile(701L);

        assertEquals(1, vo.getSummary().drifted(), "任务读不到按漂移计入");
        assertTrue(vo.getChildren().get(0).drift().contains("读不到"));
        CreativeLedgerReconciliationVo.ChildRow unknown = vo.getChildren().get(1);
        assertNull(unknown.consistent(), "无法映射时应答'判不了'，而不是一致");
        assertNull(unknown.expectedLedgerStatus());
        assertTrue(unknown.drift().contains("无法映射"));
    }

    @Test
    @DisplayName("项目不存在或ID为空：报错而不是返回空视图")
    void missingProjectIsRejected() {
        assertThrows(ServiceException.class, () -> service.reconcile(null));
        when(taskMapper.selectById(701L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.reconcile(701L));
    }

    private static CpTask project(Long platformTaskId) {
        CpTask project = new CpTask();
        project.setTaskId(701L);
        project.setPlatformTaskId(platformTaskId);
        return project;
    }

    private static DpGeneration candidate(Long id, Integer no, String status, Long aigTaskId) {
        DpGeneration generation = new DpGeneration();
        generation.setId(id);
        generation.setTaskId(701L);
        generation.setCandidateNo(no);
        generation.setStatus(status);
        generation.setAigTaskId(aigTaskId);
        return generation;
    }

    private static AigTask task(Long taskId, String status) {
        AigTask task = new AigTask();
        task.setTaskId(taskId);
        task.setTaskNo("AIG-" + taskId);
        task.setStatus(status);
        task.setExecutionMode("EXTERNAL");
        task.setProviderCode("IMAGE_KERNEL");
        return task;
    }

    private static AigTask platformTask(Long taskId) {
        AigTask task = new AigTask();
        task.setTaskId(taskId);
        task.setTaskNo("AIG-88");
        task.setTaskType("SCENARIO");
        task.setStatus("SUCCEEDED");
        task.setExecutionMode("PLATFORM");
        task.setScenarioCode("CREATIVE_FULL");
        task.setRouteSnapshot("{\"dispatch\":\"CREATIVE_EXISTING_FLOW\",\"externalRef\":\"701\","
            + "\"handoff\":\"HANDOFF_COMPLETE\"}");
        return task;
    }

}
