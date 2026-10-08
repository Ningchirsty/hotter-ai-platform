package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.ai.image.service.ImageTaskSubmissionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.helper.CreativeTaskLedger;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 只读读法（{@code queryReadOnlyPage} / {@code getReadOnly}）的副作用测试。
 *
 * <p><b>这条测试守的是一个会花钱的边界</b>：创作域的 {@code queryPage} 会「顺手刷新」内核状态——
 * 状态变了就 {@code updateById} 落库并写项目事件，对 {@code QUEUED} 的候选还会真的
 * {@code dispatchQueued}（<b>等于把出图跑起来并开始计费</b>）。
 * 治理层的只读镜像若复用它，「有人打开任务列表看一眼」就成了一个能触发计费的副作用。</p>
 *
 * <p>因此这里断言的不是「返回值对不对」，而是<b>有没有发生写与内核交互</b>：
 * 不 {@code updateById}、不触达 {@link ImageTaskSubmissionService}。
 * 这类断言在功能正常时永远是绿的，只有回归时才会红——正是它存在的意义。</p>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰数据库。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeGenerationReadOnlyTest {

    /**
     * 造一个只读读法可用的服务实例。
     *
     * <p>只用到 {@code generationMapper} 与 {@code stageMapper}，其余依赖给桩即可——
     * 这里刻意不构造 Spring 上下文，因为要测的正是「不碰那些东西」。</p>
     *
     * @param generationMapper   候选 Mapper
     * @param stageMapper        阶段 Mapper
     * @param submissionProvider 内核提交服务（只读读法不应触达）
     * @return 服务实例
     */
    @SuppressWarnings("unchecked")
    private static CreativeGenerationServiceImpl service(DpGenerationMapper generationMapper,
                                                         CreativeTaskStageMapper stageMapper,
                                                         ObjectProvider<ImageTaskSubmissionService> submissionProvider) {
        return new CreativeGenerationServiceImpl(
            mock(ICreativeProjectService.class),
            mock(ICreativeDnaService.class),
            mock(ICreativeDirectionService.class),
            mock(ICreativeStoryboardService.class),
            mock(ICreativeGateService.class),
            mock(IContentBrandBriefService.class),
            mock(DnaPromptBuilder.class),
            mock(IContentTaskService.class),
            mock(ContentOssHelper.class),
            generationMapper,
            stageMapper,
            submissionProvider,
            mock(ICreativeScenarioConfigService.class),
            // 治理任务账本：只读读法根本不该碰它（本测试的断言正是「不写库、不触达外部」）
            mock(CreativeTaskLedger.class));
    }

    /**
     * 造一条「排队中」的候选行。
     *
     * @return 候选实体
     */
    private static DpGeneration queuedRow() {
        DpGeneration row = new DpGeneration();
        row.setId(8801L);
        row.setTaskId(5001L);
        row.setCandidateNo(3);
        row.setStatus("QUEUED");
        row.setImageTaskId(7701L);
        row.setExecTenantId("000000");
        row.setExecUserId(7L);
        return row;
    }

    @Test
    @DisplayName("只读分页不写库、不碰内核：QUEUED 候选不会因为「被看一眼」而真的被派发出去")
    void readOnlyPageHasNoSideEffects() {
        DpGenerationMapper generationMapper = mock(DpGenerationMapper.class);
        CreativeTaskStageMapper stageMapper = mock(CreativeTaskStageMapper.class);
        ObjectProvider<ImageTaskSubmissionService> submissionProvider = mock(ObjectProvider.class);
        Page<DpGeneration> page = new Page<>(1, 10);
        page.setRecords(List.of(queuedRow()));
        page.setTotal(1L);
        when(generationMapper.selectPage(any(), any())).thenReturn(page);
        when(stageMapper.selectTaskName(5001L)).thenReturn("秋季主视觉");

        CreativeGenerationServiceImpl service = service(generationMapper, stageMapper, submissionProvider);
        PageResult<DpGenerationVo> result = service.queryReadOnlyPage(5001L, null, new PageQuery());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRows().size());
        DpGenerationVo vo = result.getRows().iterator().next();
        assertEquals("QUEUED", vo.getStatus(), "状态原样返回（未做任何刷新）");
        assertEquals("排队中", vo.getStatusDesc());
        assertEquals("秋季主视觉", vo.getTaskName());

        // 关键断言：没有写库、没有内核交互。
        // 触达 ImageTaskSubmissionService 就意味着可能已经派发并把出图跑起来了。
        verify(generationMapper, never()).updateById(any(DpGeneration.class));
        verifyNoInteractions(submissionProvider);
    }

    @Test
    @DisplayName("只读单条：同样不写库、不碰内核；ID 为空或不存在返回 null")
    void readOnlyDetailHasNoSideEffects() {
        DpGenerationMapper generationMapper = mock(DpGenerationMapper.class);
        CreativeTaskStageMapper stageMapper = mock(CreativeTaskStageMapper.class);
        ObjectProvider<ImageTaskSubmissionService> submissionProvider = mock(ObjectProvider.class);
        when(generationMapper.selectById(8801L)).thenReturn(queuedRow());
        when(stageMapper.selectTaskName(5001L)).thenReturn("秋季主视觉");

        CreativeGenerationServiceImpl service = service(generationMapper, stageMapper, submissionProvider);

        DpGenerationVo vo = service.getReadOnly(8801L);
        assertEquals("QUEUED", vo.getStatus());
        assertNull(service.getReadOnly(null), "ID 为空不该去查（更不该当成查到全表）");
        assertNull(service.getReadOnly(9999L), "不存在返回 null，而不是抛异常");

        verify(generationMapper, never()).updateById(any(DpGeneration.class));
        verifyNoInteractions(submissionProvider);
    }

}
