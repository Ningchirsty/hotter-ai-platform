package org.dromara.aigov.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.studio.domain.bo.AigStudioActionDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactGenerateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioChildAgentDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioSkillDraftBo;
import org.dromara.aigov.studio.domain.vo.AigStudioArtifactGenerateVo;
import org.dromara.aigov.studio.enums.AigStudioArtifactKindEnum;
import org.dromara.aigov.studio.helper.AigStudioActorProvider;
import org.dromara.aigov.studio.service.IAigStudioArtifactDraftService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 草案生成器接口测试（附件 07 增量 7）。
 *
 * <p><b>三点必须钉住</b>：①每个入口都带权限点；②<b>生成与新增草案用的是两个权限</b>——
 * 生成会写草稿（要「编辑草稿」），新增草案不会（只需草案权限），把两者合成一个等于让
 * "能登记草案的人"顺带获得改草稿的能力；③草案类型走路径片段，认不出的片段要报错并列出可选值，
 * 而不是悄悄退化成某一类。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioArtifactDraftControllerTest {

    private static final long ACTOR = 200L;
    private static final long DRAFT_ID = 6101L;
    private static final long TARGET_DRAFT = 9001L;

    private IAigStudioArtifactDraftService artifactDraftService;
    private AigStudioActorProvider actorProvider;
    private AigStudioArtifactDraftController controller;

    @BeforeEach
    void setUp() {
        artifactDraftService = mock(IAigStudioArtifactDraftService.class);
        actorProvider = mock(AigStudioActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(ACTOR);
        controller = new AigStudioArtifactDraftController(artifactDraftService, actorProvider);
    }

    private static AigStudioArtifactGenerateBo generateBo() {
        AigStudioArtifactGenerateBo bo = new AigStudioArtifactGenerateBo();
        bo.setTargetDraftId(TARGET_DRAFT);
        bo.setExpectedRevision(2);
        return bo;
    }

    @Test
    @DisplayName("三个新增入口：操作者取自登录态并透传")
    void createsUseLoginActor() {
        controller.createSkillDraft(new AigStudioSkillDraftBo());
        verify(artifactDraftService).createSkillDraft(any(), eq(ACTOR));

        controller.createChildAgentDraft(new AigStudioChildAgentDraftBo());
        verify(artifactDraftService).createChildAgentDraft(any(), eq(ACTOR));

        controller.createActionDraft(new AigStudioActionDraftBo());
        verify(artifactDraftService).createActionDraft(any(), eq(ACTOR));
    }

    @Test
    @DisplayName("★ 生成：类型片段解析成枚举，目标草稿与期望修订号透传（写草稿的 CAS 不能被吞掉）")
    void generatePassesKindAndCas() {
        AigStudioArtifactGenerateVo vo = new AigStudioArtifactGenerateVo();
        vo.setKind("CHILD_AGENT");
        when(artifactDraftService.generate(any(), any(), any(), any(), any())).thenReturn(vo);

        R<AigStudioArtifactGenerateVo> result =
            controller.generate("child-agent", DRAFT_ID, generateBo());

        assertEquals("CHILD_AGENT", result.getData().getKind());
        verify(artifactDraftService).generate(eq(AigStudioArtifactKindEnum.CHILD_AGENT),
            eq(DRAFT_ID), eq(TARGET_DRAFT), eq(2), eq(ACTOR));
    }

    @Test
    @DisplayName("★ 草案类型片段认不出 → 报错并列出可选片段（不能悄悄退化成某一类）")
    void unknownKindIsRejected() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> controller.generate("nope", DRAFT_ID, generateBo()));

        assertTrue(error.getMessage().contains("未知的草案类型"), error.getMessage());
        assertTrue(error.getMessage().contains("skill"), error.getMessage());
        assertTrue(error.getMessage().contains("child-agent"), error.getMessage());
        assertTrue(error.getMessage().contains("action"), error.getMessage());
        verify(artifactDraftService, never()).generate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 没有登录用户时拒绝写操作（草案要记责任人，生成要记操作人）")
    void missingLoginIsRejected() {
        when(actorProvider.currentUserId()).thenReturn(null);

        ServiceException createError = assertThrows(ServiceException.class,
            () -> controller.createSkillDraft(new AigStudioSkillDraftBo()));
        assertTrue(createError.getMessage().contains("登录用户"), createError.getMessage());

        ServiceException generateError = assertThrows(ServiceException.class,
            () -> controller.generate("skill", DRAFT_ID, generateBo()));
        assertTrue(generateError.getMessage().contains("登录用户"), generateError.getMessage());
        verify(artifactDraftService, never()).generate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 权限点：草案管理用 aig:studio:artifact-draft；生成用 aig:studio:draft:edit（它写草稿）")
    void everyEndpointRequiresItsPermission() throws Exception {
        assertPermission("createSkillDraft", new Class<?>[]{AigStudioSkillDraftBo.class},
            AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        assertPermission("createChildAgentDraft", new Class<?>[]{AigStudioChildAgentDraftBo.class},
            AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        assertPermission("createActionDraft", new Class<?>[]{AigStudioActionDraftBo.class},
            AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        Class<?>[] listParams = {AigStudioArtifactDraftQueryBo.class, PageQuery.class};
        assertPermission("skillDraftList", listParams, AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        assertPermission("childAgentDraftList", listParams, AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        assertPermission("actionDraftList", listParams, AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);
        assertPermission("preview", new Class<?>[]{String.class, Long.class},
            AigConstants.PERM_STUDIO_ARTIFACT_DRAFT);

        // 生成是写草稿：与「编辑草稿」同权限，而不是与草案管理同权限
        assertPermission("generate", new Class<?>[]{String.class, Long.class,
            AigStudioArtifactGenerateBo.class}, AigConstants.PERM_STUDIO_DRAFT_EDIT);

        assertRepeatSubmit("createSkillDraft", new Class<?>[]{AigStudioSkillDraftBo.class});
        assertRepeatSubmit("createChildAgentDraft", new Class<?>[]{AigStudioChildAgentDraftBo.class});
        assertRepeatSubmit("createActionDraft", new Class<?>[]{AigStudioActionDraftBo.class});
        assertRepeatSubmit("generate", new Class<?>[]{String.class, Long.class,
            AigStudioArtifactGenerateBo.class});
    }

    /* ------------------------------------------------------------------ 反射助手 */

    private static void assertPermission(String method, Class<?>[] params, String expected)
        throws Exception {
        Method m = AigStudioArtifactDraftController.class.getMethod(method, params);
        SaCheckPermission annotation = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method + " 没有权限注解 = 任何登录用户都能调");
        Set<String> values = Set.copyOf(Arrays.asList(annotation.value()));
        assertTrue(values.contains(expected),
            method + " 的权限点应为 " + expected + "，实际=" + values);
    }

    private static void assertRepeatSubmit(String method, Class<?>[] params) throws Exception {
        Method m = AigStudioArtifactDraftController.class.getMethod(method, params);
        assertNotNull(m.getAnnotation(RepeatSubmit.class),
            method + " 是写入口，必须带 @RepeatSubmit（否则双击会提两条）");
    }

}
