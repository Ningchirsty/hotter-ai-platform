package org.dromara.aigov.studio.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.studio.config.AigStudioCopilotProperties;
import org.dromara.aigov.studio.domain.AigStudioCopilotTurn;
import org.dromara.aigov.studio.domain.bo.AigStudioCopilotChatBo;
import org.dromara.aigov.studio.domain.bo.AigStudioProposalCreateBo;
import org.dromara.aigov.studio.domain.vo.AigStudioChangeProposalVo;
import org.dromara.aigov.studio.domain.vo.AigStudioCopilotTurnVo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.enums.AigStudioCopilotStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioProposalSourceEnum;
import org.dromara.aigov.studio.mapper.AigStudioCopilotTurnMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.aigov.studio.service.IAigStudioProposalService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话式 Copilot 服务测试（附件 07 增量 6）。
 *
 * <p><b>要钉死的五件事</b>：①关闭时拒绝（开着它就能花钱）；②<b>模型的话不被信任</b>——
 * 越权路径被规划器拒绝时记 REJECTED、不产提案、也不改草稿；③<b>不伪造灰度证据</b>——
 * 不把 agentVersionId 传给网关；④花钱之前就把非责任人/归档草稿/缺能力挡掉；
 * ⑤失败要留痕（先落 RUNNING，绝不留下永远 RUNNING 的行）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioCopilotServiceImplTest {

    private static final long DRAFT_ID = 9001L;
    private static final long OWNER = 200L;
    private static final long OTHER = 201L;
    private static final long TURN_ID = 8801L;
    private static final long PROPOSAL_ID = 5001L;

    private AigStudioCopilotTurnMapper turnMapper;
    private IAigStudioDraftService draftService;
    private IAigStudioProposalService proposalService;
    private IAigInvokeService invokeService;
    private AigStudioCopilotProperties properties;
    private AigStudioCopilotServiceImpl service;

    /**
     * 内存里的"对话行"
     */
    private AigStudioCopilotTurn[] row;

    /**
     * 首次落库时的状态快照。
     *
     * <p>不能事后读 {@code row[0]}：收尾是在同一个对象上改的（ArgumentCaptor 持有的是引用），
     * 事后读到的是 SUCCEEDED——于是"先落 RUNNING"这条断言会假绿。</p>
     */
    private String insertedStatus;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioCopilotTurn.class);
    }

    @BeforeEach
    void setUp() {
        turnMapper = mock(AigStudioCopilotTurnMapper.class);
        draftService = mock(IAigStudioDraftService.class);
        proposalService = mock(IAigStudioProposalService.class);
        invokeService = mock(IAigInvokeService.class);
        properties = new AigStudioCopilotProperties();
        properties.setEnabled(true);
        service = new AigStudioCopilotServiceImpl(turnMapper, draftService, proposalService,
            invokeService, properties, JsonMapper.builder().build());
        row = new AigStudioCopilotTurn[1];

        when(turnMapper.insert(any(AigStudioCopilotTurn.class))).thenAnswer(invocation -> {
            AigStudioCopilotTurn saved = invocation.getArgument(0);
            saved.setTurnId(TURN_ID);
            insertedStatus = saved.getStatus();
            row[0] = saved;
            return 1;
        });
        when(turnMapper.updateById(any(AigStudioCopilotTurn.class))).thenAnswer(invocation -> {
            AigStudioCopilotTurn update = invocation.getArgument(0);
            if (row[0] == null) {
                return 0;
            }
            row[0].setStatus(update.getStatus());
            row[0].setFailureReason(update.getFailureReason());
            row[0].setProposalId(update.getProposalId());
            row[0].setModelOutput(update.getModelOutput());
            row[0].setTraceId(update.getTraceId());
            row[0].setModelKey(update.getModelKey());
            row[0].setExternalCall(update.getExternalCall());
            return 1;
        });
        when(draftService.getDraft(any())).thenAnswer(invocation -> draft(OWNER,
            AigStudioDraftStatusEnum.EDITING.getCode(),
            "{\"providerCapability\":\"text_generation\"}"));
    }

    /* ------------------------------------------------------------------ 构造 */

    private static AigStudioDraftDetailVo draft(Long owner, String status, String contentJson) {
        AigStudioDraftDetailVo vo = new AigStudioDraftDetailVo();
        vo.setDraftId(DRAFT_ID);
        vo.setOwnerId(owner);
        vo.setStatus(status);
        vo.setLatestRevision(3);
        vo.setContentHash("hash-3");
        vo.setContentJson(contentJson);
        return vo;
    }

    private static AigStudioCopilotChatBo chatBo(String message) {
        AigStudioCopilotChatBo bo = new AigStudioCopilotChatBo();
        bo.setMessage(message);
        bo.setDataLevel("INTERNAL");
        return bo;
    }

    private static AigInvokeVo invokeOk(String output) {
        AigInvokeVo vo = new AigInvokeVo();
        vo.setOutput(output);
        vo.setTraceId("trace-1");
        vo.setModelKey("gpt-x");
        vo.setDeploymentType("LOCAL");
        vo.setExternalCall(false);
        vo.setLatencyMs(120L);
        return vo;
    }

    private void stubProposalCreated() {
        AigStudioChangeProposalVo proposal = new AigStudioChangeProposalVo();
        proposal.setProposalId(PROPOSAL_ID);
        when(proposalService.create(any(), any(), any())).thenReturn(proposal);
    }

    private static final String OK_OUTPUT = "{\"summary\":\"改目标\","
        + "\"operations\":[{\"path\":\"/objective\",\"value\":\"新目标\"}]}";

    /* ------------------------------------------------------------------ 前置拒绝 */

    @Test
    @DisplayName("★ 开关关闭 → 拒绝，且不落库、不调模型")
    void rejectsWhenDisabled() {
        properties.setEnabled(false);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.chat(DRAFT_ID, chatBo("改一下目标"), OWNER));

        assertTrue(error.getMessage().contains("aigov.studio.copilot.enabled"), error.getMessage());
        verify(turnMapper, never()).insert(any(AigStudioCopilotTurn.class));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★ 非责任人 → 在花钱之前拒绝（让非责任人点一下就能烧一次调用，等于白送事故面）")
    void rejectsNonOwnerBeforeSpending() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.chat(DRAFT_ID, chatBo("改一下目标"), OTHER));

        assertTrue(error.getMessage().contains("责任人"), error.getMessage());
        verify(invokeService, never()).invoke(any());
        verify(turnMapper, never()).insert(any(AigStudioCopilotTurn.class));
    }

    @Test
    @DisplayName("归档草稿 / 缺 providerCapability / 空或过长消息 → 都在调用前拒绝")
    void rejectsBadInputBeforeSpending() {
        when(draftService.getDraft(any())).thenReturn(draft(OWNER,
            AigStudioDraftStatusEnum.ARCHIVED.getCode(), "{\"providerCapability\":\"text_generation\"}"));
        ServiceException archived = assertThrows(ServiceException.class,
            () -> service.chat(DRAFT_ID, chatBo("改一下"), OWNER));
        assertTrue(archived.getMessage().contains("已归档"), archived.getMessage());

        when(draftService.getDraft(any())).thenReturn(draft(OWNER,
            AigStudioDraftStatusEnum.EDITING.getCode(), "{\"agentName\":\"x\"}"));
        ServiceException noCapability = assertThrows(ServiceException.class,
            () -> service.chat(DRAFT_ID, chatBo("改一下"), OWNER));
        assertTrue(noCapability.getMessage().contains("providerCapability"), noCapability.getMessage());

        when(draftService.getDraft(any())).thenReturn(draft(OWNER,
            AigStudioDraftStatusEnum.EDITING.getCode(), "{\"providerCapability\":\"text_generation\"}"));
        assertThrows(ServiceException.class, () -> service.chat(DRAFT_ID, chatBo("   "), OWNER));
        assertThrows(ServiceException.class, () -> service.chat(DRAFT_ID, null, OWNER));

        properties.setMaxMessageChars(5);
        ServiceException tooLong = assertThrows(ServiceException.class,
            () -> service.chat(DRAFT_ID, chatBo("这是一句很长的话"), OWNER));
        assertTrue(tooLong.getMessage().contains("过长"), tooLong.getMessage());

        verify(invokeService, never()).invoke(any());
        verify(turnMapper, never()).insert(any(AigStudioCopilotTurn.class));
    }

    /* ------------------------------------------------------------------ 成功 */

    @Test
    @DisplayName("★ 成功：走网关（不传 agentVersionId）、产出提案（baseRevision + source=COPILOT），收尾 SUCCEEDED")
    void chatProducesProposal() {
        stubProposalCreated();
        when(invokeService.invoke(any())).thenReturn(invokeOk(OK_OUTPUT));

        AigStudioCopilotTurnVo result = service.chat(DRAFT_ID, chatBo("把目标改成新目标"), OWNER);

        assertEquals(AigStudioCopilotStatusEnum.SUCCEEDED.getCode(), result.getStatus());
        assertEquals(PROPOSAL_ID, result.getProposalId());
        assertEquals("trace-1", result.getTraceId());
        assertEquals("gpt-x", result.getModelKey());
        assertEquals("N", result.getExternalCall(), "网关说没外发就记 N");
        assertNull(result.getFailureReason());

        // 这一个轮次先落 RUNNING（崩在中间也留痕），再把结论写回去
        ArgumentCaptor<AigStudioCopilotTurn> inserted =
            ArgumentCaptor.forClass(AigStudioCopilotTurn.class);
        verify(turnMapper).insert(inserted.capture());
        assertEquals(AigStudioCopilotStatusEnum.RUNNING.getCode(), insertedStatus,
            "必须先落 RUNNING（崩在调用中间也留下「可能已经调用过」的痕迹）");
        assertEquals(3, inserted.getValue().getRevision());
        assertEquals("hash-3", inserted.getValue().getContentHash());
        verify(turnMapper).updateById(any(AigStudioCopilotTurn.class));

        // 网关入参：用草稿的能力编码、带上提示词、**不传 agentVersionId**
        ArgumentCaptor<AigInvokeBo> invokeBo = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(invokeBo.capture());
        assertEquals("text_generation", invokeBo.getValue().getCapabilityCode());
        assertEquals("INTERNAL", invokeBo.getValue().getDataLevel());
        assertNull(invokeBo.getValue().getAgentVersionId(),
            "传了 agentVersionId 会把该版本的 CANARY 计数抬上去 = 用对话伪造灰度证据");
        assertTrue(invokeBo.getValue().getPrompt().contains("把目标改成新目标"), "提示词要带用户要求");
        assertTrue(invokeBo.getValue().getPrompt().contains("/objective"), "提示词要带白名单");

        // 提案入参：基于当前修订、来源 COPILOT、操作与 summary 原样带过去
        ArgumentCaptor<AigStudioProposalCreateBo> createBo =
            ArgumentCaptor.forClass(AigStudioProposalCreateBo.class);
        verify(proposalService).create(eq(DRAFT_ID), createBo.capture(), eq(OWNER));
        assertEquals(3, createBo.getValue().getBaseRevision());
        assertEquals(AigStudioProposalSourceEnum.COPILOT.getCode(), createBo.getValue().getSource());
        assertEquals("改目标", createBo.getValue().getSummary());
        assertEquals("/objective", createBo.getValue().getOperations().get(0).getPath());
    }

    @Test
    @DisplayName("★ 模型越界（规划器拒绝）→ 记 REJECTED、不产提案、原因保留原文，且不抛给调用方")
    void modelOutOfBoundsIsRecordedAsRejected() {
        when(invokeService.invoke(any())).thenReturn(invokeOk(
            "{\"summary\":\"换类别\",\"operations\":[{\"path\":\"/agentCategory\",\"value\":\"QA\"}]}"));
        // ★拒绝必须由"只校验不落库"的 validateForCreate 判定，**不是**靠 create 抛异常：
        //   create 是 @Transactional，它的异常会把 chat 的共享事务标记 rollback-only ⇒
        //   生产上表现为「发生未知异常」且整轮对话被回滚（2026-10-12 实测 3/3）。
        doThrow(new ServiceException(
            "提案被拒绝：提案不允许改这个字段：/agentCategory（只允许改文案/清单/主 Prompt 分节；"
                + "不开放的是：/agentCategory、/providerCapability）"))
            .when(proposalService).validateForCreate(any(), any(), any());

        AigStudioCopilotTurnVo result = service.chat(DRAFT_ID, chatBo("把这个 Agent 改成 QA 类"), OWNER);

        assertEquals(AigStudioCopilotStatusEnum.REJECTED.getCode(), result.getStatus());
        assertNull(result.getProposalId(), "被拒绝时不能有提案ID");
        assertTrue(result.getFailureReason().contains("/agentCategory"), result.getFailureReason());
        // 拒绝不是失败：人要看得出"模型试图越界"，而不是"系统报错"
        assertTrue(result.getFailureReason().contains("不允许改这个字段"), result.getFailureReason());
        // ★这条断言就是把上面那个线上故障钉在构建期：判 REJECTED 时**绝不能**去调事务性的 create。
        verify(proposalService, never()).create(any(), any(), any());
    }

    /* ------------------------------------------------------------------ 失败留痕 */

    @Test
    @DisplayName("★ 模型输出不是要求的 JSON → FAILED（解析原因留在账本里），且不产提案")
    void unparsableOutputIsFailed() {
        when(invokeService.invoke(any())).thenReturn(invokeOk("我觉得可以改一改"));

        AigStudioCopilotTurnVo result = service.chat(DRAFT_ID, chatBo("改一下"), OWNER);

        assertEquals(AigStudioCopilotStatusEnum.FAILED.getCode(), result.getStatus());
        assertTrue(result.getFailureReason().contains("没有找到 JSON 对象"), result.getFailureReason());
        assertTrue(result.getModelOutput().contains("我觉得可以改一改"), "模型原文要留档");
        verify(proposalService, never()).create(any(), any(), any());
        assertEquals(AigStudioCopilotStatusEnum.FAILED.getCode(), row[0].getStatus());
    }

    @Test
    @DisplayName("★ 调用抛异常 / 没返回内容 → FAILED，绝不留下永远 RUNNING 的行")
    void invokeFailureEndsTheTurn() {
        when(invokeService.invoke(any())).thenThrow(new IllegalStateException("网关超时"));

        AigStudioCopilotTurnVo first = service.chat(DRAFT_ID, chatBo("改一下"), OWNER);
        assertEquals(AigStudioCopilotStatusEnum.FAILED.getCode(), first.getStatus());
        assertTrue(first.getFailureReason().contains("调用异常"), first.getFailureReason());
        assertTrue(first.getFailureReason().contains("网关超时"), first.getFailureReason());

        AigInvokeVo empty = new AigInvokeVo();
        empty.setReason("策略拒绝：无可用候选模型");
        // 重打桩必须用 doReturn：对已 stub 的方法再 when(...) 会先执行旧答案（旧答案是抛异常）
        doReturn(empty).when(invokeService).invoke(any());
        AigStudioCopilotTurnVo second = service.chat(DRAFT_ID, chatBo("再改一下"), OWNER);
        assertEquals(AigStudioCopilotStatusEnum.FAILED.getCode(), second.getStatus());
        assertTrue(second.getFailureReason().contains("策略拒绝"), second.getFailureReason());
    }

    @Test
    @DisplayName("模型原文按配置截断（完整原文按 traceId 从网关审计查）")
    void modelOutputIsCapped() {
        properties.setOutputPreviewChars(20);
        when(invokeService.invoke(any())).thenReturn(invokeOk(OK_OUTPUT));
        stubProposalCreated();

        AigStudioCopilotTurnVo result = service.chat(DRAFT_ID, chatBo("改一下"), OWNER);

        assertEquals(20, result.getModelOutput().length());
    }

    /* ------------------------------------------------------------------ 历史 */

    @Test
    @DisplayName("会话历史：先确认草稿存在，最新在前")
    void turnsReadHistory() {
        AigStudioCopilotTurn historical = new AigStudioCopilotTurn();
        historical.setTurnId(TURN_ID);
        historical.setDraftId(DRAFT_ID);
        historical.setStatus(AigStudioCopilotStatusEnum.SUCCEEDED.getCode());
        when(turnMapper.selectList(any())).thenReturn(List.of(historical));

        List<AigStudioCopilotTurnVo> turns = service.turns(DRAFT_ID);

        assertEquals(1, turns.size());
        assertEquals(TURN_ID, turns.get(0).getTurnId());
        verify(draftService).getDraft(DRAFT_ID);
        verify(turnMapper).selectList(any());
    }

}
