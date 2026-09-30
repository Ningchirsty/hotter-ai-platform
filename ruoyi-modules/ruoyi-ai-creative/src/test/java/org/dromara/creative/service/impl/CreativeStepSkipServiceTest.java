package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpProjectStepState;
import org.dromara.creative.domain.vo.ProjectStepStateVo;
import org.dromara.creative.helper.CreativeStepProjection;
import org.dromara.creative.mapper.DpProjectStepStateMapper;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「跳过步骤」服务（V0.2 R36）：允许条件、留痕与取消。
 *
 * <p>这里钉的是**拒绝面**：跳过是「少做一步」，最容易出的问题是它悄悄放行了不该放行的步骤
 * （必填步骤、有闸门的步骤、已经做完的步骤）。所以每条拒绝都要有自己的说法，
 * 用户看到提示就知道该去找谁，而不是一句「操作失败」。</p>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeStepSkipServiceTest {

    private static final Long TASK = 4242L;

    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;
    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private DpProjectStepStateMapper stepStateMapper;

    @InjectMocks
    private CreativeStepStateServiceImpl service;

    private void givenStep(ProjectStepStateVo step) {
        when(scenarioConfigService.listProjectSteps(TASK)).thenReturn(List.of(step));
    }

    private static ProjectStepStateVo step(String code, String status, boolean skippable, boolean gated) {
        return new ProjectStepStateVo(code, code + "（步骤名）", 80, status, "QA_PROCESSING",
            null, null, "DERIVED", skippable ? "0" : "1", gated, skippable, null, null);
    }

    @BeforeEach
    void setUp() {
        when(projectService.getProject(TASK)).thenReturn(null);
    }

    @Test
    @DisplayName("可跳过的步骤：写行（状态 SKIPPED + 原因进 remark）并留一条事件")
    void skipWritesRowAndEvent() {
        givenStep(step("QA", CreativeStepProjection.PENDING, true, false));
        when(stepStateMapper.selectList(any())).thenReturn(List.of());

        service.skip(TASK, "QA", "这一批不做质检，客户只要白底图");

        verify(stepStateMapper, times(1)).insert(any(DpProjectStepState.class));
        verify(stepStateMapper, never()).updateById(any(DpProjectStepState.class));
        verify(projectService, times(1)).appendEvent(eq(TASK), eq("QA"), eq("STEP_SKIPPED"), anyString());
    }

    @Test
    @DisplayName("已有行时改行而不是插新行（同一个步骤只能有一行状态）")
    void skipUpdatesExistingRow() {
        givenStep(step("QA", CreativeStepProjection.PENDING, true, false));
        DpProjectStepState existing = new DpProjectStepState();
        existing.setId(9L);
        existing.setTaskId(TASK);
        existing.setStepCode("QA");
        existing.setStatus(CreativeStepProjection.PENDING);
        when(stepStateMapper.selectList(any())).thenReturn(List.of(existing));

        service.skip(TASK, "QA", "客户确认不做机检");

        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
        verify(stepStateMapper, times(1)).updateById(any(DpProjectStepState.class));
    }

    @Test
    @DisplayName("必填步骤拒绝，并且提示说清为什么")
    void requiredStepIsRefused() {
        givenStep(step("FACT", CreativeStepProjection.PENDING, false, false));

        ServiceException e = assertThrows(ServiceException.class, () -> service.skip(TASK, "FACT", "懒得做"));
        assertTrue(e.getMessage().contains("必填"), e.getMessage());
        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
    }

    @Test
    @DisplayName("有闸门的步骤拒绝，提示指向完成审核")
    void gatedStepIsRefused() {
        givenStep(step("GATE", CreativeStepProjection.PENDING, false, true));

        ServiceException e = assertThrows(ServiceException.class, () -> service.skip(TASK, "GATE", "先跳过"));
        assertTrue(e.getMessage().contains("闸门"), e.getMessage());
        assertTrue(e.getMessage().contains("绕过门禁"), e.getMessage());
    }

    @Test
    @DisplayName("已完成的步骤不能被改成跳过（那是把已完成说成跳过）")
    void doneStepIsRefused() {
        givenStep(step("DNA", CreativeStepProjection.DONE, false, false));

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.skip(TASK, "DNA", "已经做完但想标跳过"));
        assertTrue(e.getMessage().contains("已经做完"), e.getMessage());
    }

    @Test
    @DisplayName("原因必填且有最短长度（否则不算留痕）")
    void reasonIsMandatory() {
        givenStep(step("QA", CreativeStepProjection.PENDING, true, false));

        ServiceException blank = assertThrows(ServiceException.class, () -> service.skip(TASK, "QA", "  "));
        assertTrue(blank.getMessage().contains("跳过原因"), blank.getMessage());
        ServiceException tooShort = assertThrows(ServiceException.class, () -> service.skip(TASK, "QA", "跳"));
        assertTrue(tooShort.getMessage().contains("至少"), tooShort.getMessage());
        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
    }

    @Test
    @DisplayName("取消跳过：删行 + 事件；不是跳过状态时拒绝")
    void cancelSkip() {
        givenStep(step("QA", CreativeStepProjection.SKIPPED, false, false));
        DpProjectStepState skipped = new DpProjectStepState();
        skipped.setId(11L);
        skipped.setTaskId(TASK);
        skipped.setStepCode("QA");
        skipped.setStatus(CreativeStepProjection.SKIPPED);
        skipped.setRemark("上一批客户不要质检");
        when(stepStateMapper.selectList(any())).thenReturn(List.of(skipped));

        service.cancelSkip(TASK, "QA");
        verify(stepStateMapper, times(1)).deleteById(11L);
        verify(projectService, times(1)).appendEvent(eq(TASK), eq("QA"), eq("STEP_SKIP_CANCELLED"), anyString());

        // 不是跳过状态 → 拒绝
        givenStep(step("QA", CreativeStepProjection.PENDING, true, false));
        when(stepStateMapper.selectList(any())).thenReturn(List.of());
        ServiceException e = assertThrows(ServiceException.class, () -> service.cancelSkip(TASK, "QA"));
        assertTrue(e.getMessage().contains("不是跳过状态"), e.getMessage());
    }

    @Test
    @DisplayName("步骤不在该交付类型的流程里 → 明确拒绝（不是静默什么都不做）")
    void unknownStepIsRefused() {
        when(scenarioConfigService.listProjectSteps(TASK)).thenReturn(List.of());
        ServiceException e = assertThrows(ServiceException.class, () -> service.skip(TASK, "NOPE", "随便跳"));
        assertTrue(e.getMessage().contains("不在该交付类型的流程里"), e.getMessage());
        verify(stepStateMapper, never()).insert(any(DpProjectStepState.class));
        verify(projectService, never()).appendEvent(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("跳过返回的是更新后的完整步骤列表（页面一次请求就能刷新）")
    void skipReturnsUpdatedSteps() {
        ProjectStepStateVo pending = step("QA", CreativeStepProjection.PENDING, true, false);
        List<ProjectStepStateVo> after = List.of(
            step("INPUT", CreativeStepProjection.DONE, false, false),
            step("QA", CreativeStepProjection.SKIPPED, false, false));
        // 第一次查（校验前）看到的是待办，写完行之后再查才是 SKIPPED——同一个查询的两次调用
        when(scenarioConfigService.listProjectSteps(TASK)).thenReturn(List.of(pending), after);
        when(stepStateMapper.selectList(any())).thenReturn(List.of());

        List<ProjectStepStateVo> result = service.skip(TASK, "QA", "客户确认不做机检");
        assertEquals(2, result.size());
        assertEquals(CreativeStepProjection.SKIPPED, result.get(1).status());
        // 事件里要留下"跳过之后的进度"：复盘时不用按今天的配置重算
        // （2 步里做完 1 步、跳过 1 步 → 分子 1、分母 1）
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(projectService, times(1)).appendEvent(eq(TASK), eq("QA"), eq("STEP_SKIPPED"), detail.capture());
        assertTrue(detail.getValue().contains("1/1（其中跳过 1 步）"), detail.getValue());
        assertTrue(detail.getValue().contains("客户确认不做机检"), detail.getValue());
    }
}
