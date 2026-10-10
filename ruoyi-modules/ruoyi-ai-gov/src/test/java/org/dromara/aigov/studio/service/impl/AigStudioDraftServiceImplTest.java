package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 训练草稿读写测试（增量 S2）。
 *
 * <p><b>这条测试守的是一段"出错不会报错"的逻辑</b>：草稿的 CAS、修订号推进、
 * 内容未变不产生新修订、回滚不改历史——它们失效时的表现全是"看起来正常"：
 * 要么多出几条什么都没改的历史，要么别人改的内容被静默覆盖，要么回滚把历史改掉了。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioDraftServiceImplTest {

    private static final long DRAFT_ID = 9001L;
    private static final long OWNER = 200L;
    private static final long OTHER = 201L;

    private AigStudioDraftMapper draftMapper;
    private AigStudioRevisionMapper revisionMapper;
    private AigStudioDraftServiceImpl service;

    /**
     * 内存里的"草稿行"（长度 1）
     */
    private AigStudioDraft[] row;

    /**
     * 内存里的修订表
     */
    private List<AigStudioRevision> revisions;

    @BeforeAll
    static void initTableInfo() {
        // 服务用 LambdaQueryWrapper/LambdaUpdateWrapper，需要实体→列的 lambda 缓存；纯单测没有 Spring
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioDraft.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioRevision.class);
    }

    @BeforeEach
    void setUp() {
        draftMapper = mock(AigStudioDraftMapper.class);
        revisionMapper = mock(AigStudioRevisionMapper.class);
        service = new AigStudioDraftServiceImpl(draftMapper, revisionMapper, JsonMapper.builder().build());
        row = new AigStudioDraft[1];
        revisions = new ArrayList<>();

        when(draftMapper.insert(any(AigStudioDraft.class))).thenAnswer(invocation -> {
            AigStudioDraft saved = invocation.getArgument(0);
            // 真实 MP 在 insert 时分配主键（雪花），mock 不会
            saved.setDraftId(DRAFT_ID);
            row[0] = saved;
            return 1;
        });
        when(revisionMapper.insert(any(AigStudioRevision.class))).thenAnswer(invocation -> {
            AigStudioRevision saved = invocation.getArgument(0);
            saved.setRevisionId(7000L + revisions.size());
            revisions.add(saved);
            return 1;
        });
        when(draftMapper.selectById(any())).thenAnswer(invocation -> row[0]);
        // 模拟行的写回（CAS 的"命中/未命中"由各用例的返回值决定）
        when(draftMapper.update(any(AigStudioDraft.class), any())).thenAnswer(invocation -> {
            AigStudioDraft update = invocation.getArgument(0);
            if (row[0] == null) {
                return 0;
            }
            if (update.getContentJson() != null) {
                row[0].setContentJson(update.getContentJson());
            }
            if (update.getContentHash() != null) {
                row[0].setContentHash(update.getContentHash());
            }
            if (update.getLatestRevision() != null) {
                row[0].setLatestRevision(update.getLatestRevision());
            }
            if (update.getStatus() != null) {
                row[0].setStatus(update.getStatus());
            }
            return 1;
        });
    }

    private static String content(String objective) {
        return "{\"agentName\":\"详情页文案助手\",\"promptSections\":{\"role\":\"策划\",\"objective\":\""
            + objective + "\"}}";
    }

    private AigStudioDraft existingDraft(int revision, String contentJson, String status) {
        AigStudioDraft draft = new AigStudioDraft();
        draft.setDraftId(DRAFT_ID);
        draft.setAgentCode("DETAIL_COPYWRITER");
        draft.setOwnerId(OWNER);
        draft.setLatestRevision(revision);
        draft.setContentJson(AigStudioContentHasher.canonicalize(contentJson));
        draft.setContentHash(AigStudioContentHasher.hash(contentJson));
        draft.setStatus(status);
        row[0] = draft;
        return draft;
    }

    private AigStudioDraftSaveBo saveBo(int expectedRevision, String contentJson) {
        AigStudioDraftSaveBo bo = new AigStudioDraftSaveBo();
        bo.setDraftId(DRAFT_ID);
        bo.setExpectedRevision(expectedRevision);
        bo.setContentJson(contentJson);
        bo.setSummary("改了目标");
        return bo;
    }

    // ------------------------------------------------------------------ 创建

    @Test
    @DisplayName("★ 创建：同时产生第 1 个修订，内容按规范化存储，责任人=操作用户")
    void createWritesDraftAndFirstRevision() {
        AigStudioDraftCreateBo bo = new AigStudioDraftCreateBo();
        bo.setAgentCode("DETAIL_COPYWRITER");
        bo.setOrgId(100L);
        bo.setContentJson(content("出脚本"));

        Long id = service.createDraft(bo, OWNER);

        assertEquals(DRAFT_ID, id);
        assertEquals(OWNER, row[0].getOwnerId());
        assertEquals("DETAIL_COPYWRITER", row[0].getAgentCode());
        assertEquals(1, row[0].getLatestRevision());
        assertEquals(AigStudioDraftStatusEnum.EDITING.getCode(), row[0].getStatus());
        assertNull(row[0].getLastPublishedHash(), "新草稿从未提交过");
        // 入库的必须是规范化内容，且哈希可复算
        assertEquals(AigStudioContentHasher.canonicalize(content("出脚本")), row[0].getContentJson());
        assertEquals(AigStudioContentHasher.hash(row[0].getContentJson()), row[0].getContentHash());
        assertEquals(1, revisions.size());
        assertEquals(1, revisions.get(0).getRevisionNo());
        assertEquals(AigStudioRevisionSourceEnum.MANUAL.getCode(), revisions.get(0).getSource());
        assertEquals(OWNER, revisions.get(0).getAuthorId());
    }

    @Test
    @DisplayName("内容为空 → 填标准骨架：八个 Prompt 分节都在（页面据此渲染待填写）")
    void createFillsSkeletonWhenContentBlank() {
        AigStudioDraftCreateBo bo = new AigStudioDraftCreateBo();
        bo.setAgentCode("INDUSTRY_ANALYST");

        service.createDraft(bo, OWNER);

        String stored = row[0].getContentJson();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            assertTrue(stored.contains("\"" + key + "\""), "骨架缺少分节 " + key + "：" + stored);
        }
        assertTrue(stored.contains("\"allowExternal\":\"N\""), "骨架默认不外发：" + stored);
    }

    @Test
    @DisplayName("创建：缺编码/缺操作人/内容不是合法 JSON 都要报错，且不落库")
    void createRejectsBadInput() {
        AigStudioDraftCreateBo noCode = new AigStudioDraftCreateBo();
        assertThrows(ServiceException.class, () -> service.createDraft(noCode, OWNER));

        AigStudioDraftCreateBo ok = new AigStudioDraftCreateBo();
        ok.setAgentCode("X");
        assertThrows(ServiceException.class, () -> service.createDraft(ok, null), "没有操作人就无法定责任人");

        AigStudioDraftCreateBo badJson = new AigStudioDraftCreateBo();
        badJson.setAgentCode("X");
        badJson.setContentJson("{not json");
        assertThrows(ServiceException.class, () -> service.createDraft(badJson, OWNER));

        verify(draftMapper, never()).insert(any(AigStudioDraft.class));
    }

    // ------------------------------------------------------------------ 保存

    @Test
    @DisplayName("★ 保存：期望修订号与库中不一致 → 报错且一个字都不写（不静默覆盖）")
    void saveRejectsStaleRevision() {
        existingDraft(3, content("旧"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveDraft(saveBo(2, content("新")), OWNER));

        assertTrue(ex.getMessage().contains("你手上的修订号=2"), ex.getMessage());
        assertTrue(ex.getMessage().contains("库中当前=3"), "消息必须给出两个版本，否则用户不知道怎么办：" + ex.getMessage());
        verify(draftMapper, never()).update(any(AigStudioDraft.class), any());
        verify(revisionMapper, never()).insert(any(AigStudioRevision.class));
    }

    @Test
    @DisplayName("★ 保存：内容没变（只是键顺序/空白不同）→ 不产生新修订、不动修订号")
    void saveWithUnchangedContentCreatesNoRevision() {
        existingDraft(2, content("出脚本"), AigStudioDraftStatusEnum.EDITING.getCode());
        // 同一内容、不同键顺序与空白
        String reordered = "{ \"promptSections\" : { \"objective\" : \"出脚本\" , \"role\" : \"策划\" } ,"
            + " \"agentName\" : \"详情页文案助手\" }";

        AigStudioDraftDetailVo vo = service.saveDraft(saveBo(2, reordered), OWNER);

        assertFalse(vo.getRevisionCreated(), "内容没变就不该产生新修订（否则历史会被噪声淹掉）");
        assertEquals(2, row[0].getLatestRevision());
        verify(draftMapper, never()).update(any(AigStudioDraft.class), any());
        verify(revisionMapper, never()).insert(any(AigStudioRevision.class));
    }

    @Test
    @DisplayName("保存：内容变了 → 修订号 +1、写新修订、用 CAS 更新草稿行")
    void saveWithChangedContentCreatesNextRevision() {
        existingDraft(1, content("出脚本"), AigStudioDraftStatusEnum.EDITING.getCode());

        AigStudioDraftDetailVo vo = service.saveDraft(saveBo(1, content("出分镜")), OWNER);

        assertTrue(vo.getRevisionCreated());
        assertEquals(2, vo.getLatestRevision());
        assertEquals(AigStudioContentHasher.hash(content("出分镜")), vo.getContentHash());
        assertEquals(1, revisions.size());
        assertEquals(2, revisions.get(0).getRevisionNo());
        assertEquals("改了目标", revisions.get(0).getSummary());
        ArgumentCaptor<AigStudioDraft> captor = ArgumentCaptor.forClass(AigStudioDraft.class);
        verify(draftMapper).update(captor.capture(), any());
        assertEquals(2, captor.getValue().getLatestRevision());
        assertNotNull(captor.getValue().getContentHash());
    }

    @Test
    @DisplayName("★ 保存：读之后、写之前被人改过（CAS 0 行）→ 报冲突，不覆盖也不写修订")
    void saveReportsDbLevelConflict() {
        existingDraft(1, content("出脚本"), AigStudioDraftStatusEnum.EDITING.getCode());
        when(draftMapper.update(any(AigStudioDraft.class), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveDraft(saveBo(1, content("出分镜")), OWNER));

        assertTrue(ex.getMessage().contains("已被他人修改"), ex.getMessage());
        verify(revisionMapper, never()).insert(any(AigStudioRevision.class));
    }

    @Test
    @DisplayName("保存：非责任人不能改（草稿有明确责任人）")
    void saveRejectsNonOwner() {
        existingDraft(1, content("出脚本"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveDraft(saveBo(1, content("出分镜")), OTHER));

        assertTrue(ex.getMessage().contains("责任人"), ex.getMessage());
        verify(draftMapper, never()).update(any(AigStudioDraft.class), any());
    }

    @Test
    @DisplayName("保存：已归档的草稿不能再改（要再训练请新建）")
    void saveRejectsArchived() {
        existingDraft(1, content("出脚本"), AigStudioDraftStatusEnum.ARCHIVED.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.saveDraft(saveBo(1, content("出分镜")), OWNER));

        assertTrue(ex.getMessage().contains("已归档"), ex.getMessage());
    }

    @Test
    @DisplayName("保存：缺 expectedRevision 直接拒绝（无版本的编辑等于允许静默覆盖）")
    void saveRejectsMissingExpectedRevision() {
        existingDraft(1, content("出脚本"), AigStudioDraftStatusEnum.EDITING.getCode());
        AigStudioDraftSaveBo bo = saveBo(1, content("出分镜"));
        bo.setExpectedRevision(null);

        assertThrows(ServiceException.class, () -> service.saveDraft(bo, OWNER));
    }

    // ------------------------------------------------------------------ 回滚

    @Test
    @DisplayName("★ 回滚：不修改历史，而是产生一条来源为 ROLLBACK 的新修订")
    void rollbackCreatesNewRevisionAndKeepsHistory() {
        existingDraft(3, content("当前内容"), AigStudioDraftStatusEnum.EDITING.getCode());
        // 历史里有修订 1（内容 A）
        AigStudioRevision old = new AigStudioRevision();
        old.setRevisionId(7001L);
        old.setDraftId(DRAFT_ID);
        old.setRevisionNo(1);
        old.setContentSnapshotJson(AigStudioContentHasher.canonicalize(content("第一版")));
        old.setContentHash(AigStudioContentHasher.hash(content("第一版")));
        when(revisionMapper.selectOne(any())).thenReturn(old);

        AigStudioDraftDetailVo vo = service.rollback(DRAFT_ID, 1, 3, OWNER);

        assertTrue(vo.getRevisionCreated());
        assertEquals(4, vo.getLatestRevision());
        assertEquals(AigStudioContentHasher.hash(content("第一版")), vo.getContentHash());
        assertEquals(1, revisions.size(), "只新增一条");
        AigStudioRevision created = revisions.get(0);
        assertEquals(4, created.getRevisionNo());
        assertEquals(AigStudioRevisionSourceEnum.ROLLBACK.getCode(), created.getSource(),
            "必须标成回滚来源，否则看不出这条修订是怎么来的");
        assertEquals(AigStudioContentHasher.canonicalize(content("第一版")), created.getContentSnapshotJson());
        assertTrue(created.getSummary().contains("#1"), created.getSummary());
    }

    @Test
    @DisplayName("回滚：目标就是当前内容 → 不产生噪声修订")
    void rollbackToCurrentContentIsNoop() {
        existingDraft(2, content("同一版"), AigStudioDraftStatusEnum.EDITING.getCode());
        AigStudioRevision same = new AigStudioRevision();
        same.setRevisionNo(1);
        same.setDraftId(DRAFT_ID);
        same.setContentHash(AigStudioContentHasher.hash(content("同一版")));
        same.setContentSnapshotJson(AigStudioContentHasher.canonicalize(content("同一版")));
        when(revisionMapper.selectOne(any())).thenReturn(same);

        AigStudioDraftDetailVo vo = service.rollback(DRAFT_ID, 1, 2, OWNER);

        assertFalse(vo.getRevisionCreated());
        verify(revisionMapper, never()).insert(any(AigStudioRevision.class));
    }

    @Test
    @DisplayName("回滚：目标修订不存在 → 报错（不能当成回滚成功）")
    void rollbackRejectsUnknownTarget() {
        existingDraft(2, content("当前"), AigStudioDraftStatusEnum.EDITING.getCode());
        when(revisionMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.rollback(DRAFT_ID, 99, 2, OWNER));

        assertTrue(ex.getMessage().contains("目标修订不存在"), ex.getMessage());
    }

    // ------------------------------------------------------------------ 读取与归档

    @Test
    @DisplayName("详情：从未提交 → 有未发布改动；提交过且哈希相同 → 没有未发布改动")
    void getDraftComputesUnpublishedChanges() {
        AigStudioDraft draft = existingDraft(1, content("X"), AigStudioDraftStatusEnum.EDITING.getCode());
        assertTrue(service.getDraft(DRAFT_ID).getUnpublishedChanges(), "从未提交过，当然是未发布");

        draft.setLastPublishedHash(draft.getContentHash());
        assertFalse(service.getDraft(DRAFT_ID).getUnpublishedChanges());

        draft.setLastPublishedHash(AigStudioContentHasher.hash(content("别的")));
        assertTrue(service.getDraft(DRAFT_ID).getUnpublishedChanges(), "提交后又改了，就该再报未发布");
    }

    @Test
    @DisplayName("详情：状态中文描述由服务端填（避免每个页面各写一套映射）")
    void getDraftFillsStatusLabel() {
        existingDraft(1, content("X"), AigStudioDraftStatusEnum.SUBMITTED.getCode());
        assertEquals(AigStudioDraftStatusEnum.SUBMITTED.getDesc(), service.getDraft(DRAFT_ID).getStatusLabel());
    }

    @Test
    @DisplayName("修订列表：按修订号倒序查询（最新在前）")
    void listRevisionsQueriesDescending() {
        existingDraft(2, content("X"), AigStudioDraftStatusEnum.EDITING.getCode());
        when(revisionMapper.selectVoList(any())).thenReturn(List.of());

        service.listRevisions(DRAFT_ID);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AigStudioRevision>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(revisionMapper).selectVoList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("revision_no DESC"),
            "版本记录要最新的在前：" + captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("读取修订：不存在要报错，不能返回 null 让上层去猜")
    void getRevisionRejectsUnknown() {
        when(revisionMapper.selectVoById(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.getRevision(1L));
    }

    @Test
    @DisplayName("归档：设终态；重复归档要报错（不能当成功）")
    void archiveMarksTerminal() {
        existingDraft(1, content("X"), AigStudioDraftStatusEnum.EDITING.getCode());

        service.archive(DRAFT_ID, OWNER);

        assertEquals(AigStudioDraftStatusEnum.ARCHIVED.getCode(), row[0].getStatus());
        assertThrows(ServiceException.class, () -> service.archive(DRAFT_ID, OWNER),
            "已经归档了就不该再当成功一次");
    }

    @Test
    @DisplayName("归档：非责任人不能归档")
    void archiveRejectsNonOwner() {
        existingDraft(1, content("X"), AigStudioDraftStatusEnum.EDITING.getCode());
        assertThrows(ServiceException.class, () -> service.archive(DRAFT_ID, OTHER));
        verify(draftMapper, times(0)).update(any(AigStudioDraft.class), any());
    }

}
