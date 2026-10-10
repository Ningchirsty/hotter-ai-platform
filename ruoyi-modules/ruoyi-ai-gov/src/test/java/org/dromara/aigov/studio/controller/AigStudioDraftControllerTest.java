package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 训练台草稿接口测试（S3）。
 *
 * <p><b>两点必须钉住</b>：①每个入口都带权限点——训练台的写操作能间接影响后续版本内容，
 * 不能是开放接口；②保存时<b>以路径参数为准</b>——若信请求体里的 draftId，
 * 就会出现"路径写着 A、实际改了 B"的越权面。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioDraftControllerTest {

    private static final long ACTOR = 200L;

    private IAigStudioDraftService draftService;
    private AigStudioActorProvider actorProvider;
    private AigStudioDraftController controller;

    @BeforeEach
    void setUp() {
        draftService = mock(IAigStudioDraftService.class);
        actorProvider = mock(AigStudioActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(ACTOR);
        controller = new AigStudioDraftController(draftService, actorProvider);
    }

    @Test
    @DisplayName("新建草稿：操作者取自登录态并透传给服务层")
    void createUsesLoginActor() {
        when(draftService.createDraft(any(), eq(ACTOR))).thenReturn(9001L);
        AigStudioDraftCreateBo bo = new AigStudioDraftCreateBo();
        bo.setAgentCode("DETAIL_COPYWRITER");

        R<Long> result = controller.create(bo);

        assertEquals(9001L, result.getData());
        verify(draftService).createDraft(bo, ACTOR);
    }

    @Test
    @DisplayName("★ 保存：以路径上的 draftId 为准（否则会出现「路径写 A、实际改了 B」的越权面）")
    void updatePrefersPathDraftIdOverBody() {
        AigStudioDraftSaveBo bo = new AigStudioDraftSaveBo();
        bo.setDraftId(999L);
        bo.setExpectedRevision(1);
        bo.setContentJson("{}");

        controller.update(9001L, bo);

        verify(draftService).saveDraft(bo, ACTOR);
        assertEquals(9001L, bo.getDraftId(), "请求体里的 999 必须被路径覆盖掉");
    }

    @Test
    @DisplayName("★ 没有登录用户时拒绝写操作（不能把「不知道是谁」当成系统身份放行）")
    void missingLoginIsRejected() {
        when(actorProvider.currentUserId()).thenReturn(null);
        AigStudioDraftCreateBo bo = new AigStudioDraftCreateBo();
        bo.setAgentCode("X");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.create(bo));

        assertTrue(ex.getMessage().contains("登录用户"), ex.getMessage());
        verify(draftService, never()).createDraft(any(), any());
    }

    @Test
    @DisplayName("★ 每个入口都必须带权限点（训练台写操作能间接影响后续版本内容，不能是开放接口）")
    void everyEndpointRequiresItsPermission() throws Exception {
        assertPermission("list", listParams(), AigConstants.PERM_STUDIO_DRAFT_LIST);
        assertPermission("get", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_DRAFT_QUERY);
        assertPermission("create", new Class<?>[]{AigStudioDraftCreateBo.class},
            AigConstants.PERM_STUDIO_DRAFT_CREATE);
        assertPermission("update", new Class<?>[]{Long.class, AigStudioDraftSaveBo.class},
            AigConstants.PERM_STUDIO_DRAFT_EDIT);
        assertPermission("revisions", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_DRAFT_QUERY);
        assertPermission("revision", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_DRAFT_QUERY);
        assertPermission("validate", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_DRAFT_VALIDATE);
        assertPermission("rollback", new Class<?>[]{Long.class,
            org.dromara.aigov.studio.domain.bo.AigStudioDraftRollbackBo.class},
            AigConstants.PERM_STUDIO_DRAFT_EDIT);
        assertPermission("archive", new Class<?>[]{Long.class}, AigConstants.PERM_STUDIO_DRAFT_EDIT);
    }

    /**
     * list 的参数类型表（查询 BO + 分页）。
     *
     * @return 参数类型
     */
    private static Class<?>[] listParams() {
        return new Class<?>[]{org.dromara.aigov.studio.domain.bo.AigStudioDraftQueryBo.class,
            org.dromara.common.mybatis.core.page.PageQuery.class};
    }

    private static void assertPermission(String method, Class<?>[] params, String expected) throws Exception {
        Method m = AigStudioDraftController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何人都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(expected),
            method + " 的权限点应为 " + expected + "，实际=" + values);
    }

}
