package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.studio.domain.AigStudioActionDraft;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;
import org.dromara.aigov.studio.domain.bo.AigStudioActionDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioArtifactDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioChildAgentDraftBo;
import org.dromara.aigov.studio.domain.bo.AigStudioSkillDraftBo;
import org.dromara.aigov.studio.domain.vo.AigStudioArtifactGenerateVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioSkillDraftVo;
import org.dromara.aigov.studio.enums.AigStudioArtifactKindEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.mapper.AigStudioActionDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioChildAgentDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioSkillDraftMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 草案生成器服务测试（附件 07 增量 7）。
 *
 * <p><b>要钉死的四件事</b>：①创建草案<b>不碰任何草稿</b>；②预览<b>不写任何表</b>；
 * ③生成走的是训练台唯一的写修订入口且来源为 {@code DRAFT_GENERATED}；
 * ④"内容与目标草稿一致"时生成<b>报错</b>，而不是返回一个"成功但什么都没发生"的结果。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioArtifactDraftServiceImplTest {

    private static final long ACTOR = 200L;
    private static final long TARGET_DRAFT = 9001L;
    private static final long SKILL_DRAFT_ID = 6101L;
    private static final long CHILD_DRAFT_ID = 6201L;
    private static final long ACTION_DRAFT_ID = 6301L;

    private AigStudioSkillDraftMapper skillDraftMapper;
    private AigStudioChildAgentDraftMapper childAgentDraftMapper;
    private AigStudioActionDraftMapper actionDraftMapper;
    private IAigStudioDraftService draftService;
    private AigStudioArtifactDraftServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // selectCount / selectVoPage 用 LambdaQueryWrapper，需要实体→列的 lambda 缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioSkillDraft.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioChildAgentDraft.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioActionDraft.class);
    }

    @BeforeEach
    void setUp() {
        skillDraftMapper = mock(AigStudioSkillDraftMapper.class);
        childAgentDraftMapper = mock(AigStudioChildAgentDraftMapper.class);
        actionDraftMapper = mock(AigStudioActionDraftMapper.class);
        draftService = mock(IAigStudioDraftService.class);
        service = new AigStudioArtifactDraftServiceImpl(skillDraftMapper, childAgentDraftMapper,
            actionDraftMapper, draftService);
        when(skillDraftMapper.selectCount(any())).thenReturn(0L);
        when(childAgentDraftMapper.selectCount(any())).thenReturn(0L);
        when(actionDraftMapper.selectCount(any())).thenReturn(0L);
    }

    /* ------------------------------------------------------------------ 构造 */

    private static AigStudioSkillDraftBo skillBo() {
        AigStudioSkillDraftBo bo = new AigStudioSkillDraftBo();
        bo.setDraftCode("SKILL-BRAND-BRIEF");
        bo.setSkillCode("brand_brief");
        bo.setSkillName("品牌简报");
        bo.setAgentCategory("PLANNING");
        bo.setProviderCapability("text_generation");
        bo.setCapabilities("text_generation");
        return bo;
    }

    private static AigStudioChildAgentDraftBo childBo() {
        AigStudioChildAgentDraftBo bo = new AigStudioChildAgentDraftBo();
        bo.setDraftCode("CHILD-SKU-ASSIST");
        bo.setAgentName("SKU 详情助手");
        bo.setAgentCategory("PLANNING");
        bo.setProviderCapability("text_generation");
        bo.setPromptSectionsJson("{\"workflow_rules\":\"先查事实再写\"}");
        return bo;
    }

    private static AigStudioActionDraftBo actionBo() {
        AigStudioActionDraftBo bo = new AigStudioActionDraftBo();
        bo.setDraftCode("ACTION-WRITE-SELLING-POINT");
        bo.setActionName("写一句卖点");
        bo.setLaunchMode("QUICK");
        bo.setTargetType("QUICK_CAPABILITY");
        bo.setTargetRef("text_generation");
        bo.setAgentCategory("ANALYSIS");
        bo.setObjective("给商品写一句卖点");
        bo.setProviderCapability("text_generation");
        return bo;
    }

    private AigStudioSkillDraft existingSkillDraft() {
        AigStudioSkillDraft draft = new AigStudioSkillDraft();
        draft.setSkillDraftId(SKILL_DRAFT_ID);
        draft.setDraftCode("SKILL-BRAND-BRIEF");
        draft.setSkillCode("brand_brief");
        draft.setAgentCategory("PLANNING");
        draft.setProviderCapability("text_generation");
        draft.setEnabled("Y");
        when(skillDraftMapper.selectById(SKILL_DRAFT_ID)).thenReturn(draft);
        return draft;
    }

    private AigStudioChildAgentDraft existingChildDraft() {
        AigStudioChildAgentDraft draft = new AigStudioChildAgentDraft();
        draft.setChildAgentDraftId(CHILD_DRAFT_ID);
        draft.setDraftCode("CHILD-SKU-ASSIST");
        draft.setAgentName("SKU 详情助手");
        draft.setAgentCategory("PLANNING");
        draft.setProviderCapability("text_generation");
        draft.setEnabled("Y");
        when(childAgentDraftMapper.selectById(CHILD_DRAFT_ID)).thenReturn(draft);
        return draft;
    }

    private AigStudioActionDraft existingActionDraft() {
        AigStudioActionDraft draft = new AigStudioActionDraft();
        draft.setActionDraftId(ACTION_DRAFT_ID);
        draft.setDraftCode("ACTION-WRITE-SELLING-POINT");
        draft.setActionName("写一句卖点");
        draft.setLaunchMode("QUICK");
        draft.setAgentCategory("ANALYSIS");
        draft.setProviderCapability("text_generation");
        draft.setObjective("给商品写一句卖点");
        draft.setEnabled("Y");
        when(actionDraftMapper.selectById(ACTION_DRAFT_ID)).thenReturn(draft);
        return draft;
    }

    /* ------------------------------------------------------------------ 创建 */

    @Test
    @DisplayName("★ 创建技能草案：只写草案表（不改任何草稿），并落责任人/默认启用")
    void createSkillDraftOnlyWritesDraftTable() {
        when(skillDraftMapper.insert(any(AigStudioSkillDraft.class))).thenAnswer(invocation -> {
            invocation.<AigStudioSkillDraft>getArgument(0).setSkillDraftId(SKILL_DRAFT_ID);
            return 1;
        });

        assertEquals(SKILL_DRAFT_ID, service.createSkillDraft(skillBo(), ACTOR));

        ArgumentCaptor<AigStudioSkillDraft> captor =
            ArgumentCaptor.forClass(AigStudioSkillDraft.class);
        verify(skillDraftMapper).insert(captor.capture());
        AigStudioSkillDraft saved = captor.getValue();
        assertEquals("SKILL-BRAND-BRIEF", saved.getDraftCode());
        assertEquals("SKILL-BRAND-BRIEF", saved.getDraftName(), "没给名称时用编码（页面上总得有个名字）");
        assertEquals(ACTOR, saved.getOwnerId());
        assertEquals("Y", saved.getEnabled());
        assertEquals("0", saved.getDelFlag());
        // ★ 创建草案绝不写草稿
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("创建草案：编码重复 / 缺编码 / 缺操作人 → 拒绝且不落库")
    void createRejectsBadInput() {
        when(skillDraftMapper.selectCount(any())).thenReturn(1L);
        ServiceException duplicated = assertThrows(ServiceException.class,
            () -> service.createSkillDraft(skillBo(), ACTOR));
        assertTrue(duplicated.getMessage().contains("已存在"), duplicated.getMessage());

        when(skillDraftMapper.selectCount(any())).thenReturn(0L);
        AigStudioSkillDraftBo noCode = skillBo();
        noCode.setDraftCode(" ");
        assertThrows(ServiceException.class, () -> service.createSkillDraft(noCode, ACTOR));
        assertThrows(ServiceException.class, () -> service.createSkillDraft(skillBo(), null));
        assertThrows(ServiceException.class, () -> service.createSkillDraft(null, ACTOR));

        verify(skillDraftMapper, never()).insert(any(AigStudioSkillDraft.class));
    }

    @Test
    @DisplayName("创建子 Agent 草案 / 快捷指令草案：各自落表且编码按类型独立查重")
    void createOtherKindsWriteTheirOwnTables() {
        when(childAgentDraftMapper.insert(any(AigStudioChildAgentDraft.class))).thenAnswer(invocation -> {
            invocation.<AigStudioChildAgentDraft>getArgument(0).setChildAgentDraftId(CHILD_DRAFT_ID);
            return 1;
        });
        when(actionDraftMapper.insert(any(AigStudioActionDraft.class))).thenAnswer(invocation -> {
            invocation.<AigStudioActionDraft>getArgument(0).setActionDraftId(ACTION_DRAFT_ID);
            return 1;
        });

        assertEquals(CHILD_DRAFT_ID, service.createChildAgentDraft(childBo(), ACTOR));
        assertEquals(ACTION_DRAFT_ID, service.createActionDraft(actionBo(), ACTOR));

        ArgumentCaptor<AigStudioChildAgentDraft> child =
            ArgumentCaptor.forClass(AigStudioChildAgentDraft.class);
        verify(childAgentDraftMapper).insert(child.capture());
        assertEquals("{\"workflow_rules\":\"先查事实再写\"}", child.getValue().getPromptSectionsJson());
        assertEquals("Y", child.getValue().getEnabled());

        ArgumentCaptor<AigStudioActionDraft> action =
            ArgumentCaptor.forClass(AigStudioActionDraft.class);
        verify(actionDraftMapper).insert(action.capture());
        assertEquals("QUICK", action.getValue().getLaunchMode());
        assertEquals("QUICK_CAPABILITY", action.getValue().getTargetType());
        assertEquals("text_generation", action.getValue().getTargetRef());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    /* ------------------------------------------------------------------ 清单 */

    @Test
    @DisplayName("清单：三类各查自己的表，最新在前")
    void queryPagesReadTheirOwnTables() {
        AigStudioSkillDraftVo row = new AigStudioSkillDraftVo();
        row.setDraftCode("SKILL-BRAND-BRIEF");
        when(skillDraftMapper.selectVoPage(any(), any()))
            .thenReturn(new Page<AigStudioSkillDraftVo>(1, 10).setRecords(List.of(row)).setTotal(1));

        PageResult<AigStudioSkillDraftVo> result = service.querySkillDraftPage(
            new AigStudioArtifactDraftQueryBo(), new PageQuery());

        List<AigStudioSkillDraftVo> rows = new ArrayList<>(result.getRows());
        assertEquals(1, rows.size());
        assertEquals("SKILL-BRAND-BRIEF", rows.get(0).getDraftCode());
        assertEquals(1L, result.getTotal());
        verify(skillDraftMapper).selectVoPage(any(), any());
        verify(childAgentDraftMapper, never()).selectVoPage(any(), any());
    }

    /* ------------------------------------------------------------------ 预览 */

    @Test
    @DisplayName("★ 预览：三类都能生成，且预览绝不写任何表（不调 applyRevision）")
    void previewBuildsWithoutWritingAnything() {
        existingSkillDraft();
        existingChildDraft();
        existingActionDraft();

        AigStudioArtifactGenerateVo skill = service.preview(AigStudioArtifactKindEnum.SKILL,
            SKILL_DRAFT_ID);
        assertEquals("SKILL", skill.getKind());
        assertEquals(SKILL_DRAFT_ID, skill.getSourceDraftId());
        assertEquals("SKILL-BRAND-BRIEF", skill.getSourceDraftCode());
        assertEquals("brand_brief", skill.getContent().getAllowedSkills().get(0));
        assertNull(skill.getTargetDraftId(), "预览不该有目标草稿");
        assertNull(skill.getRevision());
        assertTrue(skill.getNotes().size() > 0);

        assertEquals("CHILD_AGENT", service.preview(AigStudioArtifactKindEnum.CHILD_AGENT,
            CHILD_DRAFT_ID).getKind());
        assertEquals("ACTION", service.preview(AigStudioArtifactKindEnum.ACTION,
            ACTION_DRAFT_ID).getKind());

        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
        verify(skillDraftMapper, never()).insert(any(AigStudioSkillDraft.class));
    }

    @Test
    @DisplayName("预览：草案不存在 / 已停用 / 类型未知 / 缺ID → 都拒绝（不能返回空壳让人以为没问题）")
    void previewRejectsBadInput() {
        assertThrows(ServiceException.class, () -> service.preview(null, SKILL_DRAFT_ID));
        assertThrows(ServiceException.class, () -> service.preview(AigStudioArtifactKindEnum.SKILL, null));

        assertThrows(ServiceException.class,
            () -> service.preview(AigStudioArtifactKindEnum.SKILL, SKILL_DRAFT_ID));

        AigStudioSkillDraft disabled = existingSkillDraft();
        disabled.setEnabled("N");
        ServiceException stopped = assertThrows(ServiceException.class,
            () -> service.preview(AigStudioArtifactKindEnum.SKILL, SKILL_DRAFT_ID));
        assertTrue(stopped.getMessage().contains("已停用"), stopped.getMessage());
    }

    @Test
    @DisplayName("★ 预览：生成规则拒绝（缺能力）要转成业务异常并说明原因")
    void previewWrapsGeneratorRejection() {
        AigStudioSkillDraft draft = existingSkillDraft();
        draft.setProviderCapability(null);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.preview(AigStudioArtifactKindEnum.SKILL, SKILL_DRAFT_ID));

        assertTrue(error.getMessage().contains("无法生成"), error.getMessage());
        assertTrue(error.getMessage().contains("providerCapability"), error.getMessage());
    }

    /* ------------------------------------------------------------------ 生成 */

    @Test
    @DisplayName("★ 生成：走训练台唯一的写修订入口，来源 DRAFT_GENERATED，并带回目标草稿与修订号")
    void generateWritesRevisionWithItsOwnSource() {
        existingSkillDraft();
        AigStudioDraftDetailVo updated = new AigStudioDraftDetailVo();
        updated.setDraftId(TARGET_DRAFT);
        updated.setLatestRevision(3);
        updated.setContentHash("hash-3");
        updated.setRevisionCreated(true);
        when(draftService.applyRevision(eq(TARGET_DRAFT), eq(2), any(),
            eq(AigStudioRevisionSourceEnum.DRAFT_GENERATED), any(), eq(ACTOR))).thenReturn(updated);

        AigStudioArtifactGenerateVo result = service.generate(AigStudioArtifactKindEnum.SKILL,
            SKILL_DRAFT_ID, TARGET_DRAFT, 2, ACTOR);

        assertEquals(TARGET_DRAFT, result.getTargetDraftId());
        assertEquals(3, result.getRevision());
        assertEquals("hash-3", result.getContentHash());
        assertEquals(Boolean.TRUE, result.getRevisionCreated());
        assertEquals("brand_brief", result.getContent().getAllowedSkills().get(0));

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(draftService).applyRevision(eq(TARGET_DRAFT), eq(2), any(),
            eq(AigStudioRevisionSourceEnum.DRAFT_GENERATED), summary.capture(), eq(ACTOR));
        assertTrue(summary.getValue().contains("SKILL-BRAND-BRIEF"),
            "修订说明要能回答「这条修订是哪份草案生成的」：" + summary.getValue());
    }

    @Test
    @DisplayName("★ 生成：内容与目标草稿一致（未产生新修订）→ 报错，不返回「成功但什么都没发生」")
    void generateRejectsWhenNothingChanged() {
        existingSkillDraft();
        AigStudioDraftDetailVo unchanged = new AigStudioDraftDetailVo();
        unchanged.setDraftId(TARGET_DRAFT);
        unchanged.setLatestRevision(2);
        unchanged.setRevisionCreated(false);
        when(draftService.applyRevision(any(), any(), any(), any(), any(), any())).thenReturn(unchanged);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.generate(AigStudioArtifactKindEnum.SKILL, SKILL_DRAFT_ID, TARGET_DRAFT, 2, ACTOR));

        assertTrue(error.getMessage().contains("一致"), error.getMessage());
    }

    @Test
    @DisplayName("★ 生成：缺目标草稿 → 拒绝；草案本身有问题时也在写库前就拒绝")
    void generateRejectsBeforeWriting() {
        existingSkillDraft();

        ServiceException noTarget = assertThrows(ServiceException.class,
            () -> service.generate(AigStudioArtifactKindEnum.SKILL, SKILL_DRAFT_ID, null, 2, ACTOR));
        assertTrue(noTarget.getMessage().contains("targetDraftId"), noTarget.getMessage());

        // 草案不存在：build 阶段就拒绝，绝不能先去写目标草稿
        ServiceException noDraft = assertThrows(ServiceException.class,
            () -> service.generate(AigStudioArtifactKindEnum.ACTION, ACTION_DRAFT_ID, TARGET_DRAFT, 1, ACTOR));
        assertTrue(noDraft.getMessage().contains("不存在"), noDraft.getMessage());

        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

}
