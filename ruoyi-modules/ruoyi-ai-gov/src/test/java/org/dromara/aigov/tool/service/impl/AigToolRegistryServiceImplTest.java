package org.dromara.aigov.tool.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.tool.domain.AigToolCall;
import org.dromara.aigov.tool.domain.AigToolDefinition;
import org.dromara.aigov.tool.domain.AigToolDefinitionVersion;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionBo;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionVersionBo;
import org.dromara.aigov.tool.enums.AigToolCallStatusEnum;
import org.dromara.aigov.tool.exec.AigToolCallRequest;
import org.dromara.aigov.tool.exec.AigToolCallResult;
import org.dromara.aigov.tool.exec.AigToolExecutionPolicy;
import org.dromara.aigov.tool.exec.ToolExecutor;
import org.dromara.aigov.tool.mapper.AigToolCallMapper;
import org.dromara.aigov.tool.mapper.AigToolDefinitionMapper;
import org.dromara.aigov.tool.mapper.AigToolDefinitionVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具注册与执行测试（设计 §6.5、§7.5）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigToolRegistryServiceImplTest {

    private static final Long TOOL_ID = 3001L;
    private static final Long VERSION_ID = 4001L;
    private static final Long CALL_ID = 5001L;
    private static final String INPUT = "{\"x\":1}";

    private AigToolDefinitionMapper definitionMapper;
    private AigToolDefinitionVersionMapper versionMapper;
    private AigToolCallMapper callMapper;
    private TestExecutor executor;
    private AigToolRegistryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigToolDefinition.class);
        TableInfoHelper.initTableInfo(assistant, AigToolDefinitionVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigToolCall.class);
    }

    @BeforeEach
    void setUp() {
        definitionMapper = mock(AigToolDefinitionMapper.class);
        versionMapper = mock(AigToolDefinitionVersionMapper.class);
        callMapper = mock(AigToolCallMapper.class);
        executor = new TestExecutor("ref-1");
        service = new AigToolRegistryServiceImpl(definitionMapper, versionMapper, callMapper,
            List.of(executor));
        when(definitionMapper.selectOne(any())).thenReturn(definition());
        when(callMapper.insert(any(AigToolCall.class))).thenAnswer(invocation -> {
            invocation.<AigToolCall>getArgument(0).setCallId(CALL_ID);
            return 1;
        });
        when(callMapper.updateById(any(AigToolCall.class))).thenReturn(1);
    }

    private static AigToolDefinition definition() {
        AigToolDefinition definition = new AigToolDefinition();
        definition.setToolId(TOOL_ID);
        definition.setToolCode("product_fact_query");
        definition.setStatus("0");
        return definition;
    }

    private void stubVersion(String operationType, String executorRef) {
        AigToolDefinitionVersion version = new AigToolDefinitionVersion();
        version.setToolVersionId(VERSION_ID);
        version.setToolId(TOOL_ID);
        version.setVersion("1.0.0");
        version.setOperationType(operationType);
        version.setExecutorRef(executorRef);
        version.setStatus("0");
        when(versionMapper.selectOne(any())).thenReturn(version);
    }

    private static AigToolCallRequest request(String approvalDigest, LocalDateTime expiresAt) {
        return new AigToolCallRequest(1L, "product_fact_query", "1.0.0", "key-1", INPUT,
            approvalDigest == null ? null : "approval-1", approvalDigest, expiresAt);
    }

    @Test
    @DisplayName("★ 只读查询：直接执行，落一条 RUNNING 再收尾成功（带凭证）")
    void readOnlyExecutesAndRecordsCall() {
        stubVersion("READ_ONLY_QUERY", "ref-1");

        AigToolCallResult result = service.execute(request(null, null));

        assertEquals(AigToolCallStatusEnum.SUCCEEDED, result.status());
        assertEquals("R-1", result.receiptRef());
        assertEquals(1, executor.calls);
        ArgumentCaptor<AigToolCall> inserted = ArgumentCaptor.forClass(AigToolCall.class);
        verify(callMapper).insert(inserted.capture());
        assertEquals(AigToolCallStatusEnum.RUNNING.getCode(), inserted.getValue().getStatus(),
            "必须先落 RUNNING（崩溃也留痕）");
        verify(callMapper).updateById(any(AigToolCall.class));
    }

    @Test
    @DisplayName("★ 未接入（无执行器）：拒绝且不落账本（没有发生过调用）")
    void missingExecutorIsRejectedWithoutLedger() {
        stubVersion("READ_ONLY_QUERY", "ref-missing");

        AigToolCallResult result = service.execute(request(null, null));

        assertEquals(AigToolCallStatusEnum.FAILED, result.status());
        assertEquals("TOOL_NOT_EXECUTABLE", result.errorCode());
        verify(callMapper, never()).insert(any(AigToolCall.class));
        assertEquals(0, executor.calls);
    }

    @Test
    @DisplayName("★ 写操作：批准缺失/过期 → 等待人工确认，不执行、不落账本")
    void mutatingOperationNeedsApproval() {
        stubVersion("MUTATE_BUSINESS_DATA", "ref-1");

        AigToolCallResult result = service.execute(request(null, null));
        assertEquals(AigToolCallStatusEnum.NEEDS_CONFIRMATION, result.status());
        assertEquals(0, executor.calls);

        String digest = AigToolExecutionPolicy.inputDigest("product_fact_query", "1.0.0", INPUT);
        AigToolCallResult expired = service.execute(
            request(digest, LocalDateTime.now().minusMinutes(1)));
        assertEquals(AigToolCallStatusEnum.NEEDS_CONFIRMATION, expired.status(),
            "过期批准不能执行");
        verify(callMapper, never()).insert(any(AigToolCall.class));
    }

    @Test
    @DisplayName("★ 写操作：批准摘要与本次输入一致且在有效期内 → 执行")
    void mutatingOperationExecutesWithValidApproval() {
        stubVersion("MUTATE_BUSINESS_DATA", "ref-1");
        String digest = AigToolExecutionPolicy.inputDigest("product_fact_query", "1.0.0", INPUT);

        AigToolCallResult result = service.execute(request(digest, LocalDateTime.now().plusMinutes(5)));

        assertEquals(AigToolCallStatusEnum.SUCCEEDED, result.status());
        assertEquals(1, executor.calls);
    }

    @Test
    @DisplayName("★ 幂等：命中已完成的历史调用 → 直接返回历史结论，不再执行")
    void replayReturnsRecordedResult() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        AigToolCall existing = new AigToolCall();
        existing.setStatus(AigToolCallStatusEnum.SUCCEEDED.getCode());
        existing.setReceiptRef("R-OLD");
        when(callMapper.selectOne(any())).thenReturn(existing);

        AigToolCallResult result = service.execute(request(null, null));

        assertTrue(result.replayed());
        assertEquals(AigToolCallStatusEnum.SUCCEEDED, result.status());
        assertEquals("R-OLD", result.receiptRef());
        assertEquals(0, executor.calls, "命中幂等键不得再次执行");
    }

    @Test
    @DisplayName("★ 幂等：历史调用仍 RUNNING → 结果未知待核查，不重新执行")
    void runningCallYieldsOutcomeUnknown() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        AigToolCall running = new AigToolCall();
        running.setStatus(AigToolCallStatusEnum.RUNNING.getCode());
        when(callMapper.selectOne(any())).thenReturn(running);

        AigToolCallResult result = service.execute(request(null, null));

        assertEquals(AigToolCallStatusEnum.OUTCOME_UNKNOWN, result.status());
        assertEquals(0, executor.calls);
    }

    @Test
    @DisplayName("★ 执行异常：记结果未知（写操作不立即重试，避免重复写入）")
    void executorExceptionYieldsOutcomeUnknown() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        executor.fail = true;

        AigToolCallResult result = service.execute(request(null, null));

        assertEquals(AigToolCallStatusEnum.OUTCOME_UNKNOWN, result.status());
        assertEquals(1, executor.calls, "不自动重试");
        verify(callMapper).updateById(any(AigToolCall.class));
    }

    @Test
    @DisplayName("工具版本：非法操作类型拒绝")
    void addVersionRejectsInvalidOperationType() {
        AigToolDefinitionVersionBo bo = new AigToolDefinitionVersionBo();
        bo.setToolCode("product_fact_query");
        bo.setVersion("1.0.0");
        bo.setOperationType("NO_SUCH_OP");
        assertThrows(ServiceException.class, () -> service.addVersion(bo));
    }

    @Test
    @DisplayName("工具定义：编码重复拒绝")
    void createDefinitionRejectsDuplicateCode() {
        when(definitionMapper.selectCount(any())).thenReturn(1L);
        AigToolDefinitionBo bo = new AigToolDefinitionBo();
        bo.setToolCode("product_fact_query");
        bo.setToolName("产品事实查询");
        assertThrows(ServiceException.class, () -> service.createDefinition(bo, 9L));
    }

    /**
     * 测试用执行器。
     */
    static final class TestExecutor implements ToolExecutor {

        private final String ref;
        int calls;
        boolean fail;

        TestExecutor(String ref) {
            this.ref = ref;
        }

        @Override
        public String executorRef() {
            return ref;
        }

        @Override
        public AigToolCallResult execute(AigToolCallRequest request) {
            calls++;
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return AigToolCallResult.ok("{\"ok\":true}", "R-1");
        }
    }

}
