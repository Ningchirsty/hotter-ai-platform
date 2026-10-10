package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.enums.AigReleaseChannelEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.studio.domain.AigStudioDraft;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftQueryBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSaveBo;
import org.dromara.aigov.studio.domain.bo.AigStudioDraftSubmitBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftSubmitVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftVo;
import org.dromara.aigov.studio.domain.vo.AigStudioValidateVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.common.core.domain.PageResult;
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
    private static final long AGENT_ID = 300L;

    private AigStudioDraftMapper draftMapper;
    private AigStudioRevisionMapper revisionMapper;
    private AigAgentMapper agentMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigStudioDraftServiceImpl service;

    /**
     * 内存里的"草稿行"（长度 1）
     */
    private AigStudioDraft[] row;

    /**
     * 内存里的修订表
     */
    private List<AigStudioRevision> revisions;

    /**
     * 内存里的 Agent 版本表（submit 用）
     */
    private List<AigAgentVersion> createdVersions;

    @BeforeAll
    static void initTableInfo() {
        // 服务用 LambdaQueryWrapper/LambdaUpdateWrapper，需要实体→列的 lambda 缓存；纯单测没有 Spring
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioDraft.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioRevision.class);
        TableInfoHelper.initTableInfo(assistant, AigAgent.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
    }

    @BeforeEach
    void setUp() {
        draftMapper = mock(AigStudioDraftMapper.class);
        revisionMapper = mock(AigStudioRevisionMapper.class);
        agentMapper = mock(AigAgentMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        service = new AigStudioDraftServiceImpl(draftMapper, revisionMapper, agentMapper,
            agentVersionMapper, JsonMapper.builder().build());
        row = new AigStudioDraft[1];
        revisions = new ArrayList<>();
        createdVersions = new ArrayList<>();

        when(draftMapper.insert(any(AigStudioDraft.class))).thenAnswer(invocation -> {
            AigStudioDraft saved = invocation.getArgument(0);
            // 真实 MP 在 insert 时分配主键（雪花），mock 不会
            saved.setDraftId(DRAFT_ID);
            row[0] = saved;
            return 1;
        });
        when(agentMapper.insert(any(AigAgent.class))).thenAnswer(invocation -> {
            invocation.<AigAgent>getArgument(0).setAgentId(AGENT_ID);
            return 1;
        });
        when(agentVersionMapper.insert(any(AigAgentVersion.class))).thenAnswer(invocation -> {
            AigAgentVersion saved = invocation.getArgument(0);
            saved.setAgentVersionId(5100L + createdVersions.size());
            createdVersions.add(saved);
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
            if (update.getAgentId() != null) {
                row[0].setAgentId(update.getAgentId());
            }
            if (update.getAgentVersionId() != null) {
                row[0].setAgentVersionId(update.getAgentVersionId());
            }
            if (update.getLastPublishedHash() != null) {
                row[0].setLastPublishedHash(update.getLastPublishedHash());
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

    // ------------------------------------------------------------------ 预检（S3）

    /**
     * 一份"各方面都填全"的内容（八分节 + 能力 + 合法 Schema）。
     *
     * @return JSON
     */
    private static String fullContent() {
        StringBuilder sections = new StringBuilder();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            if (sections.length() > 0) {
                sections.append(',');
            }
            sections.append('"').append(key).append("\":\"内容-").append(key).append('"');
        }
        return "{\"agentName\":\"详情页文案助手\",\"promptSections\":{" + sections + "},"
            + "\"providerCapability\":\"text_generation\",\"allowExternal\":\"N\","
            + "\"inputSchema\":\"{\\\"type\\\":\\\"object\\\"}\","
            + "\"outputSchema\":\"{\\\"type\\\":\\\"object\\\"}\"}";
    }

    @Test
    @DisplayName("★ 预检：填全的内容通过，且结论钉住「检的是哪一版」（revision + contentHash）")
    void validatePassesOnCompleteContent() {
        AigStudioDraft draft = existingDraft(3, fullContent(), AigStudioDraftStatusEnum.EDITING.getCode());

        AigStudioValidateVo vo = service.validateDraft(DRAFT_ID);

        assertTrue(vo.getPassed(), "问题=" + vo.getProblems());
        assertTrue(vo.getProblems().isEmpty());
        assertEquals(3, vo.getRevision());
        assertEquals(draft.getContentHash(), vo.getContentHash(), "结论必须能对应到具体那一版内容");
    }

    @Test
    @DisplayName("★ 预检：问题一次列全（不是抛第一个错让人来回试）")
    void validateListsAllProblemsAtOnce() {
        // 只给两个分节且都为空、没有能力、allowExternal 非法、Schema 不合法、页面定制夹带脚本
        String bad = "{\"promptSections\":{\"role\":\"  \",\"objective\":\"  \"},"
            + "\"allowExternal\":\"MAYBE\",\"inputSchema\":\"{bad\","
            + "\"pageCustomizationJson\":\"<script>alert(1)</script>\"}";
        existingDraft(1, bad, AigStudioDraftStatusEnum.EDITING.getCode());

        AigStudioValidateVo vo = service.validateDraft(DRAFT_ID);

        assertFalse(vo.getPassed());
        String joined = String.join("\n", vo.getProblems());
        assertTrue(joined.contains("缺少 Prompt 分节"), joined);
        assertTrue(joined.contains("全部为空"), joined);
        assertTrue(joined.contains("providerCapability"), joined);
        assertTrue(joined.contains("allowExternal"), joined);
        assertTrue(joined.contains("输入 Schema"), joined);
        assertTrue(joined.contains("可执行脚本"), joined);
        assertTrue(vo.getProblems().size() >= 6, "应一次列全，实际=" + vo.getProblems());
    }

    @Test
    @DisplayName("预检：不产生任何写入（它只是只读校验）")
    void validateWritesNothing() {
        existingDraft(2, fullContent(), AigStudioDraftStatusEnum.EDITING.getCode());

        service.validateDraft(DRAFT_ID);

        verify(draftMapper, never()).update(any(AigStudioDraft.class), any());
        verify(revisionMapper, never()).insert(any(AigStudioRevision.class));
    }

    @Test
    @DisplayName("预检：内容不是合法 JSON 时如实报错，不抛异常")
    void validateReportsBrokenJson() {
        AigStudioDraft draft = existingDraft(1, fullContent(), AigStudioDraftStatusEnum.EDITING.getCode());
        draft.setContentJson("{not json");

        AigStudioValidateVo vo = service.validateDraft(DRAFT_ID);

        assertFalse(vo.getPassed());
        assertTrue(vo.getProblems().get(0).contains("不是合法 JSON"), vo.getProblems().toString());
    }

    // ------------------------------------------------------------------ 清单（S3）

    @Test
    @DisplayName("★ 清单：状态描述与「未提交改动」由服务层在分页映射时填（不填就是永远为空的两列）")
    void queryPageFillsLabelsAndDirtyFlag() {
        AigStudioDraftVo vo = new AigStudioDraftVo();
        vo.setDraftId(DRAFT_ID);
        vo.setStatus(AigStudioDraftStatusEnum.EDITING.getCode());
        vo.setContentHash("hash-new");
        vo.setLastPublishedHash(null);
        when(draftMapper.selectVoPage(any(), any())).thenReturn(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<AigStudioDraftVo>(1, 10)
                .setRecords(java.util.List.of(vo)).setTotal(1));

        PageResult<AigStudioDraftVo> result = service.queryPage(new AigStudioDraftQueryBo(),
            new org.dromara.common.mybatis.core.page.PageQuery());

        List<AigStudioDraftVo> rows = new ArrayList<>(result.getRows());
        assertEquals(1, rows.size());
        assertEquals(AigStudioDraftStatusEnum.EDITING.getDesc(), rows.get(0).getStatusLabel());
        assertTrue(rows.get(0).getUnpublishedChanges(), "从未提交过 = 有未发布改动");
    }

    // ------------------------------------------------------------------ 提交（S3b）

    /**
     * 一份"可以提交"的内容：八分节 + 能力 + 类别 + 合法 Schema。
     *
     * @return JSON
     */
    private static String submittableContent() {
        StringBuilder sections = new StringBuilder();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            if (sections.length() > 0) {
                sections.append(',');
            }
            sections.append('"').append(key).append("\":\"内容-").append(key).append('"');
        }
        return "{\"agentName\":\"详情页文案助手\",\"agentCategory\":\"PLANNING\","
            + "\"roleDescription\":\"电商详情页文案策划\","
            + "\"promptSections\":{" + sections + "},"
            + "\"providerCapability\":\"text_generation\",\"allowExternal\":\"N\","
            + "\"allowedTools\":[\"brand_brief\"],\"forbiddenTools\":[],"
            + "\"inputSchema\":\"{\\\"type\\\":\\\"object\\\"}\","
            + "\"outputSchema\":\"{\\\"type\\\":\\\"object\\\"}\"}";
    }

    @Test
    @DisplayName("★ 提交（从零创建）：先建 Agent 定义，再产出 DRAFT 版本，并回写草稿")
    void submitFromScratchCreatesAgentAndDraftVersion() {
        AigStudioDraft draft = existingDraft(2, submittableContent(),
            AigStudioDraftStatusEnum.EDITING.getCode());
        when(agentMapper.selectOne(any())).thenReturn(null);
        when(agentVersionMapper.selectOne(any())).thenReturn(null);
        AigStudioDraftSubmitBo bo = new AigStudioDraftSubmitBo();

        AigStudioDraftSubmitVo vo = service.submitDraft(DRAFT_ID, bo, OWNER);

        assertTrue(vo.getAgentCreated(), "草稿未绑定 Agent，本次应新建");
        assertEquals(AGENT_ID, vo.getAgentId());
        assertEquals("0.1.2", vo.getVersion(), "未指定版本号时按 0.1.<修订号> 生成");
        assertEquals(AigReleaseStatusEnum.DRAFT.getCode(), vo.getReleaseStatus(),
            "训练台只产 DRAFT，发布推进归既有状态机");
        assertEquals(1, createdVersions.size());
        AigAgentVersion av = createdVersions.get(0);
        assertEquals(AGENT_ID, av.getAgentId());
        assertEquals(AigReleaseStatusEnum.DRAFT.getCode(), av.getReleaseStatus());
        assertEquals(AigReleaseChannelEnum.TESTING.getCode(), av.getReleaseChannel(),
            "显式给通道，不依赖 DDL 默认值");
        assertEquals("text_generation", av.getProviderCapability());
        assertEquals("N", av.getAllowExternal());
        assertEquals("brand_brief", av.getAllowedTools());
        assertTrue(av.getPromptTemplate().contains("## role"), "主 Prompt 由八分节拼成");
        assertEquals(draft.getContentJson(), av.getConfigJson(), "结构化留档=提交时那一版原文");

        assertEquals(AGENT_ID, row[0].getAgentId(), "新建的 Agent 要绑回草稿");
        assertEquals(5100L, row[0].getAgentVersionId());
        assertEquals(draft.getContentHash(), row[0].getLastPublishedHash(),
            "已提交哈希要对上，否则页面会一直显示「有未发布改动」");
        assertEquals(AigStudioDraftStatusEnum.SUBMITTED.getCode(), row[0].getStatus());
    }

    @Test
    @DisplayName("提交（已绑定 Agent）：复用该 Agent，不再新建定义")
    void submitReusesBoundAgent() {
        AigStudioDraft draft = existingDraft(1, submittableContent(),
            AigStudioDraftStatusEnum.EDITING.getCode());
        draft.setAgentId(AGENT_ID);
        when(agentVersionMapper.selectOne(any())).thenReturn(null);

        AigStudioDraftSubmitVo vo = service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER);

        assertFalse(vo.getAgentCreated());
        assertEquals(AGENT_ID, vo.getAgentId());
        assertEquals("0.1.1", vo.getVersion());
        verify(agentMapper, never()).insert(any(AigAgent.class));
    }

    @Test
    @DisplayName("★ 提交：预检不过就直接拒绝（不允许「先提交、后面再补」）")
    void submitRejectsContentFailingPreflight() {
        existingDraft(1, content("只有两节"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER));

        assertTrue(ex.getMessage().contains("未通过预检"), ex.getMessage());
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));
        verify(agentMapper, never()).insert(any(AigAgent.class));
    }

    @Test
    @DisplayName("★ 提交：未声明 Agent 类别 → 拒绝并说明原因（不替它猜一个绑定实现）")
    void submitRejectsMissingCategory() {
        // 内容合法（能过预检），但没有 agentCategory
        String noCategory = submittableContent().replace("\"agentCategory\":\"PLANNING\",", "");
        existingDraft(1, noCategory, AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER));

        assertTrue(ex.getMessage().contains("agentCategory"), ex.getMessage());
        assertTrue(ex.getMessage().contains("PLANNING"), "报错里要列出可用的类别：" + ex.getMessage());
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));
    }

    @Test
    @DisplayName("提交：版本号被占用 → 报错要求显式指定（不悄悄换一个号）")
    void submitRejectsTakenVersion() {
        AigStudioDraft draft = existingDraft(3, submittableContent(),
            AigStudioDraftStatusEnum.EDITING.getCode());
        draft.setAgentId(AGENT_ID);
        AigAgentVersion taken = new AigAgentVersion();
        taken.setVersion("0.1.3");
        when(agentVersionMapper.selectOne(any())).thenReturn(taken);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER));

        assertTrue(ex.getMessage().contains("已被占用"), ex.getMessage());
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));
    }

    @Test
    @DisplayName("★ 提交（从零创建）：编码已被别人占用 → 拒绝，不「顺手绑上去」")
    void submitRejectsExistingAgentCode() {
        existingDraft(1, submittableContent(), AigStudioDraftStatusEnum.EDITING.getCode());
        AigAgent existing = new AigAgent();
        existing.setAgentId(999L);
        existing.setAgentCode("DETAIL_COPYWRITER");
        when(agentMapper.selectOne(any())).thenReturn(existing);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER));

        assertTrue(ex.getMessage().contains("已存在同编码"), ex.getMessage());
        verify(agentMapper, never()).insert(any(AigAgent.class));
    }

    @Test
    @DisplayName("提交：非责任人不能提交")
    void submitRejectsNonOwner() {
        existingDraft(1, submittableContent(), AigStudioDraftStatusEnum.EDITING.getCode());
        assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OTHER));
        verify(agentVersionMapper, never()).insert(any(AigAgentVersion.class));
    }

    @Test
    @DisplayName("提交：已归档的草稿不能再提交")
    void submitRejectsArchived() {
        existingDraft(1, submittableContent(), AigStudioDraftStatusEnum.ARCHIVED.getCode());
        assertThrows(ServiceException.class,
            () -> service.submitDraft(DRAFT_ID, new AigStudioDraftSubmitBo(), OWNER));
    }

}
