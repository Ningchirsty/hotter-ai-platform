package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioCopilotService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
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
 * 对话式 Copilot 接口测试（附件 07 增量 6）。
 *
 * <p><b>三点必须钉住</b>：①<b>发起对话用独立权限</b>（它花钱），与"看会话历史"分开——
 * 合成一个等于让"能看提案的人"顺带获得花钱的能力；②对话入口必须带 {@code @RepeatSubmit}
 * （双击就是两次真实调用、两次计费）；③操作者取自登录态。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioCopilotControllerTest {

    private static final long ACTOR = 200L;
    private static final long DRAFT_ID = 9001L;

    private IAigStudioCopilotService copilotService;
    private AigStudioActorProvider actorProvider;
    private AigStudioCopilotController controller;

    @BeforeEach
    void setUp() {
        copilotService = mock(IAigStudioCopilotService.class);
        actorProvider = mock(AigStudioActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(ACTOR);
        controller = new AigStudioCopilotController(copilotService, actorProvider);
    }

    @Test
    @DisplayName("对话：操作者取自登录态并透传（这一轮要花钱，必须能追到人）")
    void chatUsesLoginActor() {
        AigStudioCopilotChatBo bo = new AigStudioCopilotChatBo();
        bo.setMessage("把目标改一下");
        bo.setDataLevel("INTERNAL");

        controller.chat(DRAFT_ID, bo);

        verify(copilotService).chat(DRAFT_ID, bo, ACTOR);
    }

    @Test
    @DisplayName("会话历史：透传草稿ID（不需要操作者：只读）")
    void turnsDelegates() {
        controller.turns(DRAFT_ID);

        verify(copilotService).turns(DRAFT_ID);
        verify(copilotService, never()).chat(any(), any(), any());
    }

    @Test
    @DisplayName("★ 没有登录用户时拒绝对话（不能把「不知道是谁」当成系统身份放行）")
    void missingLoginIsRejected() {
        when(actorProvider.currentUserId()).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
            () -> controller.chat(DRAFT_ID, new AigStudioCopilotChatBo()));

        assertTrue(error.getMessage().contains("登录用户"), error.getMessage());
        verify(copilotService, never()).chat(any(), any(), any());
    }

    @Test
    @DisplayName("★ 权限点：发起对话用 aig:studio:copilot:chat（花钱的动作单独授权），历史用 copilot:use")
    void permissionsAreSeparated() throws Exception {
        assertPermission("chat", new Class<?>[]{Long.class, AigStudioCopilotChatBo.class},
            AigConstants.PERM_STUDIO_COPILOT_CHAT);
        assertPermission("turns", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_COPILOT_USE);

        assertTrue(!AigConstants.PERM_STUDIO_COPILOT_CHAT.equals(AigConstants.PERM_STUDIO_COPILOT_USE),
            "「花钱的对话」与「看/处理提案」不该共用一个权限串（同 aig:studio:test:run vs test:view）");

        Method chat = AigStudioCopilotController.class.getMethod("chat", Long.class,
            AigStudioCopilotChatBo.class);
        assertNotNull(chat.getAnnotation(RepeatSubmit.class),
            "双击就是两次真实调用、两次计费，必须带 @RepeatSubmit");
    }

    private static void assertPermission(String method, Class<?>[] params, String expected)
        throws Exception {
        Method m = AigStudioCopilotController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何登录用户都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(expected),
            method + " 的权限点应为 " + expected + "，实际=" + values);
    }

}
