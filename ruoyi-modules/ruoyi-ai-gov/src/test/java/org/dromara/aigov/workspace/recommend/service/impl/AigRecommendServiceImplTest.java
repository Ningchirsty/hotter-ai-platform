package org.dromara.aigov.workspace.recommend.service.impl;

import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalActionVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.aigov.workspace.recommend.config.AigRecommendProperties;
import org.dromara.aigov.workspace.recommend.domain.bo.AigRecommendSuggestBo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendResultVo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐服务层的"过滤与失败语义"测试（增量 7）。
 *
 * <p>透传与容错的数学已经在 {@code AigRecommendHelperTest} 里钉过，这里只钉服务层独有的三件事：</p>
 * <ol>
 *     <li><b>默认关着就报错</b>，而且**一次模型调用都不发**——静默返回空会被读成"没有相关卡片"；</li>
 *     <li><b>模型吐出不存在的编码必须被丢掉</b>（这是"看起来能用、其实把可见性交给了模型"的失效点）；</li>
 *     <li><b>调用没成功要抛错</b>（配额/策略/超时不能被伪装成"没有推荐"）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRecommendServiceImplTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    private IAigPortalService portalService;
    private IAigInvokeService invokeService;
    private AigRecommendProperties properties;
    private AigRecommendServiceImpl service;

    @BeforeEach
    void setUp() {
        portalService = mock(IAigPortalService.class);
        invokeService = mock(IAigInvokeService.class);
        properties = new AigRecommendProperties();
        properties.setEnabled(true);
        properties.setCapabilityCode("intent_suggest");
        properties.setDataLevel("INTERNAL");
        service = new AigRecommendServiceImpl(portalService, invokeService, properties);
    }

    @Test
    @DisplayName("★模型返回不存在的编码一律丢掉：只留服务端可见清单里真实存在的那张卡片")
    void nonVisibleCodeIsDropped() {
        when(portalService.listMyRoleHomes(ACTOR)).thenReturn(List.of(role()));
        when(invokeService.invoke(any())).thenReturn(invoked(
            "{\"actionCodes\":[\"DETAIL_PAGE_CREATE\",\"HALLUCINATED_CARD\",\"BANNER_CREATE\"]}"));

        AigRecommendResultVo result = service.suggest(suggest("给我做个详情页"), ACTOR);

        // 模型给了 3 个，只有 2 张真的在可见清单里
        assertEquals(2, result.getSuggestions().size());
        assertTrue(result.getSuggestions().stream()
            .noneMatch(item -> "HALLUCINATED_CARD".equals(item.getAction().getActionCode())),
            "不存在的编码不得出现在结果里");
        assertEquals("GRAPHIC_DESIGNER_AI", result.getSuggestions().get(0).getRoleCode());
        assertEquals("DETAIL_PAGE_CREATE", result.getSuggestions().get(0).getAction().getActionCode());
    }

    @Test
    @DisplayName("提示词里只放可见卡片，并把员工需求带给模型；能力与数据等级来自配置")
    void promptCarriesOnlyVisibleCardsAndDemand() {
        when(portalService.listMyRoleHomes(ACTOR)).thenReturn(List.of(role()));
        when(invokeService.invoke(any())).thenReturn(invoked("{\"actionCodes\":[\"DETAIL_PAGE_CREATE\"]}"));

        service.suggest(suggest("我要给新品做一套详情页"), ACTOR);

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        AigInvokeBo bo = captor.getValue();
        assertEquals("intent_suggest", bo.getCapabilityCode());
        assertEquals("INTERNAL", bo.getDataLevel());
        assertTrue(bo.getPrompt().contains("DETAIL_PAGE_CREATE"));
        assertTrue(bo.getPrompt().contains("我要给新品做一套详情页"), "员工需求要进提示词");
        // 推荐不是任务，也不该算成某个 Agent 版本的灰度调用
        assertEquals(null, bo.getAgentVersionId());
        assertEquals(null, bo.getTaskId());
    }

    @Test
    @DisplayName("关着就明确报错，且一次模型调用都不发（不能静默返回空）")
    void disabledFailsLoudly() {
        properties.setEnabled(false);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.suggest(suggest("做张海报"), ACTOR));
        assertTrue(e.getMessage().contains("关闭"), e.getMessage());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("输入为空/过长都报错，不花这次钱")
    void blankAndTooLongInputFail() {
        assertThrows(ServiceException.class, () -> service.suggest(suggest("   "), ACTOR));
        assertThrows(ServiceException.class, () -> service.suggest(suggest(null), ACTOR));
        properties.setMaxInputChars(5);
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.suggest(suggest("这是一句明显超过五个字的描述"), ACTOR));
        assertTrue(e.getMessage().contains("过长"), e.getMessage());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("没有可见卡片：如实说明并**不调用**模型（白花钱没有意义），也不报错")
    void emptyCatalogDoesNotCallModel() {
        when(portalService.listMyRoleHomes(ACTOR)).thenReturn(List.of());

        AigRecommendResultVo result = service.suggest(suggest("做张海报"), ACTOR);

        assertTrue(result.getSuggestions().isEmpty());
        assertNotNull(result.getReason());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("调用没成功（策略拒绝/配额耗尽）必须抛错，不能伪装成「没有推荐」")
    void failedInvocationThrows() {
        when(portalService.listMyRoleHomes(ACTOR)).thenReturn(List.of(role()));
        AigInvokeVo denied = new AigInvokeVo();
        denied.setErrorCode("POLICY_DENIED");
        denied.setReason("策略拒绝：未配置 intent_suggest 的路由策略");
        when(invokeService.invoke(any())).thenReturn(denied);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.suggest(suggest("做张海报"), ACTOR));
        assertTrue(e.getMessage().contains("未成功"), e.getMessage());
        assertTrue(e.getMessage().contains("未配置 intent_suggest"), e.getMessage());
    }

    @Test
    @DisplayName("解析不出结构时返回空结果（不是错误）：模型没按格式说，就当作没有推荐")
    void unparsableOutputYieldsEmpty() {
        when(portalService.listMyRoleHomes(ACTOR)).thenReturn(List.of(role()));
        when(invokeService.invoke(any())).thenReturn(invoked("我不知道该推荐什么"));

        AigRecommendResultVo result = service.suggest(suggest("做张海报"), ACTOR);

        assertTrue(result.getSuggestions().isEmpty());
    }

    private static AigRecommendSuggestBo suggest(String input) {
        AigRecommendSuggestBo bo = new AigRecommendSuggestBo();
        bo.setInput(input);
        return bo;
    }

    private static AigInvokeVo invoked(String output) {
        AigInvokeVo vo = new AigInvokeVo();
        vo.setOutput(output);
        return vo;
    }

    private static AigPortalRoleHomeVo role() {
        AigPortalRoleHomeVo role = new AigPortalRoleHomeVo();
        role.setRoleCode("GRAPHIC_DESIGNER_AI");
        role.setRoleName("平面设计 AI 工作台");
        role.setActions(List.of(
            action("DETAIL_PAGE_CREATE", "做详情页"),
            action("BANNER_CREATE", "做横幅")));
        return role;
    }

    private static AigPortalActionVo action(String code, String title) {
        AigPortalActionVo action = new AigPortalActionVo();
        action.setActionCode(code);
        action.setTitle(title);
        action.setLaunchMode("QUICK");
        action.setTargetType("QUICK_CAPABILITY");
        action.setTargetRef("cap/" + code);
        return action;
    }

}
