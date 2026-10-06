package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.CpTaskFile;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.mapper.CpOutputCheckMapper;
import org.dromara.content.mapper.CpTaskFileMapper;
import org.dromara.content.service.IContentProductService;
import org.dromara.content.service.IContentTaskService;
import org.dromara.content.service.IContentWorkPackageService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 参考图上传**不能写别人的任务**（R38-1 修复的回归守卫）。
 *
 * <p>修的是什么：{@code uploadReference} 把文件登记进**内容域**的 {@code cp_task_file}。
 * 改造前当 {@code asProductImage=false}（前端默认值）时整段跳过 {@code getProject()}，
 * 于是只要持有 {@code creative:project:upload}，就能对**任意** taskId 挂一张
 * {@code source_type=REFERENCE} 的附件——包括创作域根本不处理的 MANUAL / EXHIBITION 任务，
 * 而内容域的 {@code uploadFile} 只校验「任务存在 + 非空 + ≤50MB」。表现为
 * 「设计角色能给品牌侧任务加附件」。</p>
 *
 * <p>这里钉住三条：
 * <ol>
 *   <li>交付类型不在视觉工厂配置里（例如 MANUAL）→ 必须在落对象存储**之前**被拒，
 *       且 {@code uploadFile} 一次都不能被调到；</li>
 *   <li>交付类型已停用 → 同样被拒（停用之后连上传口子也不该留着）；</li>
 *   <li>正常项目（ECOM_DETAIL）→ 照旧能传，不因为这次收紧而误伤。</li>
 * </ol>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeReferenceUploadScopeTest {

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
    private CpOutputCheckMapper outputCheckMapper;
    @Mock
    private IContentWorkPackageService contentWorkPackageService;

    @InjectMocks
    private CreativeProjectServiceImpl service;

    private static final long TASK = 4242L;

    private MockMultipartFile png;

    @BeforeEach
    void setUp() {
        png = new MockMultipartFile("file", "ref.png", "image/png",
            "not-a-real-png-but-the-guard-runs-first".getBytes(StandardCharsets.UTF_8));
    }

    /** 组一条内容任务详情（交付类型可指定） */
    private void givenTask(String deliverableType) {
        CpTaskVo task = new CpTaskVo();
        task.setTaskId(TASK);
        task.setTaskName("R38-1 上传范围验证");
        task.setDeliverableType(deliverableType);
        ContentTaskDetailVo detail = new ContentTaskDetailVo();
        detail.setTask(task);
        when(contentTaskService.getDetail(TASK)).thenReturn(detail);
    }

    @Test
    @DisplayName("交付类型不在视觉工厂配置里（MANUAL）→ 拒绝，且一个字节都不落对象存储")
    void refusesTaskWhoseDeliverableTypeIsNotConfigured() {
        givenTask("MANUAL");
        when(scenarioConfigService.getDeliveryType("MANUAL")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadReference(TASK, png));

        assertEquals("交付类型不在视觉工厂的配置里（dp_delivery_type）：MANUAL——请先在场景配置里登记该交付类型",
            ex.getMessage());
        verify(contentTaskService, never())
            .uploadFile(any(), isNull(), any(), any());
    }

    @Test
    @DisplayName("交付类型已停用 → 拒绝（停用之后上传口子也不该留着）")
    void refusesTaskWhoseDeliverableTypeIsDisabled() {
        givenTask("BRAND_POSTER");
        org.dromara.creative.domain.DpDeliveryType disabled = new org.dromara.creative.domain.DpDeliveryType();
        disabled.setDeliveryType("BRAND_POSTER");
        disabled.setEnabled("1");
        when(scenarioConfigService.getDeliveryType("BRAND_POSTER")).thenReturn(disabled);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadReference(TASK, png));

        assertEquals("交付类型已停用：BRAND_POSTER", ex.getMessage());
        verify(contentTaskService, never())
            .uploadFile(any(), isNull(), any(), any());
    }

    @Test
    @DisplayName("没有已发布场景档案 → 拒绝（视觉工厂无法确定流程与步骤）")
    void refusesTaskWithoutPublishedScenario() {
        givenTask("ECOM_DETAIL");
        org.dromara.creative.domain.DpDeliveryType enabled = new org.dromara.creative.domain.DpDeliveryType();
        enabled.setDeliveryType("ECOM_DETAIL");
        enabled.setEnabled("0");
        when(scenarioConfigService.getDeliveryType("ECOM_DETAIL")).thenReturn(enabled);
        when(scenarioConfigService.getScenario("ECOM_DETAIL")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadReference(TASK, png));

        assertEquals("交付类型 ECOM_DETAIL 还没有已发布的场景档案（dp_scenario_profile），"
            + "视觉工厂无法确定流程与步骤", ex.getMessage());
        verify(contentTaskService, never())
            .uploadFile(any(), isNull(), any(), any());
    }

    @Test
    @DisplayName("正常项目（ECOM_DETAIL，配置齐全）→ 照旧能传，别把正常路径一起收掉")
    void stillAllowsAConfiguredVisualProject() {
        givenTask("ECOM_DETAIL");
        org.dromara.creative.domain.DpDeliveryType enabled = new org.dromara.creative.domain.DpDeliveryType();
        enabled.setDeliveryType("ECOM_DETAIL");
        enabled.setEnabled("0");
        when(scenarioConfigService.getDeliveryType("ECOM_DETAIL")).thenReturn(enabled);
        when(scenarioConfigService.getScenario("ECOM_DETAIL"))
            .thenReturn(new org.dromara.creative.domain.DpScenarioProfile());
        when(stageMapper.selectTaskName(TASK)).thenReturn("R38-1 上传范围验证");
        when(fileMapper.selectList(any())).thenReturn(java.util.List.<CpTaskFile>of());
        when(contentTaskService.uploadFile(eq(TASK), isNull(), any(), any())).thenReturn(777L);

        Long fileId = service.uploadReference(TASK, png);

        assertEquals(777L, fileId);
        verify(contentTaskService).uploadFile(eq(TASK), isNull(), any(), any());
        verify(productService, never()).bindImageFromFile(any(), any());
    }

    @Test
    @DisplayName("「同时设为产品图」但没有关联产品 → 仍然先拒（不传完再报错留一张孤儿图）")
    void refusesProductImageBindingWithoutProduct() {
        givenTask("ECOM_DETAIL");
        org.dromara.creative.domain.DpDeliveryType enabled = new org.dromara.creative.domain.DpDeliveryType();
        enabled.setDeliveryType("ECOM_DETAIL");
        enabled.setEnabled("0");
        when(scenarioConfigService.getDeliveryType("ECOM_DETAIL")).thenReturn(enabled);
        when(scenarioConfigService.getScenario("ECOM_DETAIL"))
            .thenReturn(new org.dromara.creative.domain.DpScenarioProfile());
        when(fileMapper.selectList(any())).thenReturn(java.util.List.<CpTaskFile>of());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadReference(TASK, png, true));

        assertEquals("该项目没有关联产品，无法设为产品图；请先在项目里选择产品", ex.getMessage());
        verify(contentTaskService, never())
            .uploadFile(any(), isNull(), any(), any());
        verify(productService, never()).bindImageFromFile(any(), any());
    }

    @Test
    @DisplayName("非图片文件 → 拒绝（原有的 MIME 校验不能被这次重构弄丢）")
    void stillRefusesNonImage() {
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt", "text/plain",
            "hello".getBytes(StandardCharsets.UTF_8));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.uploadReference(TASK, txt));

        assertEquals("参考图必须是图片（png/jpg/webp）", ex.getMessage());
        verify(contentTaskService, never())
            .uploadFile(any(), isNull(), any(), any());
    }
}
