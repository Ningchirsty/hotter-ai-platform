package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.studio.domain.AigStudioChangeProposal;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalDecideBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalOperationBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalSourceEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioRevisionSourceEnum;
import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.dromara.aigov.studio.mapper.AigStudioChangeProposalMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 训练草稿「变更提案」服务测试（增量 21／附件 07 增量 6，ST-003/ST-004）。
 *
 * <p><b>这条测试守的是"Copilot 提议"与"Copilot 改了"之间的那道墙</b>：它的失效不会报错，
 * 只会让一次"采纳建议"悄悄覆盖掉别人刚改的内容、或者让提案被处理两次、或者让越权字段被改掉。
 * 因此逐条钉住：创建绝不写草稿、采纳必须带 CAS 且内容漂移即拒绝、状态只能被处理一次。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioProposalServiceImplTest {

    private static final long DRAFT_ID = 9001L;
    private static final long PROPOSAL_ID = 5001L;
    private static final long OWNER = 200L;
    private static final long OTHER = 201L;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AigStudioChangeProposalMapper proposalMapper;
    private IAigStudioDraftService draftService;
    private AigStudioProposalServiceImpl service;

    /**
     * 内存里的"提案行"
     */
    private AigStudioChangeProposal[] row;

    /**
     * 当前草稿（getDraft 返回它）
     */
    private AigStudioDraftDetailVo draft;

    /**
     * 状态 CAS 的返回行数（模拟并发：置 0 表示"被别人抢先处理了"）
     */
    private int updateRows;

    @BeforeAll
    static void initTableInfo() {
        // 服务用 LambdaQueryWrapper/LambdaUpdateWrapper，需要实体→列的 lambda 缓存；纯单测没有 Spring
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioChangeProposal.class);
    }

    @BeforeEach
    void setUp() {
        proposalMapper = mock(AigStudioChangeProposalMapper.class);
        draftService = mock(IAigStudioDraftService.class);
        service = new AigStudioProposalServiceImpl(proposalMapper, draftService, MAPPER);
        row = new AigStudioChangeProposal[1];
        updateRows = 1;

        when(proposalMapper.insert(any(AigStudioChangeProposal.class))).thenAnswer(invocation -> {
            AigStudioChangeProposal saved = invocation.getArgument(0);
            // 真实 MP 在 insert 时分配主键（雪花），mock 不会
            saved.setProposalId(PROPOSAL_ID);
            row[0] = saved;
            return 1;
        });
        when(proposalMapper.selectById(any())).thenAnswer(invocation -> row[0]);
        when(proposalMapper.selectList(any())).thenAnswer(invocation -> {
            List<AigStudioChangeProposal> list = new ArrayList<>();
            if (row[0] != null) {
                list.add(row[0]);
            }
            return list;
        });
        when(proposalMapper.update(any(AigStudioChangeProposal.class), any())).thenAnswer(invocation -> {
            if (updateRows == 0) {
                return 0;
            }
            AigStudioChangeProposal update = invocation.getArgument(0);
            if (row[0] != null) {
                row[0].setStatus(update.getStatus());
                row[0].setDecidedBy(update.getDecidedBy());
                row[0].setDecidedAt(update.getDecidedAt());
                row[0].setDecidedReason(update.getDecidedReason());
            }
            return updateRows;
        });
        when(draftService.getDraft(any())).thenAnswer(invocation -> draft);
        when(draftService.applyRevision(any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                Integer expected = invocation.getArgument(1);
                AigStudioDraftContent content = invocation.getArgument(2);
                AigStudioDraftDetailVo updated = new AigStudioDraftDetailVo();
                updated.setDraftId(id);
                updated.setOwnerId(OWNER);
                updated.setLatestRevision(expected + 1);
                updated.setContentJson(canonicalJson(content));
                updated.setContentHash(AigStudioContentHasher.hash(canonicalJson(content)));
                updated.setStatus(AigStudioDraftStatusEnum.EDITING.getCode());
                updated.setRevisionCreated(true);
                return updated;
            });
    }

    /* ------------------------------------------------------------------ 构造 */

    private static AigStudioDraftContent content(String objective) {
        AigStudioDraftContent content = new AigStudioDraftContent();
        content.setAgentName("详情页文案助手");
        content.setObjective(objective);
        content.getPromptSections().put("role", "策划");
        return content;
    }

    private static String canonicalJson(AigStudioDraftContent content) {
        try {
            return AigStudioContentHasher.canonicalize(MAPPER.writeValueAsString(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 造一份草稿（getDraft 返回它）。
     *
     * @param revision 当前修订号
     * @param content  内容
     * @param status   状态
     */
    private void existingDraft(int revision, AigStudioDraftContent content, String status) {
        String canonical = canonicalJson(content);
        draft = new AigStudioDraftDetailVo();
        draft.setDraftId(DRAFT_ID);
        draft.setOwnerId(OWNER);
        draft.setLatestRevision(revision);
        draft.setContentJson(canonical);
        draft.setContentHash(AigStudioContentHasher.hash(canonical));
        draft.setStatus(status);
    }

    private static AigStudioProposalCreateBo createBo(int baseRevision, String path, Object value) {
        AigStudioProposalOperationBo operation = new AigStudioProposalOperationBo();
        operation.setPath(path);
        operation.setValue(value);
        AigStudioProposalCreateBo bo = new AigStudioProposalCreateBo();
        bo.setBaseRevision(baseRevision);
        bo.setOperations(new ArrayList<>(List.of(operation)));
        bo.setSummary("把目标改成新目标");
        return bo;
    }

    private static AigStudioProposalDecideBo decideBo(Integer expectedRevision) {
        AigStudioProposalDecideBo bo = new AigStudioProposalDecideBo();
        bo.setExpectedRevision(expectedRevision);
        return bo;
    }

    /**
     * 走一遍"创建提案"（多数用例的公共前置）。
     */
    private void createOne() {
        service.create(DRAFT_ID, createBo(1, "/objective", "新目标"), OWNER);
    }

    /* ------------------------------------------------------------------ 创建 */

    @Test
    @DisplayName("★ 创建：只落一条待审提案——操作清单与预览留档，草稿一个字都不动")
    void createOnlyRegistersProposal() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        AigStudioChangeProposalVo vo = service.create(DRAFT_ID,
            createBo(1, "/objective", "新目标"), OWNER);

        assertEquals(PROPOSAL_ID, vo.getProposalId());
        assertEquals(DRAFT_ID, vo.getDraftId());
        assertEquals(1, vo.getBaseRevision());
        assertEquals(AigStudioProposalStatusEnum.PENDING.getCode(), vo.getStatus());
        assertEquals(AigStudioProposalSourceEnum.COPILOT.getCode(), vo.getSource());
        assertNotNull(vo.getContentHashBefore());
        assertNotNull(vo.getContentHashAfter());
        assertEquals(1, vo.getPreview().size());
        assertEquals("/objective", vo.getPreview().get(0).path());
        assertEquals("旧目标", vo.getPreview().get(0).before());
        assertEquals("新目标", vo.getPreview().get(0).after());

        // 落库的操作清单是规范化的（只留 path/value）
        assertTrue(row[0].getOperationsJson().contains("\"path\":\"/objective\""),
            row[0].getOperationsJson());
        assertTrue(row[0].getOperationsJson().contains("\"value\":\"新目标\""),
            row[0].getOperationsJson());
        assertEquals("把目标改成新目标", row[0].getSummary());

        // ★ 创建提案绝不写草稿：这是"提议"与"改了"之间的墙
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 创建：baseRevision 与草稿当前修订不一致 → 拒绝（针对旧内容的提案已失效）")
    void createRejectsStaleBaseRevision() {
        existingDraft(3, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, createBo(2, "/objective", "新目标"), OWNER));

        assertTrue(ex.getMessage().contains("已不是当前修订"), ex.getMessage());
        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("★ 创建：提案值与当前内容一致（无变化）→ 拒绝，不产生空提案")
    void createRejectsNoopProposal() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, createBo(1, "/objective", "旧目标"), OWNER));

        assertTrue(ex.getMessage().contains("没有产生任何内容变化"), ex.getMessage());
        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("★ 创建：越权路径（能力编码等）→ 拒绝，且不落库")
    void createRejectsUnauthorizedPath() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, createBo(1, "/providerCapability", "image_generation"), OWNER));

        assertTrue(ex.getMessage().contains("提案被拒绝"), ex.getMessage());
        assertTrue(ex.getMessage().contains("不允许改这个字段"), ex.getMessage());
        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("★ validateForCreate 与 create 同一套规则：越权路径同样被拒，且一行都不落库")
    void validateForCreateUsesSameRulesAndWritesNothing() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.validateForCreate(DRAFT_ID,
                createBo(1, "/providerCapability", "image_generation"), OWNER));

        assertTrue(ex.getMessage().contains("提案被拒绝"), ex.getMessage());
        assertTrue(ex.getMessage().contains("不允许改这个字段"), ex.getMessage());
        // ★这就是这个方法的全部意义：把"模型越界"判出来，但**不落库**——
        //   落库走 create（事务性），而"抛出业务异常"不能发生在事务边界上（见 §十八）。
        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("★ validateForCreate 通过时也不落库（它只算，不写）")
    void validateForCreateDoesNotWriteWhenValid() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        service.validateForCreate(DRAFT_ID, createBo(1, "/objective", "新目标"), OWNER);

        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("创建：非责任人不能提提案")
    void createRejectsNonOwner() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, createBo(1, "/objective", "新目标"), OTHER));

        assertTrue(ex.getMessage().contains("责任人"), ex.getMessage());
        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("创建：已归档的草稿不能再提提案 / 非法来源要拒绝 / 缺 baseRevision 要拒绝")
    void createRejectsBadInput() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.ARCHIVED.getCode());
        assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, createBo(1, "/objective", "新目标"), OWNER));

        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        AigStudioProposalCreateBo badSource = createBo(1, "/objective", "新目标");
        badSource.setSource("NOT_A_SOURCE");
        ServiceException sourceError = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, badSource, OWNER));
        assertTrue(sourceError.getMessage().contains("提案来源非法"), sourceError.getMessage());

        AigStudioProposalCreateBo noRevision = createBo(1, "/objective", "新目标");
        noRevision.setBaseRevision(null);
        ServiceException revisionError = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, noRevision, OWNER));
        assertTrue(revisionError.getMessage().contains("baseRevision"), revisionError.getMessage());

        verify(proposalMapper, never()).insert(any(AigStudioChangeProposal.class));
    }

    @Test
    @DisplayName("创建：空操作清单 → 拒绝")
    void createRejectsEmptyOperations() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        AigStudioProposalCreateBo bo = createBo(1, "/objective", "新目标");
        bo.setOperations(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(DRAFT_ID, bo, OWNER));

        assertTrue(ex.getMessage().contains("至少要有一处改动"), ex.getMessage());
    }

    @Test
    @DisplayName("清单：能列出草稿的提案，并把留档的预览解析成结构化视图")
    void listParsesPreview() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();

        List<AigStudioChangeProposalVo> list = service.list(DRAFT_ID);

        assertEquals(1, list.size());
        assertEquals("/objective", list.get(0).getPreview().get(0).path());
        // createOne 里读过一次草稿，这里是 list 的第二次（必须先确认草稿存在）
        verify(draftService, times(2)).getDraft(DRAFT_ID);
    }

    /* ------------------------------------------------------------------ 采纳 */

    @Test
    @DisplayName("★ 采纳：按提案内容落一条 COPILOT_ACCEPTED 新修订，并把提案置为已采纳")
    void applyWritesRevisionAndMarksApplied() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        AigStudioChangeProposalVo proposal = service.create(DRAFT_ID,
            createBo(1, "/objective", "新目标"), OWNER);

        AigStudioDraftDetailVo updated = service.apply(PROPOSAL_ID, decideBo(1), OWNER);

        assertTrue(updated.getRevisionCreated());
        assertEquals(2, updated.getLatestRevision());
        assertEquals(AigStudioProposalStatusEnum.APPLIED.getCode(), row[0].getStatus());
        assertEquals(OWNER, row[0].getDecidedBy());
        assertNotNull(row[0].getDecidedAt());

        // 落库内容确实是"应用了提案"之后的内容
        assertEquals(AigStudioContentHasher.hash(canonicalJson(content("新目标"))),
            updated.getContentHash());
        assertEquals(proposal.getContentHashAfter(), updated.getContentHash());

        ArgumentCaptor<AigStudioRevisionSourceEnum> source =
            ArgumentCaptor.forClass(AigStudioRevisionSourceEnum.class);
        verify(draftService).applyRevision(eq(DRAFT_ID), eq(1), any(), source.capture(), any(), eq(OWNER));
        assertEquals(AigStudioRevisionSourceEnum.COPILOT_ACCEPTED, source.getValue(),
            "采纳提案必须标成 COPILOT_ACCEPTED，否则看不出这条修订是模型提的");
    }

    @Test
    @DisplayName("★ 采纳：提案基于旧修订 → 拒绝（不能按旧提案覆盖新内容）")
    void applyRejectsStaleBaseRevision() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        // 提案提出后草稿又被人推进了一版
        draft.setLatestRevision(2);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(2), OWNER));

        assertTrue(ex.getMessage().contains("提案基于旧修订"), ex.getMessage());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 采纳：草稿内容在提案之后漂移（哈希对不上）→ 拒绝，不按老提案覆盖")
    void applyRejectsDriftedContent() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        // 模拟"提案之外有人改了别处"：预计哈希与实际重放算出的不一致
        row[0].setContentHashAfter("deadbeef");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(1), OWNER));

        assertTrue(ex.getMessage().contains("内容哈希不一致"), ex.getMessage());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 采纳：提案只能被处理一次（已是终态 → 拒绝）")
    void applyRejectsAlreadyDecided() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        row[0].setStatus(AigStudioProposalStatusEnum.APPLIED.getCode());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(1), OWNER));

        assertTrue(ex.getMessage().contains("已被处理过"), ex.getMessage());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 采纳：并发下状态 CAS 未命中（0 行）→ 报错，不假装成功")
    void applyReportsConcurrentDecision() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        updateRows = 0;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(1), OWNER));

        assertTrue(ex.getMessage().contains("并发"), ex.getMessage());
    }

    @Test
    @DisplayName("采纳：缺 expectedRevision → 直接拒绝（写草稿必须带 CAS）")
    void applyRejectsMissingExpectedRevision() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();

        assertThrows(ServiceException.class, () -> service.apply(PROPOSAL_ID, decideBo(null), OWNER));
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("采纳：草稿服务判定「没产生新修订」→ 拒绝，不把提案标成已采纳")
    void applyRejectsWhenNoRevisionCreated() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        // 注意：对"已 stub 过"的方法再 when(...) 会先执行旧答案；重打桩要用 doAnswer
        doAnswer(invocation -> {
            AigStudioDraftDetailVo vo = new AigStudioDraftDetailVo();
            vo.setDraftId(DRAFT_ID);
            vo.setLatestRevision(1);
            vo.setRevisionCreated(false);
            return vo;
        }).when(draftService).applyRevision(any(), any(), any(), any(), any(), any());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(1), OWNER));

        assertTrue(ex.getMessage().contains("未产生新修订"), ex.getMessage());
        assertEquals(AigStudioProposalStatusEnum.PENDING.getCode(), row[0].getStatus(),
            "落修订失败时提案必须还是待处理");
    }

    @Test
    @DisplayName("采纳：非责任人不能采纳")
    void applyRejectsNonOwner() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.apply(PROPOSAL_ID, decideBo(1), OTHER));

        assertTrue(ex.getMessage().contains("责任人"), ex.getMessage());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    /* ------------------------------------------------------------------ 拒绝 */

    @Test
    @DisplayName("拒绝：置为已拒绝并留原因，草稿内容不变")
    void rejectMarksRejectedWithReason() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        AigStudioProposalDecideBo bo = new AigStudioProposalDecideBo();
        bo.setReason("文案口径不符");

        AigStudioChangeProposalVo vo = service.reject(PROPOSAL_ID, bo, OWNER);

        assertEquals(AigStudioProposalStatusEnum.REJECTED.getCode(), vo.getStatus());
        assertEquals("文案口径不符", vo.getDecidedReason());
        assertEquals(OWNER, vo.getDecidedBy());
        verify(draftService, never()).applyRevision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("拒绝：已处理过的提案不能再拒绝（只能处理一次）")
    void rejectRejectsAlreadyDecided() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        row[0].setStatus(AigStudioProposalStatusEnum.REJECTED.getCode());

        assertThrows(ServiceException.class,
            () -> service.reject(PROPOSAL_ID, new AigStudioProposalDecideBo(), OWNER));
    }

    @Test
    @DisplayName("拒绝：不存在的提案 → 报错（不能当成功）")
    void rejectRejectsUnknownProposal() {
        assertThrows(ServiceException.class,
            () -> service.reject(PROPOSAL_ID, new AigStudioProposalDecideBo(), OWNER));
    }

    @Test
    @DisplayName("拒绝：并发 CAS 未命中 → 报错")
    void rejectReportsConcurrentDecision() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();
        updateRows = 0;

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.reject(PROPOSAL_ID, new AigStudioProposalDecideBo(), OWNER));

        assertTrue(ex.getMessage().contains("并发"), ex.getMessage());
    }

    @Test
    @DisplayName("拒绝：非责任人不能拒绝")
    void rejectRejectsNonOwner() {
        existingDraft(1, content("旧目标"), AigStudioDraftStatusEnum.EDITING.getCode());
        createOne();

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.reject(PROPOSAL_ID, new AigStudioProposalDecideBo(), OTHER));

        assertTrue(ex.getMessage().contains("责任人"), ex.getMessage());
    }

}
