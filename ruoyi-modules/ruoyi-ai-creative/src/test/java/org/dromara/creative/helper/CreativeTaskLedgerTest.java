package org.dromara.creative.helper;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创作域登记/回写接线测试（{@link CreativeTaskLedger}，一候选一任务）。
 *
 * <p><b>为什么这些断言值得写</b>：这一段跨了两个模块，而它出错的样子是**静默的**——
 * 任务信息填错（等级、能力、快照）只会让治理台显示错数据；回写漏了或写错顺序只会让任务
 * 永远停在 `DISPATCHED`。所以逐项钉住：填进去的每个字段、回写的每一步、以及每一步用的
 * 乐观锁版本（两步必须是**链式**的，第二跳用第一跳返回的新版本）。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeTaskLedgerTest {

    private static final long GENERATION_ID = 5001L;
    private static final long CREATIVE_TASK_ID = 9001L;
    private static final long IMAGE_TASK_ID = 777L;
    private static final long AIG_TASK_ID = 4242L;

    private IAigTaskService taskService;
    private CreativeTaskLedger ledger;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        ledger = new CreativeTaskLedger(taskService);
    }

    private static DpGeneration candidate() {
        DpGeneration row = new DpGeneration();
        row.setId(GENERATION_ID);
        row.setTaskId(CREATIVE_TASK_ID);
        row.setCandidateNo(2);
        row.setWorkflowCode("WF-HERO-001");
        row.setInputJson("{\"prompt\":\"a cat\",\"seed\":7}");
        row.setNegativePrompt("blurry");
        row.setImageTaskId(IMAGE_TASK_ID);
        row.setStatus(DpGenerationStatusEnum.QUEUED.getCode());
        return row;
    }

    private static CreativeProjectVo project(String dataLevel) {
        CreativeProjectVo vo = new CreativeProjectVo();
        vo.setDataLevel(dataLevel);
        return vo;
    }

    private static AigTask task(String status, int version) {
        AigTask task = new AigTask();
        task.setTaskId(AIG_TASK_ID);
        task.setStatus(status);
        task.setVersion(version);
        return task;
    }

    // ------------------------------------------------------------------ 登记

    @Test
    @DisplayName("★ 登记：能力/业务域/项目/等级/快照/幂等键与内核作业号都要如实传过去")
    void registerBuildsTaskFromCandidate() {
        when(taskService.createDispatched(any(), any(), any())).thenReturn(task("DISPATCHED", 0));

        Long taskId = ledger.register(candidate(), project("RESTRICTED"));

        assertEquals(AIG_TASK_ID, taskId);
        ArgumentCaptor<AigTaskCreateBo> boCaptor = ArgumentCaptor.forClass(AigTaskCreateBo.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jobCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).createDispatched(boCaptor.capture(), providerCaptor.capture(), jobCaptor.capture());
        AigTaskCreateBo bo = boCaptor.getValue();
        assertEquals("IMAGE_GENERATION", bo.getTaskType());
        assertEquals(CreativeTaskLedger.CAPABILITY_CODE, bo.getCapabilityCode(),
            "业务上要的是「出图」，与它由内核还是外部模型服务无关");
        assertEquals(CreativeTaskLedger.PROJECT_TYPE, bo.getProjectType());
        assertEquals(CREATIVE_TASK_ID, bo.getProjectId(), "业务对象＝视觉项目（cp_task.task_id）");
        assertEquals("RESTRICTED", bo.getDataLevel(), "数据等级取项目上声明的那个——有真相源就别猜");
        assertEquals("N", bo.getAllowExternal(), "出图走本地/内网内核：业务侧不允许外发");
        assertEquals("creative:generation:" + GENERATION_ID, bo.getIdempotencyKey(),
            "一候选一任务：幂等键必须只由候选主键决定");
        assertEquals("{\"prompt\":\"a cat\",\"seed\":7}", bo.getSnapshotJson(),
            "快照必须与 dp_generation.input_json 是同一串字节（同一个「当时按什么出的」答案）");
        assertEquals("blurry", bo.getNegativeConstraints());
        assertTrue(bo.getRemark().contains("#2"), "备注要能认出是哪个候选：" + bo.getRemark());
        assertEquals(CreativeTaskLedger.PROVIDER_CODE, providerCaptor.getValue());
        assertEquals(String.valueOf(IMAGE_TASK_ID), jobCaptor.getValue(),
            "外部作业号＝内核任务号：排障时靠它把治理任务与出图真相对上");
    }

    @Test
    @DisplayName("项目未声明数据等级 → 按 INTERNAL 兜底（与创作域 Brain 适配器同口径），不新造失败点")
    void registerFallsBackToInternal() {
        when(taskService.createDispatched(any(), any(), any())).thenReturn(task("DISPATCHED", 0));

        ledger.register(candidate(), project("  "));

        ArgumentCaptor<AigTaskCreateBo> captor = ArgumentCaptor.forClass(AigTaskCreateBo.class);
        verify(taskService).createDispatched(captor.capture(), any(), any());
        assertEquals("INTERNAL", captor.getValue().getDataLevel());
    }

    @Test
    @DisplayName("候选没主键 / 没快照 → 拒绝登记（登记没有意义，也不该静默跳过）")
    void registerRejectsIncompleteCandidate() {
        DpGeneration noId = candidate();
        noId.setId(null);
        assertThrows(ServiceException.class, () -> ledger.register(noId, project("INTERNAL")));

        DpGeneration noSnapshot = candidate();
        noSnapshot.setInputJson(null);
        assertThrows(ServiceException.class, () -> ledger.register(noSnapshot, project("INTERNAL")));

        verify(taskService, never()).createDispatched(any(), any(), any());
    }

    // ------------------------------------------------------------------ 内核状态回写

    @Test
    @DisplayName("未登记的候选（历史行）→ 什么都不做，绝不新建任务")
    void kernelWritebackSkipsUnregistered() {
        DpGeneration row = candidate();
        row.setAigTaskId(null);

        ledger.writebackKernel(row, DpGenerationStatusEnum.RUNNING);

        verify(taskService, never()).transition(any(), any(), any(), any(), any());
        verify(taskService, never()).getTask(any());
    }

    @Test
    @DisplayName("任务已在目标状态 → 不写第二个事件（刷新很频繁，重复事件会把事件流淹掉）")
    void kernelWritebackSkipsWhenUnchanged() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("RUNNING", 3));

        ledger.writebackKernel(row, DpGenerationStatusEnum.RUNNING);

        verify(taskService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 状态变化 → 用任务当前版本做 CAS，说明里带上候选ID与内核原因，失败还要带错误分类")
    void kernelWritebackTransitionsOnChange() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        row.setStatus(DpGenerationStatusEnum.FAILED.getCode());
        row.setErrorMessage("内核报错：模型加载失败");
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("RUNNING", 3));

        ledger.writebackKernel(row, DpGenerationStatusEnum.FAILED);

        ArgumentCaptor<Integer> versionCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AigErrorClassEnum> classCaptor = ArgumentCaptor.forClass(AigErrorClassEnum.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        // 失败走「带失败信息」的重载：错误分类与原因必须落到 aig_task.error_code/error_message，
        // 否则治理台那两列永远是空的（只知道失败、不知道是哪一类）
        verify(taskService).transition(eq(AIG_TASK_ID), versionCaptor.capture(),
            eq(AigTaskStatusEnum.FAILED), detailCaptor.capture(), any(),
            classCaptor.capture(), messageCaptor.capture());
        assertEquals(3, versionCaptor.getValue(), "必须带乐观锁版本：并发被改时要响亮地失败");
        assertTrue(detailCaptor.getValue().contains(String.valueOf(GENERATION_ID)), detailCaptor.getValue());
        assertTrue(detailCaptor.getValue().contains("模型加载失败"),
            "失败原因要进事件说明，否则治理台只知道失败、不知道为什么：" + detailCaptor.getValue());
        assertEquals(AigErrorClassEnum.UNKNOWN, classCaptor.getValue(),
            "内核错误码与平台分类未对齐，不猜；如实记 UNKNOWN（会转人工）");
        assertEquals("内核报错：模型加载失败", messageCaptor.getValue(),
            "失败原因要落到 error_message 列上");
    }

    @Test
    @DisplayName("成功回写不走「带失败信息」的重载（不该往成功的行上写错误码）")
    void kernelWritebackSuccessUsesPlainTransition() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("RUNNING", 3));

        ledger.writebackKernel(row, DpGenerationStatusEnum.SUCCEEDED);

        verify(taskService).transition(eq(AIG_TASK_ID), eq(3), eq(AigTaskStatusEnum.SUCCEEDED),
            any(), any());
        verify(taskService, never()).transition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("回写失败不向上抛（它是观测面，不该让创作域刷新失败）")
    void kernelWritebackSwallowsFailure() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("RUNNING", 3));
        when(taskService.transition(any(), any(), any(), any(), any()))
            .thenThrow(new ServiceException("并发冲突"));

        ledger.writebackKernel(row, DpGenerationStatusEnum.SUCCEEDED);
    }

    // ------------------------------------------------------------------ 人工结论回写

    @Test
    @DisplayName("★ 选定：两步且**链式**用版本——第二跳必须用第一跳返回的新版本")
    void decisionWritebackChainsTwoSteps() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("SUCCEEDED", 5));
        when(taskService.transition(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Integer version = inv.getArgument(1);
            AigTaskStatusEnum to = inv.getArgument(2);
            AigTask next = new AigTask();
            next.setTaskId(id);
            next.setStatus(to.getCode());
            next.setVersion(version + 1);
            return next;
        });

        ledger.writebackDecision(row, DpGenerationStatusEnum.APPROVED);

        ArgumentCaptor<Integer> versions = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<AigTaskStatusEnum> statuses = ArgumentCaptor.forClass(AigTaskStatusEnum.class);
        verify(taskService, org.mockito.Mockito.times(2))
            .transition(eq(AIG_TASK_ID), versions.capture(), statuses.capture(), any(), any());
        assertEquals(List.of(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.APPROVED),
            statuses.getAllValues(), "必须先经过「待人工复核」——任务侧的不变式是 APPROVED 必经 REVIEW_PENDING");
        assertEquals(List.of(5, 6), versions.getAllValues(),
            "第二跳要用第一跳返回的新版本；用旧版本会 CAS 失败（或更糟：覆盖别人的结论）");
    }

    @Test
    @DisplayName("任务已是结论态 → 不再写（幂等：重复选定不该反复写事件）")
    void decisionWritebackStopsWhenAlreadyDecided() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);
        when(taskService.getTask(AIG_TASK_ID)).thenReturn(task("APPROVED", 7));

        ledger.writebackDecision(row, DpGenerationStatusEnum.APPROVED);

        verify(taskService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("非人工结论（如已出图）→ 不产生任何回写")
    void decisionWritebackIgnoresNonDecision() {
        DpGeneration row = candidate();
        row.setAigTaskId(AIG_TASK_ID);

        ledger.writebackDecision(row, DpGenerationStatusEnum.SUCCEEDED);

        verify(taskService, never()).getTask(any());
        verify(taskService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("幂等键只由候选主键决定（同一个候选永远同一个键）")
    void idempotencyKeyIsStable() {
        assertEquals("creative:generation:5001",
            CreativeTaskLedger.idempotencyKey(5001L));
    }

}
