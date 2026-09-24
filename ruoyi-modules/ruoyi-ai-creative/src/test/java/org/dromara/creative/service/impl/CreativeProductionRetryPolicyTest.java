package org.dromara.creative.service.impl;

import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpStoryboardMapper;
import org.dromara.creative.mapper.DpStoryboardScreenMapper;
import org.dromara.content.service.IContentOutputCheckService;
import org.dromara.content.service.IContentProductService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「失败候选自动重试」策略的单元测试。
 *
 * <p>为什么必须钉住：这是自动化里少数会<b>真的再烧一次 GPU</b> 的动作——判错（把已成功的屏
 * 也重试、或失败到顶还无限重试）的代价是钱和排队时间；而靠真实失败去碰这些分支既不可控，
 * 也不该为了测试在生产上制造故障。因此用确定性单测把规则钉死。</p>
 *
 * <p>规则（实现即约定）：</p>
 * <ol>
 *     <li>该屏已有排队中/执行中/已出图/已选定的候选 → <b>不重试</b>；</li>
 *     <li>该屏候选数已达上限（3 次尝试）且存在失败 → <b>不重试</b>，并把屏标为待人工；</li>
 *     <li>该屏无活动/成功候选、有失败、且未到上限 → 自动重试一次并写 AUTO_RETRY 事件；</li>
 *     <li>已达上限但该屏已有「人选定」的候选 → 既不重试也不标待人工（人的决定优先）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeProductionRetryPolicyTest {

    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private ICreativeGateService gateService;
    @Mock
    private ICreativeGenerationService generationService;
    @Mock
    private ICreativeStoryboardService storyboardService;
    @Mock
    private DpGenerationMapper generationMapper;
    @Mock
    private DpStoryboardMapper storyboardMapper;
    @Mock
    private DpStoryboardScreenMapper screenMapper;
    @Mock
    private IContentOutputCheckService outputCheckService;
    @Mock
    private IContentProductService productService;

    private CreativeProductionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreativeProductionServiceImpl(projectService, gateService, generationService,
            storyboardService, generationMapper, storyboardMapper, screenMapper, outputCheckService,
            productService);
    }

    @Test
    @DisplayName("无活动/成功候选且失败未到上限 → 自动重试一次并留痕")
    void retriesWhenFailedBelowCap() {
        stubStoryboard(screen(11L, "S01"));
        when(generationMapper.selectList(any())).thenReturn(List.of(failed(101L, 11L)));
        when(generationService.submitForScreen(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new DpGenerationVo());

        int retried = service.autoRetryFailed(9L);

        Assertions.assertEquals(1, retried);
        verify(generationService, times(1))
            .submitForScreen(eq(9L), eq(11L), any(), any(), any(), any(), any(), any());
        verify(projectService, times(1)).appendEvent(eq(9L), eq("GENERATION"), eq("AUTO_RETRY"), any());
    }

    @Test
    @DisplayName("该屏已有成功/排队候选 → 不重试（避免无谓再烧卡）")
    void skipsWhenActiveOrSucceeded() {
        stubStoryboard(screen(12L, "S02"));
        when(generationMapper.selectList(any())).thenReturn(List.of(failed(102L, 12L), succeeded(103L, 12L)));

        int retried = service.autoRetryFailed(9L);

        Assertions.assertEquals(0, retried);
        verify(generationService, never())
            .submitForScreen(anyLong(), anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("失败已达上限（3 次尝试）→ 不重试，并把该屏标为待人工")
    void givesUpAtCap() {
        stubStoryboard(screen(13L, "S03"));
        when(generationMapper.selectList(any()))
            .thenReturn(List.of(failed(104L, 13L), failed(105L, 13L), failed(106L, 13L)));

        int retried = service.autoRetryFailed(9L);

        Assertions.assertEquals(0, retried);
        verify(generationService, never())
            .submitForScreen(anyLong(), anyLong(), any(), any(), any(), any(), any(), any());
        verify(screenMapper, times(1)).updateById(any(DpStoryboardScreen.class));
    }

    @Test
    @DisplayName("失败达上限但该屏已有人工选定的候选 → 不重试也不标待人工")
    void approvedWinsOverGiveUp() {
        stubStoryboard(screen(14L, "S04"));
        when(generationMapper.selectList(any()))
            .thenReturn(List.of(failed(107L, 14L), failed(108L, 14L), approved(109L, 14L)));

        int retried = service.autoRetryFailed(9L);

        Assertions.assertEquals(0, retried);
        verify(screenMapper, never()).updateById(any(DpStoryboardScreen.class));
    }

    // ------------------------------------------------------------------

    private void stubStoryboard(DpStoryboardScreenVo... screens) {
        DpStoryboardVo storyboard = new DpStoryboardVo();
        storyboard.setId(1L);
        storyboard.setTaskId(9L);
        storyboard.setScreens(List.of(screens));
        when(storyboardService.latest(9L)).thenReturn(storyboard);
    }

    private DpStoryboardScreenVo screen(Long id, String no) {
        DpStoryboardScreenVo vo = new DpStoryboardScreenVo();
        vo.setId(id);
        vo.setTaskId(9L);
        vo.setScreenNo(no);
        vo.setScreenType("HERO");
        vo.setScreenTypeDesc("主图");
        vo.setWorkflowCode("wf-i2i-qwen21");
        return vo;
    }

    private DpGeneration failed(Long id, Long screenId) {
        return row(id, screenId, "FAILED");
    }

    private DpGeneration succeeded(Long id, Long screenId) {
        return row(id, screenId, "SUCCEEDED");
    }

    private DpGeneration approved(Long id, Long screenId) {
        return row(id, screenId, "APPROVED");
    }

    private DpGeneration row(Long id, Long screenId, String status) {
        DpGeneration row = new DpGeneration();
        row.setId(id);
        row.setTaskId(9L);
        row.setScreenId(screenId);
        row.setStatus(status);
        row.setCandidateNo(1);
        return row;
    }

}
