package org.dromara.creative.helper;

import org.dromara.creative.domain.DpProjectStepState;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.mapper.DpProjectStepStateMapper;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 步骤状态写入者的单元测试（V0.2 D2）。
 *
 * <p>钉住四件事（都属于"写错了也不报错、只是页面显示不对"的静默失败）：</p>
 * <ol>
 *   <li>第一次同步：配置里的每一步都落库，状态与投影一致，DONE 的行有时间戳；</li>
 *   <li>状态没变就不写库（阶段反复重算不该产生无意义的 update）；</li>
 *   <li>状态变了才更新，并且 <b>时间戳只补不清</b>（返工时"曾经完成过"的痕迹要留着）；</li>
 *   <li>没有步骤配置时 <b>什么都不写</b>（不能凭 0 行配置去写假状态）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeStepStateWriterTest {

    @Mock
    private DpProjectStepStateMapper stepStateMapper;

    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;

    @InjectMocks
    private CreativeStepStateWriter writer;

    private static final Long TASK_ID = 2100000000000000123L;

    /**
     * 造配置步骤。
     *
     * @param profileId 场景档案ID
     * @param rows      依次为 stepCode / stepName / stageCodes / sortNo
     * @return 步骤列表
     */
    private static List<DpScenarioStep> steps(Long profileId, Object[]... rows) {
        List<DpScenarioStep> list = new ArrayList<>();
        for (Object[] row : rows) {
            DpScenarioStep step = new DpScenarioStep();
            step.setProfileId(profileId);
            step.setStepCode((String) row[0]);
            step.setStepName((String) row[1]);
            step.setStageCodes((String) row[2]);
            step.setSortNo((Integer) row[3]);
            list.add(step);
        }
        return list;
    }

    /**
     * 简化的三步配置：INPUT(MATERIAL_READY) → DNA(DNA_*) → GENERATION(PRODUCING)。
     *
     * @return 步骤列表
     */
    private static List<DpScenarioStep> threeSteps() {
        return steps(777L,
            new Object[]{"INPUT", "资料", "MATERIAL_READY", 10},
            new Object[]{"DNA", "基因", "DNA_GENERATING,DNA_REVIEW,DNA_LOCKED", 20},
            new Object[]{"GENERATION", "出图", "PRODUCING", 30});
    }

    /**
     * 造一行已有的步骤状态。
     *
     * @param stepCode  步骤编码
     * @param status    状态
     * @param startedAt 开始时间
     * @return 行
     */
    private static DpProjectStepState row(String stepCode, String status, LocalDateTime startedAt) {
        DpProjectStepState state = new DpProjectStepState();
        state.setTaskId(TASK_ID);
        state.setStepCode(stepCode);
        state.setStatus(status);
        state.setStartedAt(startedAt);
        return state;
    }

    @Test
    @DisplayName("第一次同步：配置里每一步都落库，状态与投影一致，时间戳只给该给的")
    void firstSyncInsertsEveryStep() {
        when(scenarioConfigService.listSteps("ECOM_DETAIL")).thenReturn(threeSteps());
        when(stepStateMapper.selectList(any())).thenReturn(List.of());

        int touched = writer.sync(TASK_ID, "DNA_REVIEW", "ECOM_DETAIL");

        ArgumentCaptor<DpProjectStepState> captor = ArgumentCaptor.forClass(DpProjectStepState.class);
        verify(stepStateMapper, times(3)).insert(captor.capture());
        verify(stepStateMapper, never()).updateById(any(DpProjectStepState.class));
        assertEquals(3, touched);

        List<DpProjectStepState> inserted = captor.getAllValues();
        assertEquals(List.of("INPUT", "DNA", "GENERATION"),
            inserted.stream().map(DpProjectStepState::getStepCode).toList());
        assertEquals(List.of("DONE", "ACTIVE", "PENDING"),
            inserted.stream().map(DpProjectStepState::getStatus).toList());
        assertEquals(List.of(10, 20, 30),
            inserted.stream().map(DpProjectStepState::getSortNo).toList());
        assertEquals("资料", inserted.get(0).getStepName());
        for (DpProjectStepState state : inserted) {
            assertEquals(TASK_ID, state.getTaskId());
            assertEquals(777L, state.getProfileId(), "档案ID 取自配置步骤");
            assertEquals("DNA_REVIEW", state.getStageCode(), "记录是哪次阶段变更把它推到该状态");
        }
        assertNotNull(inserted.get(0).getStartedAt(), "DONE 行要有开始时间");
        assertNotNull(inserted.get(0).getCompletedAt(), "DONE 行要有完成时间");
        assertNotNull(inserted.get(1).getStartedAt(), "进行中要有开始时间");
        assertNull(inserted.get(1).getCompletedAt(), "进行中不该有完成时间");
        assertNull(inserted.get(2).getStartedAt(), "待办不该有开始时间");
        assertNull(inserted.get(2).getCompletedAt());
    }

    @Test
    @DisplayName("状态没变就不写库（同一步骤内的阶段推进不产生无意义 update）")
    void unchangedStatusIsNotWritten() {
        when(scenarioConfigService.listSteps("ECOM_DETAIL")).thenReturn(threeSteps());
        when(stepStateMapper.selectList(any())).thenReturn(List.of(
            row("INPUT", "DONE", LocalDateTime.now()),
            row("DNA", "ACTIVE", LocalDateTime.now()),
            row("GENERATION", "PENDING", null)));

        // DNA_REVIEW → DNA_LOCKED：配置里两者都落在 DNA 这一步，三步状态一个都没变
        assertEquals(0, writer.sync(TASK_ID, "DNA_LOCKED", "ECOM_DETAIL"));

        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
        verify(stepStateMapper, never()).updateById(any(DpProjectStepState.class));
    }

    @Test
    @DisplayName("状态变了才更新，且时间戳只补不清（返工不抹掉已完成痕迹）")
    void changedStatusUpdatesAndKeepsTimestamps() {
        when(scenarioConfigService.listSteps("ECOM_DETAIL")).thenReturn(threeSteps());
        List<DpProjectStepState> table = new ArrayList<>();
        LocalDateTime inputStart = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime inputDone = LocalDateTime.of(2026, 1, 1, 8, 30);
        LocalDateTime dnaStart = LocalDateTime.of(2026, 1, 1, 9, 0);
        DpProjectStepState input = row("INPUT", "DONE", inputStart);
        input.setCompletedAt(inputDone);
        DpProjectStepState dna = row("DNA", "ACTIVE", dnaStart);
        table.add(input);
        table.add(dna);
        when(stepStateMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(table));

        // 前进到出图：DNA 完成、GENERATION 首次出现
        assertEquals(2, writer.sync(TASK_ID, "PRODUCING", "ECOM_DETAIL"));
        verify(stepStateMapper, times(1)).updateById(any(DpProjectStepState.class));
        ArgumentCaptor<DpProjectStepState> inserted =
            ArgumentCaptor.forClass(DpProjectStepState.class);
        verify(stepStateMapper, times(1)).insert(inserted.capture());
        table.add(inserted.getValue());

        assertEquals("DONE", dna.getStatus());
        assertEquals(dnaStart, dna.getStartedAt(), "开始时间保持不动");
        assertNotNull(dna.getCompletedAt(), "进入 DONE 补完成时间");
        LocalDateTime dnaDone = dna.getCompletedAt();
        assertEquals("PRODUCING", dna.getStageCode(), "stage_code 记的是最近一次推动它的阶段");
        assertEquals("ACTIVE", inserted.getValue().getStatus(), "出图这一步开始");
        assertEquals("GENERATION", inserted.getValue().getStepCode());
        assertEquals("DONE", input.getStatus(), "INPUT 早已完成，这一轮不该被动");

        // 返工回资料就绪：三步状态都变，但已完成的时间戳留着
        assertEquals(3, writer.sync(TASK_ID, "MATERIAL_READY", "ECOM_DETAIL"));
        assertEquals("ACTIVE", input.getStatus(), "回到资料就绪→资料这一步重新进行中");
        assertEquals(inputDone, input.getCompletedAt(), "返工不抹掉完成时间（曾经做过的痕迹）");
        assertEquals("PENDING", dna.getStatus(), "基因回到待办");
        assertEquals(dnaDone, dna.getCompletedAt());
        assertEquals("PENDING", inserted.getValue().getStatus());
        verify(stepStateMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("没有步骤配置：什么都不写、不抛异常（派生表写不了不该影响阶段变更）")
    void missingConfigWritesNothing() {
        when(scenarioConfigService.listSteps("SOMETHING_ELSE")).thenReturn(List.of());
        assertEquals(0, writer.sync(TASK_ID, "PRODUCING", "SOMETHING_ELSE"));
        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
        verify(stepStateMapper, never()).updateById(any(DpProjectStepState.class));
        verify(stepStateMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("待办步骤也落库：表里必须凑齐配置的每一步，否则界面会少一步")
    void pendingStepsAreStillPersisted() {
        when(scenarioConfigService.listSteps("ECOM_DETAIL")).thenReturn(threeSteps());
        when(stepStateMapper.selectList(any())).thenReturn(List.of());
        writer.sync(TASK_ID, "MATERIAL_READY", "ECOM_DETAIL");
        ArgumentCaptor<DpProjectStepState> captor = ArgumentCaptor.forClass(DpProjectStepState.class);
        verify(stepStateMapper, times(3)).insert(captor.capture());
        List<String> statuses = captor.getAllValues().stream()
            .map(DpProjectStepState::getStatus).toList();
        assertEquals(List.of("ACTIVE", "PENDING", "PENDING"), statuses);
        assertTrue(statuses.contains("PENDING"));
    }

    @Test
    @DisplayName("已交付：全部 DONE，且每步都带完成时间")
    void completedStageMarksAllDone() {
        when(scenarioConfigService.listSteps("ECOM_DETAIL")).thenReturn(threeSteps());
        when(stepStateMapper.selectList(any())).thenReturn(List.of());
        writer.sync(TASK_ID, "COMPLETED", "ECOM_DETAIL");
        ArgumentCaptor<DpProjectStepState> captor = ArgumentCaptor.forClass(DpProjectStepState.class);
        verify(stepStateMapper, times(3)).insert(captor.capture());
        for (DpProjectStepState state : captor.getAllValues()) {
            assertEquals("DONE", state.getStatus(), state.getStepCode());
            assertNotNull(state.getCompletedAt(), state.getStepCode());
        }
    }
}
