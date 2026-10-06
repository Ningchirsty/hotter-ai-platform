package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.service.IContentBrandBriefService;
import org.dromara.content.service.IContentTaskGateService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.mapper.CreativeCardMapper;
import org.dromara.creative.mapper.DpGateItemMapper;
import org.dromara.creative.mapper.DpGateProfileMapper;
import org.dromara.creative.mapper.DpStageEventMapper;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视觉门「上传资料并识别」的单元测试（v1 人工测试反馈 详情页与审核 1.3）。
 *
 * <p>原文：「闸门结论版块除了手动录入信息之外还应该有上传信息自动识别录入的功能」；
 * 裁定：「在闸门里做上传+识别」。这一块**不自己识别**——上传落内容侧同一张附件表、
 * 识别走内容侧那条「以待确认落库」的链；视觉门这边只负责入口、触发与状态。</p>
 *
 * <p>要钉住的四件事：</p>
 * <ol>
 *   <li>上传守卫：空文件 / 超过 20MB / 明显不是资料的类型（如 .mp4）都要**明确报错**，
 *       而且**不能**把文件交给内容侧（否则会留下一个"传上去了但没识别"的垃圾附件）；</li>
 *   <li>允许的类型（文档 + 图片）走内容侧上传，来源标 {@code UPLOAD}；</li>
 *   <li>识别前必须有资料：一条都没有时给"先上传再识别"的回话，而不是发起一次空解析；</li>
 *   <li>状态如实：解析状态码要翻成中文、失败原因要带出来、待确认/已确认条数要分开数。</li>
 * </ol>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeGateMaterialTest {

    private static final long TASK_ID = 2104582766641799169L;
    private static final long FILE_ID = 2104583461734440961L;

    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private IContentBrandBriefService briefService;
    @Mock
    private ICreativeDnaService dnaService;
    @Mock
    private ICreativeDirectionService directionService;
    @Mock
    private ICreativeStoryboardService storyboardService;
    @Mock
    private IContentTaskService contentTaskService;
    @Mock
    private IContentTaskGateService contentTaskGateService;
    @Mock
    private CreativeCardMapper cardMapper;
    @Mock
    private DpStageEventMapper eventMapper;
    @Mock
    private DpGateProfileMapper gateProfileMapper;
    @Mock
    private DpGateItemMapper gateItemMapper;

    private CreativeGateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreativeGateServiceImpl(projectService, briefService, dnaService,
            directionService, storyboardService, contentTaskService, contentTaskGateService,
            cardMapper, eventMapper, gateProfileMapper, gateItemMapper);
        when(projectService.getProject(anyLong())).thenReturn(new CreativeProjectVo());
    }

    /** 一份附件（默认：一份解析成功的 txt） */
    private CpTaskFileVo file(String name, String ext, String parseStatus, String message) {
        CpTaskFileVo vo = new CpTaskFileVo();
        vo.setFileId(FILE_ID);
        vo.setTaskId(TASK_ID);
        vo.setFileName(name);
        vo.setFileExt(ext);
        vo.setFileSize(1024L);
        vo.setParseStatus(parseStatus);
        vo.setParseMessage(message);
        return vo;
    }

    private ContentTaskDetailVo detail(List<CpTaskFileVo> files, long pending, long confirmed) {
        ContentTaskDetailVo detail = new ContentTaskDetailVo();
        CpTaskVo task = new CpTaskVo();
        task.setParseDoneAt(java.time.LocalDateTime.of(2026, 10, 6, 12, 0));
        detail.setTask(task);
        detail.setFiles(new ArrayList<>(files));
        List<CpFactSnapshotVo> facts = new ArrayList<>();
        for (long i = 0; i < pending; i++) {
            CpFactSnapshotVo fact = new CpFactSnapshotVo();
            fact.setConfirmStatus("PENDING");
            facts.add(fact);
        }
        for (long i = 0; i < confirmed; i++) {
            CpFactSnapshotVo fact = new CpFactSnapshotVo();
            fact.setConfirmStatus("CONFIRMED");
            facts.add(fact);
        }
        detail.setFacts(facts);
        return detail;
    }

    // ------------------------------------------------------------------
    // 状态读取
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态如实：解析状态翻中文、失败原因带出来、待确认与已确认分开数")
    void materialsReportsHonestStatus() {
        when(contentTaskService.getDetail(TASK_ID)).thenReturn(detail(List.of(
            file("产品资料.txt", "txt", "DONE", null),
            file("报价单.pdf", "pdf", "FAILED", "解析能力未返回结果")), 3, 5));

        ICreativeGateService.GateMaterials materials = service.materials(TASK_ID);

        assertEquals(2, materials.files().size());
        assertEquals("已完成", materials.files().get(0).parseStatusDesc());
        assertEquals("失败", materials.files().get(1).parseStatusDesc());
        assertEquals("解析能力未返回结果", materials.files().get(1).parseMessage());
        assertEquals(3, materials.pendingFacts(), "待确认条数要单独数");
        assertEquals(5, materials.confirmedFacts());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 6, 12, 0), materials.parseDoneAt());
    }

    @Test
    @DisplayName("没有任务详情时返回空状态（不抛异常、不编数据）")
    void materialsWithoutDetail() {
        when(contentTaskService.getDetail(TASK_ID)).thenReturn(null);

        ICreativeGateService.GateMaterials materials = service.materials(TASK_ID);

        assertTrue(materials.files().isEmpty());
        assertEquals(0, materials.pendingFacts());
        assertEquals(0, materials.confirmedFacts());
    }

    // ------------------------------------------------------------------
    // 上传守卫
    // ------------------------------------------------------------------

    @Test
    @DisplayName("空文件 / 超过 20MB / 明显不是资料的类型：明确报错，且不把文件交给内容侧")
    void uploadGuards() {
        ServiceException empty = assertThrows(ServiceException.class,
            () -> service.uploadMaterial(TASK_ID, new MockMultipartFile("file", "a.txt", "text/plain", new byte[0])));
        assertTrue(empty.getMessage().contains("请选择"), empty.getMessage());

        byte[] big = new byte[21 * 1024 * 1024];
        ServiceException tooBig = assertThrows(ServiceException.class,
            () -> service.uploadMaterial(TASK_ID,
                new MockMultipartFile("file", "big.txt", "text/plain", big)));
        assertTrue(tooBig.getMessage().contains("20MB"), tooBig.getMessage());

        ServiceException badType = assertThrows(ServiceException.class,
            () -> service.uploadMaterial(TASK_ID,
                new MockMultipartFile("file", "demo.mp4", "video/mp4", "x".getBytes(StandardCharsets.UTF_8))));
        assertTrue(badType.getMessage().contains("文档与图片"), badType.getMessage());

        // 三次都必须在"交给内容侧"之前被挡住——否则会留下传上去了却没识别的垃圾附件
        verify(contentTaskService, never()).uploadFile(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("允许的类型走内容侧上传，来源标 UPLOAD，返回读回来的真实状态")
    void uploadDelegatesAndReadsBack() {
        MockMultipartFile file = new MockMultipartFile("file", "产品资料.txt", "text/plain",
            "材质：陶瓷".getBytes(StandardCharsets.UTF_8));
        when(contentTaskService.uploadFile(eq(TASK_ID), eq(null), eq(file), eq("UPLOAD")))
            .thenReturn(FILE_ID);
        when(contentTaskService.listFiles(TASK_ID))
            .thenReturn(List.of(file("产品资料.txt", "txt", "PENDING", null)));

        ICreativeGateService.GateMaterial material = service.uploadMaterial(TASK_ID, file);

        assertEquals(FILE_ID, material.fileId());
        assertEquals("待解析", material.parseStatusDesc(), "刚上传就是待解析——识别要人点一下才跑");
    }

    @Test
    @DisplayName("上传成功但读不回状态：如实报错，不替它编一个状态")
    void uploadWithoutReadBack() {
        MockMultipartFile file = new MockMultipartFile("file", "产品资料.txt", "text/plain", "x".getBytes());
        when(contentTaskService.uploadFile(anyLong(), any(), any(), any())).thenReturn(FILE_ID);
        when(contentTaskService.listFiles(TASK_ID)).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadMaterial(TASK_ID, file));
        assertTrue(ex.getMessage().contains("读不回"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 触发识别
    // ------------------------------------------------------------------

    @Test
    @DisplayName("一条资料都没有时给回话，不发起一次空解析")
    void parseWithoutMaterials() {
        when(contentTaskService.getDetail(TASK_ID)).thenReturn(detail(List.of(), 0, 0));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.triggerMaterialParse(TASK_ID));
        assertTrue(ex.getMessage().contains("先上传"), ex.getMessage());
        verify(contentTaskService, never()).triggerParse(anyLong());
    }

    @Test
    @DisplayName("有资料时触发内容侧的解析链（识别由它做，视觉门不另起一套）")
    void parseDelegatesToContentChain() {
        when(contentTaskService.getDetail(TASK_ID))
            .thenReturn(detail(List.of(file("产品资料.txt", "txt", "PENDING", null)), 0, 0));
        when(contentTaskService.triggerParse(TASK_ID)).thenReturn(998877L);

        assertEquals(998877L, service.triggerMaterialParse(TASK_ID));
        verify(contentTaskService).triggerParse(TASK_ID);
    }
}
