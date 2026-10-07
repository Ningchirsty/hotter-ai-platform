package org.dromara.creative.mirror;

import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.service.ICreativeGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创作域只读镜像的映射与边界测试。
 *
 * <p>三个要点各自对应一种「错了也不会报错」的失败：</p>
 * <ul>
 *     <li><b>不能调会刷新的读法</b>：调了就会写库、甚至真的把出图派发出去；</li>
 *     <li><b>状态必须原样透传</b>：映射成 {@code aig_task} 的状态会让人以为两套状态机是一套；</li>
 *     <li><b>主键类型错误要报错</b>：返回 null 会把「参数拿错了」伪装成「这条不存在」。</li>
 * </ul>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰数据库。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeTaskMirrorProviderTest {

    private static final String SOURCE = "DP_GENERATION";

    /**
     * 造一条候选视图。
     *
     * @param id     候选ID
     * @param status 状态编码
     * @return 候选视图
     */
    private static DpGenerationVo row(long id, String status) {
        DpGenerationVo vo = new DpGenerationVo();
        vo.setId(id);
        vo.setTaskId(5001L);
        vo.setTaskName("秋季主视觉");
        vo.setCandidateNo(3);
        vo.setStatus(status);
        vo.setOutputAssetId(9901L);
        vo.setDurationMs(1234L);
        vo.setCreateTime(LocalDateTime.of(2026, 10, 7, 12, 0));
        return vo;
    }

    @Test
    @DisplayName("走纯只读读法：绝不去调那个会刷新内核（甚至派发出图）的生产中心分页")
    void neverCallsTheRefreshingQuery() {
        ICreativeGenerationService generationService = mock(ICreativeGenerationService.class);
        when(generationService.queryReadOnlyPage(isNull(), isNull(), any()))
            .thenReturn(PageResult.build(List.of(row(8801L, "QUEUED")), 1L));
        CreativeTaskMirrorProvider provider = new CreativeTaskMirrorProvider(generationService);
        AigTaskMirrorQueryBo bo = new AigTaskMirrorQueryBo();
        bo.setSource(SOURCE);

        provider.queryPage(bo, new PageQuery());

        verify(generationService).queryReadOnlyPage(isNull(), isNull(), any());
        verify(generationService, never()).queryPage(any(), any());
        verify(generationService, never()).refresh(any());
    }

    @Test
    @DisplayName("查询条件真的透传：projectId 与 status 都进到只读读法里（不静默忽略）")
    void passesFiltersThrough() {
        ICreativeGenerationService generationService = mock(ICreativeGenerationService.class);
        when(generationService.queryReadOnlyPage(any(), any(), any()))
            .thenReturn(PageResult.build(List.of(), 0L));
        CreativeTaskMirrorProvider provider = new CreativeTaskMirrorProvider(generationService);
        AigTaskMirrorQueryBo bo = new AigTaskMirrorQueryBo();
        bo.setSource(SOURCE);
        bo.setProjectId(5001L);
        bo.setStatus("SUCCEEDED");

        provider.queryPage(bo, new PageQuery());

        verify(generationService).queryReadOnlyPage(eq(5001L), eq("SUCCEEDED"), any());
    }

    @Test
    @DisplayName("状态原样透传 + 写明状态机：不映射成 aig_task 的状态，只统一 terminal 一个语义")
    void keepsSourceStatusVerbatim() {
        ICreativeGenerationService generationService = mock(ICreativeGenerationService.class);
        when(generationService.getReadOnly(8801L)).thenReturn(row(8801L, "RUNNING"));
        when(generationService.getReadOnly(8802L)).thenReturn(row(8802L, "APPROVED"));
        CreativeTaskMirrorProvider provider = new CreativeTaskMirrorProvider(generationService);

        AigTaskMirrorVo running = provider.getDetail("8801");
        AigTaskMirrorVo approved = provider.getDetail("8802");

        assertEquals(SOURCE, running.getSource());
        assertEquals("创作域生成记录", running.getSourceLabel());
        assertEquals("8801", running.getRefId());
        assertEquals(5001L, running.getProjectId());
        assertEquals("秋季主视觉", running.getProjectName());
        assertEquals("候选 #3", running.getTitle());
        assertEquals("RUNNING", running.getStatus(), "状态必须是来源原值");
        assertEquals("出图中", running.getStatusLabel(), "展示名也来自来源自己的枚举");
        assertFalse(running.isTerminal(), "出图中不是终态");
        assertTrue(running.getStateMachine().contains("dp_generation"),
            "必须写明是哪套状态机，避免被当成 aig_task 的状态：" + running.getStateMachine());
        assertFalse(running.isSelected(), "未选定");
        assertTrue(running.isReadOnly(), "镜像行恒为只读");
        assertEquals(9901L, running.getOutputAssetId());
        assertEquals(1234L, running.getDurationMs());
        assertEquals(LocalDateTime.of(2026, 10, 7, 12, 0), running.getCreateTime());

        assertEquals("APPROVED", approved.getStatus());
        assertEquals("已选定", approved.getStatusLabel());
        assertTrue(approved.isTerminal(), "已选定是终态");
        assertTrue(approved.isSelected(), "来源侧 APPROVED 即「这一屏采用这张图」");
    }

    @Test
    @DisplayName("行不存在返回 null（存在性交由调用方判断），来源状态映射不到枚举时退回原值")
    void missingRowAndUnknownStatus() {
        ICreativeGenerationService generationService = mock(ICreativeGenerationService.class);
        when(generationService.getReadOnly(8803L)).thenReturn(row(8803L, "SOMETHING_NEW"));
        CreativeTaskMirrorProvider provider = new CreativeTaskMirrorProvider(generationService);

        assertNull(provider.getDetail("9999"), "不存在时为 null（未打桩）");

        AigTaskMirrorVo unknown = provider.getDetail("8803");
        assertEquals("SOMETHING_NEW", unknown.getStatus(), "认不出的状态照原样透传，不能猜");
        assertEquals("SOMETHING_NEW", unknown.getStatusLabel(), "枚举认不出时退回原值，避免显示空白");
        assertFalse(unknown.isTerminal(), "认不出是否终态时保守地按「还会变」处理");
    }

    @Test
    @DisplayName("主键不是数字：明确报错，而不是返回 null 把参数错误伪装成数据缺失")
    void rejectsNonNumericRefId() {
        CreativeTaskMirrorProvider provider =
            new CreativeTaskMirrorProvider(mock(ICreativeGenerationService.class));

        ServiceException error = assertThrows(ServiceException.class, () -> provider.getDetail("dp-8801"));

        assertTrue(error.getMessage().contains("数字"), "报错要说清ID形态：" + error.getMessage());
        assertTrue(error.getMessage().contains("dp-8801"), "报错要带上实际值便于定位：" + error.getMessage());
    }

}
