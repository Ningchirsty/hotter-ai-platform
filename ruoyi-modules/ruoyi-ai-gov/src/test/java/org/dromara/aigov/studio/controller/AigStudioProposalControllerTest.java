package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalDecideBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更提案接口测试（增量 21／附件 07 增量 6）。
 *
 * <p><b>两点必须钉住</b>：①每个入口都带权限点——采纳提案会写草稿修订，
 * 不能是开放接口；②"谁提的、谁批的"取自登录态，不从请求体取，
 * 否则可以冒名提交/采纳别人的提案。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioProposalControllerTest {

    private static final long ACTOR = 200L;
    private static final long DRAFT_ID = 9001L;
    private static final long PROPOSAL_ID = 5001L;

    private IAigStudioProposalService proposalService;
    private AigStudioActorProvider actorProvider;
    private AigStudioProposalController controller;

    @BeforeEach
    void setUp() {
        proposalService = mock(IAigStudioProposalService.class);
        actorProvider = mock(AigStudioActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(ACTOR);
        controller = new AigStudioProposalController(proposalService, actorProvider);
    }

    @Test
    @DisplayName("创建提案：操作者取自登录态并透传给服务层")
    void createUsesLoginActor() {
        AigStudioChangeProposalVo vo = new AigStudioChangeProposalVo();
        vo.setProposalId(PROPOSAL_ID);
        when(proposalService.create(eq(DRAFT_ID), any(), eq(ACTOR))).thenReturn(vo);
        AigStudioProposalCreateBo bo = new AigStudioProposalCreateBo();

        R<AigStudioChangeProposalVo> result = controller.create(DRAFT_ID, bo);

        assertEquals(PROPOSAL_ID, result.getData().getProposalId());
        verify(proposalService).create(DRAFT_ID, bo, ACTOR);
    }

    @Test
    @DisplayName("列表：读草稿的提案，操作者不参与（只读）")
    void listDelegatesToService() {
        when(proposalService.list(DRAFT_ID)).thenReturn(List.of());

        R<List<AigStudioChangeProposalVo>> result = controller.list(DRAFT_ID);

        assertTrue(result.getData().isEmpty());
        verify(proposalService).list(DRAFT_ID);
    }

    @Test
    @DisplayName("采纳：操作者取自登录态（「谁批的」是事实，不能由调用方自报）")
    void applyUsesLoginActor() {
        AigStudioProposalDecideBo bo = new AigStudioProposalDecideBo();
        bo.setExpectedRevision(1);

        controller.apply(PROPOSAL_ID, bo);

        verify(proposalService).apply(PROPOSAL_ID, bo, ACTOR);
    }

    @Test
    @DisplayName("拒绝：即使不带请求体也要能拒绝（原因可空），操作者仍取自登录态")
    void rejectAllowsMissingBody() {
        controller.reject(PROPOSAL_ID, null);

        verify(proposalService).reject(PROPOSAL_ID, null, ACTOR);
    }

    @Test
    @DisplayName("★ 没有登录用户时拒绝写操作（不能把「不知道是谁」当成系统身份放行）")
    void missingLoginIsRejected() {
        when(actorProvider.currentUserId()).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> controller.create(DRAFT_ID, new AigStudioProposalCreateBo()));

        assertTrue(ex.getMessage().contains("登录用户"), ex.getMessage());
        verify(proposalService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("★ 每个入口都必须带权限点，写入口还必须有防重复提交")
    void everyEndpointRequiresItsPermission() throws Exception {
        assertPermission("create", new Class<?>[]{Long.class, AigStudioProposalCreateBo.class});
        assertPermission("list", new Class<?>[]{Long.class});
        assertPermission("apply", new Class<?>[]{Long.class, AigStudioProposalDecideBo.class});
        assertPermission("reject", new Class<?>[]{Long.class, AigStudioProposalDecideBo.class});

        assertRepeatSubmit("create", new Class<?>[]{Long.class, AigStudioProposalCreateBo.class});
        assertRepeatSubmit("apply", new Class<?>[]{Long.class, AigStudioProposalDecideBo.class});
        assertRepeatSubmit("reject", new Class<?>[]{Long.class, AigStudioProposalDecideBo.class});
    }

    private static void assertPermission(String method, Class<?>[] params) throws Exception {
        Method m = AigStudioProposalController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何人都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(AigConstants.PERM_STUDIO_COPILOT_USE),
            method + " 的权限点应为 " + AigConstants.PERM_STUDIO_COPILOT_USE + "，实际=" + values);
    }

    private static void assertRepeatSubmit(String method, Class<?>[] params) throws Exception {
        Method m = AigStudioProposalController.class.getMethod(method, params);
        assertNotNull(m.getAnnotation(RepeatSubmit.class),
            method + " 是写入口，必须带 @RepeatSubmit（否则连点两下会提两条）");
    }

}
