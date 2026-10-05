package org.dromara.creative.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.helper.ContentOssHelper;
import org.dromara.content.service.IContentTaskService;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.helper.RendererClient;
import org.dromara.creative.helper.SimpleMultipartFile;
import org.dromara.creative.mapper.DpDetailPageMapper;
import org.dromara.creative.mapper.DpDetailPageVersionMapper;
import org.dromara.creative.mapper.DpGenerationMapper;
import org.dromara.creative.service.ICreativeCopyService;
import org.dromara.creative.service.ICreativeDnaService;
import org.dromara.creative.service.ICreativeGateService;
import org.dromara.creative.service.ICreativeGenerationService;
import org.dromara.creative.service.ICreativeModuleService;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.dromara.creative.service.ICreativeTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「空屏也能交付」的确认语义（内测 S21 / C9 的回归钉）。
 *
 * <p><b>为什么这条必须钉住两个方向</b>：C9 修的是"7 屏只有 2 屏有产出，照样交付 V1.0"，
 * 但它的解法是<b>确认</b>而不是<b>拦截</b>——因为"先把做好的部分交出去"是这条产线上的真实业务。
 * 于是有两种等价的错误：</p>
 * <ol>
 *   <li><b>不拦也不问</b>：确认参数被忽略，空屏照样静默交付（就是原缺陷）；</li>
 *   <li><b>一律硬拦</b>：确认了也不放行，把"排到一半先交"直接堵死。</li>
 * </ol>
 * <p>下面两个用例分别钉住这两侧。真实链路（前端确认弹窗 + 事件留痕 + 刷新后仍可见）
 * 由本地 R53 实例验证。</p>
 *
 * @author creative
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreativeFinalShortfallTest {

    @Mock
    private DpDetailPageMapper pageMapper;
    @Mock
    private DpDetailPageVersionMapper versionMapper;
    @Mock
    private DpGenerationMapper generationMapper;
    @Mock
    private ICreativeProjectService projectService;
    @Mock
    private ICreativeGateService gateService;
    @Mock
    private ICreativeDnaService dnaService;
    @Mock
    private ICreativeStoryboardService storyboardService;
    @Mock
    private ICreativeGenerationService generationService;
    @Mock
    private ICreativeTemplateService templateService;
    @Mock
    private ICreativeModuleService moduleService;
    @Mock
    private RendererClient rendererClient;
    @Mock
    private IContentTaskService contentTaskService;
    @Mock
    private ContentOssHelper contentOssHelper;
    @Mock
    private ICreativeCopyService copyService;
    @Mock
    private ICreativeScenarioConfigService scenarioConfigService;

    @InjectMocks
    private CreativeLayoutServiceImpl layoutService;

    private static final long TASK_ID = 7L;

    /** 分镜共 3 屏；generationMapper 查不到任何已选定产出 → 3 屏全缺 */
    private void storyboardWithThreeScreensAndNoApprovedOutput() {
        DpStoryboardVo storyboard = new DpStoryboardVo();
        storyboard.setScreens(List.of(screen("01"), screen("02"), screen("03")));
        when(storyboardService.latest(TASK_ID)).thenReturn(storyboard);
        when(generationMapper.selectList(any())).thenReturn(List.of());
    }

    private static DpStoryboardScreenVo screen(String no) {
        DpStoryboardScreenVo screen = new DpStoryboardScreenVo();
        screen.setScreenNo(no);
        screen.setId(no.hashCode() * 1L);
        return screen;
    }

    /** 一张真图（1×1 PNG）：尺寸读取走 ImageIO，不能拿假字节糊弄 */
    private static SimpleMultipartFile tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new SimpleMultipartFile("file", "final.png", "image/png", out.toByteArray());
    }

    @Test
    @DisplayName("没确认就不给交：拒绝、列出缺哪几屏、且什么都不落库")
    void rejectsUnacknowledgedShortfall() throws Exception {
        storyboardWithThreeScreensAndNoApprovedOutput();

        ServiceException ex = assertThrows(ServiceException.class,
            () -> layoutService.uploadFinal(TASK_ID, tinyPng(), "人工精修最终版", false));

        assertTrue(ex.getMessage().contains("01"), "缺哪些屏要看得见：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("03"), "缺哪些屏要看得见：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("带空屏交付"), "要说清这是什么性质：" + ex.getMessage());
        // 拒绝必须发生在写入之前：不能"先登记版本再报错"
        verifyNoInteractions(versionMapper, pageMapper, contentTaskService);
    }

    @Test
    @DisplayName("确认了就放行：不再被 C9 拦下（异常原因不得是空屏）")
    void acknowledgedShortfallIsNotBlockedByC9() throws Exception {
        storyboardWithThreeScreensAndNoApprovedOutput();
        when(pageMapper.selectList(any())).thenReturn(List.of());
        when(contentTaskService.uploadFile(anyLong(), any(), any(), anyString())).thenReturn(999L);
        // 探针：守卫之后、事件 JSON 之前（后者在纯单测里需要 Spring 上下文）。
        // 用"在这里炸掉"来确定流程确实越过了 C9，而不是靠抓一堆未知异常猜。
        when(projectService.getProject(TASK_ID))
            .thenThrow(new IllegalStateException("probe-after-shortfall-guard"));

        Throwable thrown = assertThrows(Throwable.class,
            () -> layoutService.uploadFinal(TASK_ID, tinyPng(), "人工精修最终版", true));

        assertEquals("probe-after-shortfall-guard", thrown.getMessage(),
            "已确认仍被 C9 拦下：确认式门禁退化成硬拦。实际异常：" + thrown.getMessage());
        // 关键证据：确认后确实走到了"登记附件"这一步
        verify(contentTaskService).uploadFile(anyLong(), isNull(), any(), anyString());
    }
}
