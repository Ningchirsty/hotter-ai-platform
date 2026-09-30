package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.domain.DpDeliveryArtifact;
import org.dromara.creative.domain.DpDeliveryType;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.helper.CreativeRenderer;
import org.dromara.creative.helper.CreativeRendererHub;
import org.dromara.creative.mapper.DpDeliveryArtifactMapper;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「确认交付」（V0.2 R51）：多图交付类型的收尾动作。
 *
 * <p>R50 真机干跑发现：多图交付类型（主图、海报）的交付物是一组图，没有长图精修版可上传，
 * 而全平台**只有「上传精修最终版」能推到 COMPLETED**，于是这类项目永远卡在「终审」。
 * 这个动作补上出口，但必须**不能被误用**，所以这里钉住四条：</p>
 * <ol>
 *   <li>长图类（render_mode=LONGPAGE）→ 拒绝（它的交付完成是设计师改过图之后上传精修版）；</li>
 *   <li>还没有交付产物 → 拒绝（确认一个不存在的交付物＝假交付）；</li>
 *   <li>已经完成 → 拒绝（重复确认会在日志里刷出假动作）；</li>
 *   <li>指定了别人项目的版本 → 拒绝；允许时**事件与阶段都要留痕**。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeDeliveryConfirmTest {

    @Mock
    private CreativeRendererHub rendererHub;
    @Mock
    private DpDeliveryArtifactMapper artifactMapper;
    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;
    @Mock
    private CreativeRenderer multiImageRenderer;
    @Mock
    private CreativeRenderer posterRenderer;
    @Mock
    private CreativeRenderer longPageRenderer;

    @InjectMocks
    private CreativeDeliveryServiceImpl service;

    private static final long TASK = 777L;
    private static final long VERSION_ID = 9001L;

    @BeforeEach
    void setUp() {
        CreativeProjectVo project = new CreativeProjectVo();
        project.setTaskId(TASK);
        project.setDeliverableType("MAIN_IMAGE");
        when(projectService.getProject(TASK)).thenReturn(project);
        when(projectService.stageOf(TASK)).thenReturn("FINAL_REVIEW");
        when(multiImageRenderer.code()).thenReturn(MultiImageRenderer.CODE);
        when(multiImageRenderer.displayName()).thenReturn("多图交付渲染器");
        when(multiImageRenderer.targetStep()).thenReturn("FINAL");
        when(multiImageRenderer.note()).thenReturn("把各屏已选定的交付图按屏序打成一组");
        when(posterRenderer.code()).thenReturn(PosterRenderer.CODE);
        when(posterRenderer.displayName()).thenReturn("海报渲染器");
        when(longPageRenderer.code()).thenReturn(LongPageRenderer.CODE);
        when(longPageRenderer.displayName()).thenReturn("长图排版渲染器");
        when(rendererHub.all()).thenReturn(List.of(multiImageRenderer, posterRenderer, longPageRenderer));
    }

    /**
     * 配置里的渲染模式 → 渲染器。
     *
     * <p>R52 起 {@code confirm} 的判据是**渲染器**（白名单）而不是模式字符串，
     * 所以这里必须按模式分别打桩——否则"长图被拒"那条会因为所有模式都解析到多图渲染器而变绿。</p>
     */
    private void renderMode(String mode) {
        DpDeliveryType type = new DpDeliveryType();
        type.setDeliveryType("MAIN_IMAGE");
        type.setRenderMode(mode);
        when(scenarioConfigService.getDeliveryType("MAIN_IMAGE")).thenReturn(type);
        when(rendererHub.resolveFor(mode)).thenReturn(
            switch (mode) {
                case "MULTI_IMAGE" -> multiImageRenderer;
                case "POSTER" -> posterRenderer;
                default -> longPageRenderer;
            });
    }

    private DpDeliveryArtifact artifact(long taskId, int version) {
        DpDeliveryArtifact row = new DpDeliveryArtifact();
        row.setId(VERSION_ID);
        row.setTaskId(taskId);
        row.setDeliveryType("MAIN_IMAGE");
        row.setRenderer(MultiImageRenderer.CODE);
        row.setVersion(version);
        row.setImageCount(2);
        row.setChecksum("abc123");
        return row;
    }

    @Test
    @DisplayName("长图交付类型不能用「确认交付」收尾：必须走上传精修最终版")
    void rejectsLongPageType() {
        renderMode("LONGPAGE");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(TASK, null, "终审通过"));
        // 消息要同时说清"是哪个渲染器"与"该走哪条路"（只说"不允许"会让人不知道下一步做什么）
        assertTrue(ex.getMessage().contains("LONG_PAGE"), ex.getMessage());
        assertTrue(ex.getMessage().contains("精修"), ex.getMessage());
        verify(projectService, never()).moveStage(anyLong(), any(), anyString(), anyString());
        verify(projectService, never()).appendEvent(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("多图类型还没有交付产物：拒绝，且不推进阶段")
    void rejectsWhenNoArtifact() {
        renderMode("MULTI_IMAGE");
        when(artifactMapper.selectList(any())).thenReturn(List.of());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(TASK, null, null));
        assertTrue(ex.getMessage().contains("还没有交付产物"), ex.getMessage());
        verify(projectService, never()).moveStage(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("已经是「已完成」：拒绝重复确认")
    void rejectsWhenAlreadyCompleted() {
        renderMode("MULTI_IMAGE");
        when(projectService.stageOf(TASK)).thenReturn("COMPLETED");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(TASK, null, null));
        assertTrue(ex.getMessage().contains("已完成"), ex.getMessage());
        verify(projectService, never()).moveStage(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("指定了别的项目的交付版本：拒绝（不能确认别人的产物）")
    void rejectsForeignVersion() {
        renderMode("MULTI_IMAGE");
        when(artifactMapper.selectById(VERSION_ID)).thenReturn(artifact(999L, 1));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.confirm(TASK, VERSION_ID, null));
        assertTrue(ex.getMessage().contains("不属于该项目"), ex.getMessage());
        verify(projectService, never()).moveStage(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("多图类型 + 有交付产物：事件与阶段都留痕，收尾到 COMPLETED")
    void confirmsAndCompletes() {
        renderMode("MULTI_IMAGE");
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(TASK, 3)));

        var vo = service.confirm(TASK, null, "终审通过，交付");

        assertEquals(1, vo.getArtifacts().size());
        assertEquals(3, vo.getCurrentVersion());
        // 先写事件再推阶段：两条都要有，缺任何一条都会让"谁在什么时候确认了哪一版"查不出来
        verify(projectService).appendEvent(eq(TASK), eq("DELIVERY"), eq("DELIVERY_CONFIRMED"), anyString());
        verify(projectService).moveStage(eq(TASK), eq(org.dromara.creative.enums.DpVisualStageEnum.COMPLETED),
            eq("DELIVERY_CONFIRMED"), anyString());
    }

    @Test
    @DisplayName("海报（POSTER）也能用「确认交付」收尾——它的交付物是多档成品图，同样没有长图精修版")
    void posterCanConfirm() {
        renderMode("POSTER");
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(TASK, 1)));

        var vo = service.confirm(TASK, null, "三档成品图确认交付");

        assertEquals(1, vo.getCurrentVersion());
        verify(projectService).moveStage(eq(TASK), eq(org.dromara.creative.enums.DpVisualStageEnum.COMPLETED),
            eq("DELIVERY_CONFIRMED"), anyString());
    }
}
