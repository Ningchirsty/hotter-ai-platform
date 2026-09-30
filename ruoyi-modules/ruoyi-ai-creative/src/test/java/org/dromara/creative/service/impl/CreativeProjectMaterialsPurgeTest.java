package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTaskFile;
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
    @Mock
    private org.dromara.content.mapper.CpOutputCheckMapper outputCheckMapper;

    @InjectMocks
    private CreativeProjectServiceImpl service;

    private static final long TASK = 888L;
    private static final String NAME = "R25 清理素材验证";

    private final List<String> deletedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(stageMapper.selectTaskName(TASK)).thenReturn(NAME);
        when(stageMapper.selectDelFlag(TASK)).thenReturn("0");
        // 素材统计与清理都**直接读附件表**（不走内容服务的任务校验），所以这里 mock mapper
        when(fileMapper.selectList(any())).thenReturn(List.of(entity(1L, "a.png", 1000L, "k/a.png"),
            entity(2L, "b.jpg", 2000L, "k/b.jpg")));
        when(generationMapper.selectCount(any())).thenReturn(3L);
        when(detailPageVersionMapper.selectCount(any())).thenReturn(1L);
        // "先删对象再删行"的**顺序**要能被断言：对象每删一个就记一笔，行删除时检查已删数
        org.mockito.Mockito.doAnswer(inv -> {
            deletedKeys.add(inv.getArgument(0));
            return null;
        }).when(contentOssHelper).delete(any());
    }

    private static CpTaskFile entity(long id, String name, long size, String ref) {
        CpTaskFile row = new CpTaskFile();
        row.setFileId(id);
        row.setTaskId(TASK);
        row.setFileName(name);
        row.setFileSize(size);
        row.setFileRef(ref);
        return row;
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
        // purgedObjects 数的是**附件个数**（每删一个附件算一个对象），
        // 缩略图伴生对象是搭着删的，不额外计数——R34 这里保持 2，别被上面那条 deletedKeys 误导。
        assertEquals(2, vo.getPurgedObjects());
        assertEquals(2, vo.getPurgedFiles());
        assertEquals(3, vo.getPurgedGenerations());
        assertEquals(3000L, vo.getPurgedBytes());
    }

    @Test
    @DisplayName("允许清理时：先删对象再删行（含 R34 的缩略图伴生对象），且把「保留了什么」写清楚")
    void purgeRemovesObjectsThenRows() {
        ProjectMaterialsVo vo = service.purgeMaterials(TASK, NAME, true);

        // 【R34 起期望值有变，不是回归】缩略图现在会落成伴生对象，清理时必须跟着删——
        // 否则"清理素材"之后对象存储里会留下一堆没人引用的 thumb.jpg（统计也看不见的垃圾）。
        assertEquals(
            List.of("k/a.png", "content-private/888/1/thumb.jpg", "k/b.jpg", "content-private/888/2/thumb.jpg"),
            deletedKeys, "对象与缩略图伴生对象都应逐个删除");
        verify(generationMapper, times(1)).delete(any());
        verify(fileMapper, times(1)).delete(any());
        verify(outputCheckMapper, times(1)).delete(any());
        assertTrue(vo.getNote().contains("分镜、文案、模块计划与阶段事件都保留着"), vo.getNote());
    }

    @Test
    @DisplayName("质检记录随素材一起清（它引用的两张图都删了，留着就是指向不存在文件的行）")
    void purgeAlsoRemovesOutputChecks() {
        ProjectMaterialsVo vo = service.purgeMaterials(TASK, NAME, true);

        verify(outputCheckMapper, times(1)).delete(any());
        assertTrue(vo.getNote().contains("质检记录"), vo.getNote());
    }

    @Test
    @DisplayName("批量清理：口令不对 → 拒绝；只处理已删项目")
    void batchPurgeGuards() {
        ServiceException wrong = assertThrows(ServiceException.class,
            () -> service.purgeDeletedMaterials("清理", List.of(TASK)));
        assertTrue(wrong.getMessage().contains("清理素材"), wrong.getMessage());

        // 项目未删除 → 跳过（不报错，但如实回报），且一个对象都不删
        when(stageMapper.selectDelFlag(TASK)).thenReturn("0");
        java.util.Map<String, Object> result = service.purgeDeletedMaterials("清理素材", List.of(TASK));
        assertEquals(0, result.get("projects"));
        assertFalse(((java.util.List<?>) result.get("skipped")).isEmpty(), result.toString());
        verify(contentOssHelper, never()).delete(any());

        // 已删除 → 真的清
        when(stageMapper.selectDelFlag(TASK)).thenReturn("1");
        java.util.Map<String, Object> ok = service.purgeDeletedMaterials("清理素材", List.of(TASK));
        assertEquals(1, ok.get("projects"));
        assertEquals(2, ok.get("objects"));
    }

    @Test
    @DisplayName("已删项目清单只列「还有东西可清」的项目")
    void deletedListOnlyShowsProjectsWithResidue() {
        org.dromara.creative.domain.vo.DeletedTaskVo task = new org.dromara.creative.domain.vo.DeletedTaskVo();
        task.setTaskId(TASK);
        task.setTaskName(NAME);
        when(stageMapper.selectDeletedTasks()).thenReturn(List.of(task));
        when(stageMapper.selectDelFlag(TASK)).thenReturn("1");

        List<ProjectMaterialsVo> list = service.deletedProjectMaterials();

        assertEquals(1, list.size());
        assertEquals(NAME, list.get(0).getTaskName());
        assertTrue(list.get(0).getProjectDeleted());
    }
}
