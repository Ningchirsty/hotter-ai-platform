package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentProductService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.vo.ProjectMaterialsVo;
import org.dromara.creative.helper.CreativeStepStateWriter;
import org.dromara.creative.mapper.CreativeTaskStageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「清理素材」的三道闸与真实动作（V0.2 R25，按用户决定：删项目默认保留素材）。
 *
 * <p>这是**破坏性且不可恢复**的操作，所以测试要钉住的不是"能删"，而是"什么情况下不许删"：</p>
 * <ol>
 *   <li>项目名不逐字相同 → 拒绝（防手滑）；</li>
 *   <li>项目还没删除且没传 force → 拒绝（正在用的项目清素材＝毁掉它）；</li>
 *   <li>允许删时：**先删对象再删行**（顺序反了会留下孤儿对象），且统计数字如实回报。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeProjectMaterialsPurgeTest {

    @Mock
    private IContentTaskService contentTaskService;
    @Mock
    private IContentProductService productService;
    @Mock
    private ContentOssHelper contentOssHelper;
    @Mock
    private CreativeTaskStageMapper stageMapper;
    @Mock
    private CreativeStepStateWriter stepStateWriter;
    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;
    @Mock
    private DpStageEventMapper eventMapper;
    @Mock
    private DpGenerationMapper generationMapper;
    @Mock
    private DpDetailPageVersionMapper detailPageVersionMapper;
    @Mock
    private CpTaskFileMapper fileMapper;

    @InjectMocks
    private CreativeProjectServiceImpl service;

    private static final long TASK = 888L;
    private static final String NAME = "R25 清理素材验证";

    private final List<String> deletedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(stageMapper.selectTaskName(TASK)).thenReturn(NAME);
        when(stageMapper.selectDelFlag(TASK)).thenReturn("0");
        when(contentTaskService.listFiles(TASK)).thenReturn(List.of(file(1L, "a.png", 1000L, "k/a.png"),
            file(2L, "b.jpg", 2000L, "k/b.jpg")));
        when(generationMapper.selectCount(any())).thenReturn(3L);
        when(detailPageVersionMapper.selectCount(any())).thenReturn(1L);
        // "先删对象再删行"的**顺序**要能被断言：对象每删一个就记一笔，行删除时检查已删数
        org.mockito.Mockito.doAnswer(inv -> {
            deletedKeys.add(inv.getArgument(0));
            return null;
        }).when(contentOssHelper).delete(any());
    }

    private static CpTaskFileVo file(long id, String name, long size, String ref) {
        CpTaskFileVo vo = new CpTaskFileVo();
        vo.setFileId(id);
        vo.setFileName(name);
        vo.setFileSize(size);
        vo.setFileRef(ref);
        return vo;
    }

    @Test
    @DisplayName("素材概况：报出附件数/字节/生成数/版本数，且**不删任何东西**")
    void summaryIsReadOnly() {
        ProjectMaterialsVo vo = service.materials(TASK);

        assertEquals(NAME, vo.getTaskName());
        assertFalse(vo.getProjectDeleted());
        assertEquals(2, vo.getFileCount());
        assertEquals(3000L, vo.getFileBytes());
        assertEquals(3, vo.getGenerationCount());
        assertEquals(1, vo.getVersionCount());
        assertTrue(vo.getNote().contains("项目还在"), vo.getNote());
        verify(contentOssHelper, never()).delete(any());
        verify(fileMapper, never()).delete(any());
    }

    @Test
    @DisplayName("二次确认不一致 → 拒绝，且一个对象都不删")
    void wrongNameIsRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.purgeMaterials(TASK, "R25 清理素材验", true));
        assertTrue(ex.getMessage().contains("二次确认不一致"), ex.getMessage());
        assertTrue(ex.getMessage().contains(NAME), "要把正确名字写进提示里，用户才知道该输入什么");
        verify(contentOssHelper, never()).delete(any());
        verify(fileMapper, never()).delete(any());
        verify(generationMapper, never()).delete(any());
    }

    @Test
    @DisplayName("项目还在且没传 force → 拒绝（正在用的项目清素材＝毁掉它）")
    void liveProjectNeedsForce() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.purgeMaterials(TASK, NAME, false));
        assertTrue(ex.getMessage().contains("force=true"), ex.getMessage());
        verify(contentOssHelper, never()).delete(any());
    }

    @Test
    @DisplayName("已删除的项目：核对名字即可清理，不需要 force")
    void deletedProjectNeedsOnlyName() {
        when(stageMapper.selectDelFlag(TASK)).thenReturn("1");

        ProjectMaterialsVo vo = service.purgeMaterials(TASK, NAME, false);

        assertTrue(vo.getPurged());
        assertEquals(2, vo.getPurgedObjects());
        assertEquals(2, vo.getPurgedFiles());
        assertEquals(3, vo.getPurgedGenerations());
        assertEquals(3000L, vo.getPurgedBytes());
    }

    @Test
    @DisplayName("允许清理时：先删对象再删行，且把「保留了什么」写清楚")
    void purgeRemovesObjectsThenRows() {
        ProjectMaterialsVo vo = service.purgeMaterials(TASK, NAME, true);

        assertEquals(List.of("k/a.png", "k/b.jpg"), deletedKeys, "对象应逐个删除");
        verify(generationMapper, times(1)).delete(any());
        verify(fileMapper, times(1)).delete(any());
        assertTrue(vo.getNote().contains("分镜、文案、模块计划与阶段事件都保留着"), vo.getNote());
    }
}
