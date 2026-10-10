package org.dromara.aigov.studio.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.studio.config.AigStudioTestProperties;
import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.dromara.aigov.studio.domain.AigStudioExecutionLink;
import org.dromara.aigov.studio.domain.AigStudioRevision;
import org.dromara.aigov.studio.domain.bo.AigStudioTestRunBo;
import org.dromara.aigov.studio.domain.vo.AigStudioDraftDetailVo;
import org.dromara.aigov.studio.domain.vo.AigStudioTestRunVo;
import org.dromara.aigov.studio.enums.AigStudioDraftStatusEnum;
import org.dromara.aigov.studio.enums.AigStudioTestStatusEnum;
import org.dromara.aigov.studio.mapper.AigStudioExecutionLinkMapper;
import org.dromara.aigov.studio.mapper.AigStudioRevisionMapper;
import org.dromara.aigov.studio.service.IAigStudioDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 训练台测试调用测试（S5）。
 *
 * <p><b>三条要钉死的</b>：①默认关闭时直接拒绝（开着它就能花钱）；
 * ②<b>测试调用不能带 agentVersionId</b>——带了就会把该版本的 CANARY 调用计数/失败率抬上去，
 * 等于用测试伪造灰度证据；③证据<b>先落 RUNNING 再调用</b>，崩在中间也留下痕迹。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioTestServiceImplTest {

    private static final long DRAFT_ID = 9001L;
    private static final long REVISION_ID = 7001L;
    private static final long LINK_ID = 8001L;
    private static final long OWNER = 200L;
    private static final long OTHER = 201L;

    private IAigStudioDraftService draftService;
    private AigStudioRevisionMapper revisionMapper;
    private AigStudioExecutionLinkMapper linkMapper;
    private IAigInvokeService invokeService;
    private AigStudioTestProperties properties;
    private AigStudioTestServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigStudioRevision.class);
        TableInfoHelper.initTableInfo(assistant, AigStudioExecutionLink.class);
    }

    @BeforeEach
    void setUp() {
        draftService = mock(IAigStudioDraftService.class);
        revisionMapper = mock(AigStudioRevisionMapper.class);
        linkMapper = mock(AigStudioExecutionLinkMapper.class);
        invokeService = mock(IAigInvokeService.class);
        properties = new AigStudioTestProperties();
        properties.setEnabled(true);
        service = new AigStudioTestServiceImpl(draftService, revisionMapper, linkMapper,
            invokeService, properties, JsonMapper.builder().build());

        when(draftService.getDraft(DRAFT_ID)).thenReturn(draft());
        AigStudioRevision revision = new AigStudioRevision();
        revision.setRevisionId(REVISION_ID);
        revision.setDraftId(DRAFT_ID);
        revision.setRevisionNo(2);
        when(revisionMapper.selectOne(any())).thenReturn(revision);
        when(linkMapper.insert(any(AigStudioExecutionLink.class))).thenAnswer(invocation -> {
            invocation.<AigStudioExecutionLink>getArgument(0).setLinkId(LINK_ID);
            return 1;
        });
        when(linkMapper.updateById(any(AigStudioExecutionLink.class))).thenReturn(1);
    }

    private static AigStudioDraftDetailVo draft() {
        AigStudioDraftDetailVo vo = new AigStudioDraftDetailVo();
        vo.setDraftId(DRAFT_ID);
        vo.setAgentCode("DETAIL_COPYWRITER");
        vo.setOwnerId(OWNER);
        vo.setLatestRevision(2);
        vo.setContentHash("hash-2");
        vo.setAgentVersionId(4242L);
        vo.setStatus(AigStudioDraftStatusEnum.EDITING.getCode());
        vo.setContentJson(content());
        return vo;
    }

    private static String content() {
        StringBuilder sections = new StringBuilder();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            if (sections.length() > 0) {
                sections.append(',');
            }
            sections.append('"').append(key).append("\":\"节-").append(key).append('"');
        }
        return "{\"agentCategory\":\"PLANNING\",\"providerCapability\":\"text_generation\","
            + "\"promptSections\":{" + sections + "}}";
    }

    private static AigStudioTestRunBo bo() {
        AigStudioTestRunBo bo = new AigStudioTestRunBo();
        bo.setDataLevel("INTERNAL");
        bo.setInput("请写一句产品卖点");
        return bo;
    }

    private static AigInvokeVo invoked(String output, String errorCode) {
        AigInvokeVo vo = new AigInvokeVo();
        vo.setOutput(output);
        vo.setErrorCode(errorCode);
        vo.setTraceId("trace-1");
        vo.setModelKey("local-text");
        vo.setDeploymentType("LOCAL");
        vo.setExternalCall(false);
        vo.setLatencyMs(120L);
        vo.setDecision("MODEL");
        vo.setReasonCode("OK");
        return vo;
    }

    // ------------------------------------------------------------------ 开关与入参

    @Test
    @DisplayName("★ 默认关闭：开着它就能花真钱，所以关着时必须直接拒绝并说清原因")
    void disabledByDefaultIsRejected() {
        properties.setEnabled(false);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.runTest(DRAFT_ID, bo(), OWNER));

        assertTrue(ex.getMessage().contains("aigov.studio.test.enabled"), ex.getMessage());
        verify(invokeService, never()).invoke(any());
        verify(linkMapper, never()).insert(any(AigStudioExecutionLink.class));
    }

    @Test
    @DisplayName("★ 数据等级必填且要合法（按 INTERNAL 测通、按 RESTRICTED 上线 = 测了错的那件事）")
    void dataLevelIsRequired() {
        AigStudioTestRunBo noLevel = bo();
        noLevel.setDataLevel(null);
        assertThrows(ServiceException.class, () -> service.runTest(DRAFT_ID, noLevel, OWNER));

        AigStudioTestRunBo badLevel = bo();
        badLevel.setDataLevel("BOGUS");
        assertThrows(ServiceException.class, () -> service.runTest(DRAFT_ID, badLevel, OWNER));

        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("输入为空/过长都拒绝（过长的输入不是测试，是把整篇文档塞进来）")
    void inputIsBounded() {
        AigStudioTestRunBo empty = bo();
        empty.setInput("  ");
        assertThrows(ServiceException.class, () -> service.runTest(DRAFT_ID, empty, OWNER));

        properties.setMaxInputChars(5);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.runTest(DRAFT_ID, bo(), OWNER));
        assertTrue(ex.getMessage().contains("过长"), ex.getMessage());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("非责任人不能跑测试（会花钱，必须能追到人）")
    void nonOwnerIsRejected() {
        assertThrows(ServiceException.class, () -> service.runTest(DRAFT_ID, bo(), OTHER));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("已归档的草稿不能跑测试")
    void archivedIsRejected() {
        AigStudioDraftDetailVo archived = draft();
        archived.setStatus(AigStudioDraftStatusEnum.ARCHIVED.getCode());
        when(draftService.getDraft(DRAFT_ID)).thenReturn(archived);

        assertThrows(ServiceException.class, () -> service.runTest(DRAFT_ID, bo(), OWNER));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("没声明能力 / 找不到对应修订：都要拒绝（否则测试钉不到内容上）")
    void capabilityAndRevisionAreRequired() {
        AigStudioDraftDetailVo noCapability = draft();
        noCapability.setContentJson(content().replace("\"providerCapability\":\"text_generation\",", ""));
        when(draftService.getDraft(DRAFT_ID)).thenReturn(noCapability);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.runTest(DRAFT_ID, bo(), OWNER));
        assertTrue(ex.getMessage().contains("providerCapability"), ex.getMessage());

        when(draftService.getDraft(DRAFT_ID)).thenReturn(draft());
        when(revisionMapper.selectOne(any())).thenReturn(null);
        ServiceException ex2 = assertThrows(ServiceException.class,
            () -> service.runTest(DRAFT_ID, bo(), OWNER));
        assertTrue(ex2.getMessage().contains("修订记录"), ex2.getMessage());
        verify(invokeService, never()).invoke(any());
    }

    // ------------------------------------------------------------------ 成功路径

    @Test
    @DisplayName("★ 成功：证据先落 RUNNING、再调网关；结果带摘要与 traceId，输出预览标明是否截断")
    void successWritesEvidenceAndReturnsPreview() {
        when(invokeService.invoke(any())).thenReturn(invoked("这是一段输出", null));

        AigStudioTestRunVo vo = service.runTest(DRAFT_ID, bo(), OWNER);

        // 顺序：先落证据、后调用（崩在中间也留下"可能已经调用过"的痕迹）
        InOrder order = inOrder(linkMapper, invokeService);
        order.verify(linkMapper).insert(any(AigStudioExecutionLink.class));
        order.verify(invokeService).invoke(any());

        ArgumentCaptor<AigStudioExecutionLink> insertCaptor =
            ArgumentCaptor.forClass(AigStudioExecutionLink.class);
        verify(linkMapper).insert(insertCaptor.capture());
        AigStudioExecutionLink inserted = insertCaptor.getValue();
        assertEquals(AigStudioTestStatusEnum.RUNNING.getCode(), inserted.getTestStatus(),
            "调用前必须是 RUNNING——调用可能真的发生了");
        assertEquals(REVISION_ID, inserted.getRevisionId(), "必须钉到具体修订，不能只记草稿");
        assertEquals("hash-2", inserted.getContentHash());
        assertEquals(4242L, inserted.getAgentVersionId(), "版本号只记在自有证据表里");

        ArgumentCaptor<AigInvokeBo> invokeCaptor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(invokeCaptor.capture());
        AigInvokeBo sent = invokeCaptor.getValue();
        assertEquals("text_generation", sent.getCapabilityCode());
        assertEquals("INTERNAL", sent.getDataLevel());
        assertTrue(sent.getPrompt().contains("## role"), "prompt 用与提交同一份分节拼装");
        assertTrue(sent.getPrompt().contains("请写一句产品卖点"), "测试输入要拼进去");
        // ★ 关键：不能带 agentVersionId，否则会把该版本的灰度计数抬上去
        assertNull(sent.getAgentVersionId(), "测试调用绝不能带版本号——那会用测试伪造灰度证据");

        ArgumentCaptor<AigStudioExecutionLink> updateCaptor =
            ArgumentCaptor.forClass(AigStudioExecutionLink.class);
        verify(linkMapper).updateById(updateCaptor.capture());
        AigStudioExecutionLink updated = updateCaptor.getValue();
        assertEquals(AigStudioTestStatusEnum.SUCCEEDED.getCode(), updated.getTestStatus());
        assertEquals(DigestUtil.sha256Hex("这是一段输出"), updated.getResultDigest());
        assertEquals("trace-1", updated.getTraceId());
        assertNull(updated.getErrorMessage());

        assertEquals(AigStudioTestStatusEnum.SUCCEEDED.getCode(), vo.getTestStatus());
        assertEquals("这是一段输出", vo.getOutput());
        assertFalse(vo.getOutputTruncated());
        assertEquals("local-text", vo.getModelKey());
        assertEquals("LOCAL", vo.getDeploymentType());
        assertEquals(120L, vo.getLatencyMs());
        assertTrue(vo.getPolicyHits().contains("MODEL"), vo.getPolicyHits());
    }

    @Test
    @DisplayName("输出超过预览长度：标记为截断（不标的话人会以为模型只输出了这么点）")
    void longOutputIsMarkedTruncated() {
        properties.setOutputPreviewChars(5);
        when(invokeService.invoke(any())).thenReturn(invoked("0123456789", null));

        AigStudioTestRunVo vo = service.runTest(DRAFT_ID, bo(), OWNER);

        assertEquals("01234", vo.getOutput());
        assertTrue(vo.getOutputTruncated());
    }

    // ------------------------------------------------------------------ 失败路径

    @Test
    @DisplayName("有输出但有错误码 → FAILED（成功判据与任务执行同一口径：必须有输出且无错误码）")
    void errorCodeMarksFailed() {
        AigInvokeVo failed = invoked("一段看似正常的文本", "POLICY_DENIED");
        failed.setReason("策略拒绝：严格级不允许外发");
        when(invokeService.invoke(any())).thenReturn(failed);

        AigStudioTestRunVo vo = service.runTest(DRAFT_ID, bo(), OWNER);

        assertEquals(AigStudioTestStatusEnum.FAILED.getCode(), vo.getTestStatus());
        ArgumentCaptor<AigStudioExecutionLink> captor =
            ArgumentCaptor.forClass(AigStudioExecutionLink.class);
        verify(linkMapper).updateById(captor.capture());
        assertEquals(AigStudioTestStatusEnum.FAILED.getCode(), captor.getValue().getTestStatus());
        assertTrue(captor.getValue().getErrorMessage().contains("严格级"), captor.getValue().getErrorMessage());
    }

    @Test
    @DisplayName("★ 网关抛异常也要把证据收尾成 FAILED（不能留一条永远 RUNNING 的行）")
    void exceptionStillClosesEvidence() {
        when(invokeService.invoke(any())).thenThrow(new ServiceException("网关不可用"));

        AigStudioTestRunVo vo = service.runTest(DRAFT_ID, bo(), OWNER);

        assertEquals(AigStudioTestStatusEnum.FAILED.getCode(), vo.getTestStatus());
        assertTrue(vo.getReason().contains("网关不可用"), vo.getReason());
        ArgumentCaptor<AigStudioExecutionLink> captor =
            ArgumentCaptor.forClass(AigStudioExecutionLink.class);
        verify(linkMapper).updateById(captor.capture());
        assertEquals(AigStudioTestStatusEnum.FAILED.getCode(), captor.getValue().getTestStatus());
        assertNull(captor.getValue().getResultDigest(), "没有输出就不该有摘要哈希");
    }

    // ------------------------------------------------------------------ 读证据

    @Test
    @DisplayName("读证据：不存在要报错，不能返回 null 让上层去猜")
    void getTestRunRejectsUnknown() {
        when(linkMapper.selectById(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.getTestRun(LINK_ID));
    }

    @Test
    @DisplayName("读证据：不再调用网关，只回放已记录的事实")
    void getTestRunDoesNotInvoke() {
        AigStudioExecutionLink link = new AigStudioExecutionLink();
        link.setLinkId(LINK_ID);
        link.setDraftId(DRAFT_ID);
        link.setContentHash("hash-2");
        link.setTestStatus(AigStudioTestStatusEnum.SUCCEEDED.getCode());
        link.setResultDigest("digest-1");
        link.setTraceId("trace-1");
        when(linkMapper.selectById(LINK_ID)).thenReturn(link);

        AigStudioTestRunVo vo = service.getTestRun(LINK_ID);

        assertEquals(LINK_ID, vo.getLinkId());
        assertEquals(AigStudioTestStatusEnum.SUCCEEDED.getCode(), vo.getTestStatus());
        assertEquals("digest-1", vo.getResultDigest());
        assertEquals("trace-1", vo.getTraceId());
        verify(invokeService, never()).invoke(any());
    }

}
