package org.dromara.aigov.task.service.impl;

import org.dromara.aigov.task.domain.bo.AigTaskMirrorQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorSourceVo;
import org.dromara.aigov.task.domain.vo.AigTaskMirrorVo;
import org.dromara.aigov.task.service.IAigTaskMirrorProvider;
import org.dromara.aigov.task.service.IAigTaskMirrorService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只读镜像的注册与分发测试。
 *
 * <p><b>为什么这些边界值得单独测</b>：镜像层是「跨模块只读视图」，它自己不出错的方式有两种——
 * ①来源重复时<b>立刻</b>失败（否则「查这个来源得到谁的数据」取决于启动顺序，时好时坏）；
 * ②来源不存在时报错能给出可选值（否则拼错一个 code 只能去翻代码）。
 * 这两条都属于「不生效也不会报错」的类型，必须由测试钉住。
 * 另外还有一条只能靠结构保证的：镜像行必须恒为只读。</p>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskMirrorServiceImplTest {

    private static final String SOURCE = "DP_GENERATION";

    /**
     * 造一个提供者桩。
     *
     * @param source 来源编码
     * @param label  展示名
     * @return 桩提供者
     */
    private static IAigTaskMirrorProvider provider(String source, String label) {
        IAigTaskMirrorProvider provider = mock(IAigTaskMirrorProvider.class);
        when(provider.source()).thenReturn(source);
        when(provider.label()).thenReturn(label);
        when(provider.description()).thenReturn(label + " 的说明");
        return provider;
    }

    /**
     * 造一个查询入参。
     *
     * @return 查询入参
     */
    private static AigTaskMirrorQueryBo queryBo() {
        AigTaskMirrorQueryBo bo = new AigTaskMirrorQueryBo();
        bo.setSource(SOURCE);
        return bo;
    }

    @Test
    @DisplayName("来源重复：启动期直接失败并点出两个实现类（否则结果取决于启动顺序，时好时坏）")
    void duplicateSourceFailsAtRegistration() {
        IAigTaskMirrorProvider first = provider(SOURCE, "创作域生成记录");
        IAigTaskMirrorProvider second = provider(SOURCE, "另一个也想叫这个名的来源");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new AigTaskMirrorServiceImpl(List.of(first, second)));

        assertTrue(error.getMessage().contains(SOURCE), "报错要指出是哪个来源重复：" + error.getMessage());
        assertTrue(error.getMessage().contains(first.getClass().getName())
                && error.getMessage().contains(second.getClass().getName()),
            "报错要指出是哪两个实现类打架：" + error.getMessage());
    }

    @Test
    @DisplayName("来源编码为空：启动期失败（空来源会让清单里出现一个点不动的条目）")
    void blankSourceFailsAtRegistration() {
        IAigTaskMirrorProvider blank = provider("   ", "没有来源编码");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new AigTaskMirrorServiceImpl(List.of(blank)));

        assertTrue(error.getMessage().contains("来源编码"), "报错要点明是 source() 没给：" + error.getMessage());
    }

    @Test
    @DisplayName("来源清单按编码排序，且带上说明（前端据此渲染选择器）")
    void listSourcesIsSortedAndDescribed() {
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(List.of(
            provider(SOURCE, "创作域生成记录"),
            provider("IMAGE_TASK", "图像内核任务")));

        List<AigTaskMirrorSourceVo> sources = service.listSources();

        assertEquals(2, sources.size());
        assertEquals(SOURCE, sources.get(0).getSource(), "按编码升序：DP_GENERATION 在 IMAGE_TASK 之前");
        assertEquals("IMAGE_TASK", sources.get(1).getSource());
        assertEquals("创作域生成记录", sources.get(0).getLabel());
        assertTrue(sources.get(0).getDescription().contains("说明"), "说明要透传，供页面提示只读口径");
    }

    @Test
    @DisplayName("没有来源也能正常启动：清单为空，查询给出一句自解释的报错")
    void emptyRegistryIsUsable() {
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(List.of());

        assertTrue(service.listSources().isEmpty(), "没有业务域注册时清单为空，但服务本身要能起来");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.queryPage(queryBo(), new PageQuery()));
        assertTrue(error.getMessage().contains("未知的镜像来源"), error.getMessage());
        assertTrue(error.getMessage().contains("没有任何业务域注册"),
            "空注册表时的报错要说清是「没人注册」而不是「你拼错了」：" + error.getMessage());
    }

    @Test
    @DisplayName("来源不存在：报错列出全部可选来源（拼错一个 code 不该让人去翻代码）")
    void unknownSourceListsAlternatives() {
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(
            List.of(provider(SOURCE, "创作域生成记录")));
        AigTaskMirrorQueryBo bo = new AigTaskMirrorQueryBo();
        bo.setSource("DP_GENERATON");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.queryPage(bo, new PageQuery()));

        assertTrue(error.getMessage().contains("DP_GENERATON"), error.getMessage());
        assertTrue(error.getMessage().contains(SOURCE), "必须列出可用来源：" + error.getMessage());
    }

    @Test
    @DisplayName("来源为空：拒绝查询，并说明为什么不提供跨来源合并分页")
    void blankSourceIsRejected() {
        IAigTaskMirrorProvider provider = provider(SOURCE, "创作域生成记录");
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(List.of(provider));
        AigTaskMirrorQueryBo bo = new AigTaskMirrorQueryBo();

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.queryPage(bo, new PageQuery()));

        assertTrue(error.getMessage().contains("分页语义"),
            "拒绝的理由要写出来（各来源分页语义不同，合成一页会让页码与总数失真）：" + error.getMessage());
        verify(provider, never()).queryPage(any(), any());
    }

    @Test
    @DisplayName("查询与详情都分发到对应来源的实现")
    void delegatesToMatchingProvider() {
        IAigTaskMirrorProvider provider = provider(SOURCE, "创作域生成记录");
        PageResult<AigTaskMirrorVo> expected = PageResult.build(List.of(new AigTaskMirrorVo()), 1L);
        when(provider.queryPage(any(), any())).thenReturn(expected);
        AigTaskMirrorVo detail = new AigTaskMirrorVo();
        detail.setRefId("8801");
        when(provider.getDetail("8801")).thenReturn(detail);
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(List.of(provider));

        assertSame(expected, service.queryPage(queryBo(), new PageQuery()));
        assertSame(detail, service.getDetail(SOURCE, "8801"));
        verify(provider).queryPage(any(), any());
        verify(provider).getDetail("8801");
    }

    @Test
    @DisplayName("详情：来源或行ID为空时拒绝（不拿空主键去查全表）")
    void detailRejectsBlankArguments() {
        IAigTaskMirrorProvider provider = provider(SOURCE, "创作域生成记录");
        IAigTaskMirrorService service = new AigTaskMirrorServiceImpl(List.of(provider));

        assertThrows(ServiceException.class, () -> service.getDetail(" ", "8801"));
        assertThrows(ServiceException.class, () -> service.getDetail(SOURCE, " "));
        verify(provider, never()).getDetail(any());
    }

    @Test
    @DisplayName("镜像行恒为只读：VO 没有可被置 false 的入口")
    void mirrorRowIsAlwaysReadOnly() {
        AigTaskMirrorVo vo = new AigTaskMirrorVo();

        assertTrue(vo.isReadOnly(), "镜像行在任何情况下都不接受写操作");
        // 结构性保证：isReadOnly 是写死返回 true 的方法，没有字段也没有 setter，
        // 因此不存在「某处忘了置 true」这种可能
        assertThrows(NoSuchMethodException.class,
            () -> AigTaskMirrorVo.class.getMethod("setReadOnly", boolean.class));
    }

}
