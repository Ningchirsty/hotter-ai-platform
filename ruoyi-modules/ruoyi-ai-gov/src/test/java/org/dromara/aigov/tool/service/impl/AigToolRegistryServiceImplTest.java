package org.dromara.aigov.tool.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
import org.dromara.aigov.tool.domain.AigToolCall;
import org.dromara.aigov.tool.domain.AigToolDefinition;
import org.dromara.aigov.tool.domain.AigToolDefinitionVersion;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionBo;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionVersionBo;
import org.dromara.aigov.tool.domain.vo.AigToolDefinitionVersionVo;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper resourceBindingMapper;
    private TestExecutor executor;
    private AigToolRegistryServiceImpl service;
    private boolean permissionHolds = true;
    private int permissionQueries;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigToolDefinition.class);
        TableInfoHelper.initTableInfo(assistant, AigToolDefinitionVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigToolCall.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentResourceBinding.class);
    }

    @BeforeEach
    void setUp() {
        definitionMapper = mock(AigToolDefinitionMapper.class);
        versionMapper = mock(AigToolDefinitionVersionMapper.class);
        callMapper = mock(AigToolCallMapper.class);
        resourceBindingMapper = mock(org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper.class);
        executor = new TestExecutor("ref-1");
        permissionHolds = true;
        permissionQueries = 0;
        // 身份端口用桩：判据来自服务端身份，但测试只关心"结论如何被使用"
        org.dromara.aigov.tool.exec.ToolPermissionChecker permissionChecker = point -> {
            permissionQueries++;
            return permissionHolds;
        };
        service = new AigToolRegistryServiceImpl(definitionMapper, versionMapper, callMapper,
            resourceBindingMapper, permissionChecker, List.of(executor));
        // 默认：没有启用的 TOOL 绑定（不额外收窄）
        when(resourceBindingMapper.selectList(any())).thenReturn(List.of());
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
        stubVersion(operationType, executorRef, null);
    }

    private void stubVersion(String operationType, String executorRef, String permissionPoint) {
        AigToolDefinitionVersion version = new AigToolDefinitionVersion();
        version.setToolVersionId(VERSION_ID);
        version.setToolId(TOOL_ID);
        version.setVersion("1.0.0");
        version.setOperationType(operationType);
        version.setExecutorRef(executorRef);
        version.setPermissionPoint(permissionPoint);
        version.setStatus("0");
        when(versionMapper.selectOne(any())).thenReturn(version);
    }

    private static AigToolCallRequest request(String approvalDigest, LocalDateTime expiresAt) {
        return new AigToolCallRequest(1L, "product_fact_query", "1.0.0", "key-1", INPUT,
            approvalDigest == null ? null : "approval-1", approvalDigest, expiresAt, null);
    }

    private static AigToolCallRequest request(Long agentVersionId) {
        return new AigToolCallRequest(1L, "product_fact_query", "1.0.0", "key-1", INPUT,
            null, null, null, agentVersionId);
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

    @Test
    @DisplayName("★ 可绑定清单：executable 由已注册执行器决定（未接入如实显示为不可用）")
    void listVersionsMarksExecutableByRegisteredExecutor() {
        AigToolDefinitionVersion ok = versionRow(TOOL_ID, "1.0.0", "READ_ONLY_QUERY", "ref-1");
        AigToolDefinitionVersion missing = versionRow(2L, "2.0.0", "MUTATE_BUSINESS_DATA", "ref-missing");
        when(versionMapper.selectList(any())).thenReturn(List.of(ok, missing));
        AigToolDefinition other = new AigToolDefinition();
        other.setToolId(2L);
        other.setToolCode("other_tool");
        other.setToolName("另一个工具");
        other.setStatus("0");
        when(definitionMapper.selectList(any())).thenReturn(List.of(definition(), other));

        List<AigToolDefinitionVersionVo> list = service.listVersions();

        assertEquals(2, list.size());
        AigToolDefinitionVersionVo available = list.stream()
            .filter(item -> "1.0.0".equals(item.getVersion())).findFirst().orElseThrow();
        assertTrue(available.getExecutable());
        assertFalse(available.getRequiresConfirmation());

        AigToolDefinitionVersionVo pending = list.stream()
            .filter(item -> "2.0.0".equals(item.getVersion())).findFirst().orElseThrow();
        assertFalse(pending.getExecutable(), "未注册执行器必须显示为不可用");
        assertTrue(pending.getRequiresConfirmation(), "变更业务数据需要人工确认");
    }

    @Test
    @DisplayName("★ §7.5：工具不在当前版本的有效绑定范围内 → 拒绝（绑定只做收窄）")
    void toolOutsideBindingIsRejected() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        AigAgentResourceBinding binding = new AigAgentResourceBinding();
        binding.setResourceCode("other_tool");
        binding.setEnabled("Y");
        when(resourceBindingMapper.selectList(any())).thenReturn(List.of(binding));

        AigToolCallResult result = service.execute(request(4242L));

        assertEquals(AigToolCallStatusEnum.FAILED, result.status());
        assertEquals("TOOL_NOT_BOUND", result.errorCode());
        assertEquals(0, executor.calls);
    }

    @Test
    @DisplayName("§7.5：工具命中有效绑定 → 执行")
    void toolInsideBindingExecutes() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        AigAgentResourceBinding binding = new AigAgentResourceBinding();
        binding.setResourceCode("product_fact_query");
        binding.setEnabled("Y");
        when(resourceBindingMapper.selectList(any())).thenReturn(List.of(binding));

        assertEquals(AigToolCallStatusEnum.SUCCEEDED, service.execute(request(4242L)).status());
        assertEquals(1, executor.calls);
    }

    @Test
    @DisplayName("★ 契约详情：用途/数据范围/责任人来自定义，输入输出与超时来自版本（列表不带这些）")
    void versionDetailAssemblesDefinitionAndVersion() {
        AigToolDefinition definition = definition();
        definition.setToolName("产品事实查询");
        definition.setDescription("按 SKU 查批准过的产品事实");
        definition.setOwnerId(31L);
        definition.setDataPolicy("不含密钥；只读产品主数据");
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AigToolDefinitionVersion version = new AigToolDefinitionVersion();
        version.setToolVersionId(VERSION_ID);
        version.setToolId(TOOL_ID);
        version.setVersion("1.0.0");
        version.setOperationType("MUTATE_BUSINESS_DATA");
        version.setExecutorRef("ref-1");
        version.setPermissionPoint("aig:tool:product-fact-query");
        version.setDataLevelMax("INTERNAL");
        version.setInputSchema("{\"fields\":[{\"name\":\"sku\"}]}");
        version.setOutputSchema("{\"fields\":[{\"name\":\"spec\"}]}");
        version.setTimeoutMs(15000);
        version.setMaxAttempts(2);
        version.setRemark("首版");
        version.setStatus("0");
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(definitionMapper.selectById(TOOL_ID)).thenReturn(definition);

        org.dromara.aigov.tool.domain.vo.AigToolDetailVo detail =
            service.getVersionDetail("product_fact_query", "1.0.0");

        assertEquals("product_fact_query", detail.getToolCode());
        assertEquals("产品事实查询", detail.getToolName());
        assertEquals("按 SKU 查批准过的产品事实", detail.getDescription());
        assertEquals(31L, detail.getOwnerId());
        assertEquals("不含密钥；只读产品主数据", detail.getDataPolicy());
        assertEquals("1.0.0", detail.getVersion());
        assertEquals("ref-1", detail.getExecutorRef());
        assertEquals("MUTATE_BUSINESS_DATA", detail.getOperationType());
        assertTrue(detail.getRequiresConfirmation(), "变更业务数据必须人工确认");
        assertTrue(detail.getExecutable(), "已注册执行器 → 可执行");
        assertEquals("aig:tool:product-fact-query", detail.getPermissionPoint());
        assertEquals("INTERNAL", detail.getDataLevelMax());
        assertEquals("{\"fields\":[{\"name\":\"sku\"}]}", detail.getInputSchema());
        assertEquals("{\"fields\":[{\"name\":\"spec\"}]}", detail.getOutputSchema());
        assertEquals(15000, detail.getTimeoutMs());
        assertEquals(2, detail.getMaxAttempts());
    }

    @Test
    @DisplayName("★ 契约详情：未接入执行器时 executable=false（与列表同口径，都由已注册执行器决定）")
    void versionDetailMarksUnwiredExecutor() {
        when(definitionMapper.selectOne(any())).thenReturn(definition());
        AigToolDefinitionVersion version = new AigToolDefinitionVersion();
        version.setToolVersionId(VERSION_ID);
        version.setToolId(TOOL_ID);
        version.setVersion("1.0.0");
        version.setOperationType("READ_ONLY_QUERY");
        version.setExecutorRef("ref-missing");
        version.setStatus("0");
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(definitionMapper.selectById(TOOL_ID)).thenReturn(definition());

        org.dromara.aigov.tool.domain.vo.AigToolDetailVo detail =
            service.getVersionDetail("product_fact_query", "1.0.0");

        assertFalse(detail.getExecutable(), "登记了引用但没有实现 → 仍是「已登记 待接入」");
        assertEquals("ref-missing", detail.getExecutorRef(), "接线的人要知道该实现哪个引用名");
    }

    @Test
    @DisplayName("★ 契约详情：工具或版本不存在时用与执行路径同一口径拒绝")
    void versionDetailRejectsUnknownToolOrVersion() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class,
            () -> service.getVersionDetail("no_such_tool", "1.0.0"));

        when(definitionMapper.selectOne(any())).thenReturn(definition());
        when(versionMapper.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class,
            () -> service.getVersionDetail("product_fact_query", "9.9.9"));
    }

    @Test
    @DisplayName("★ §7.5：声明了权限点而调用人不持有 → 拒绝，不执行、不落账本")
    void declaredPermissionNotHeldIsRejected() {
        stubVersion("READ_ONLY_QUERY", "ref-1", "aig:tool:product-fact-query");
        permissionHolds = false;

        AigToolCallResult result = service.execute(request(null, null));

        assertEquals(AigToolCallStatusEnum.FAILED, result.status());
        assertEquals("TOOL_PERMISSION_DENIED", result.errorCode());
        assertEquals(0, executor.calls);
        verify(callMapper, never()).insert(any(AigToolCall.class));
    }

    @Test
    @DisplayName("§7.5：声明了权限点且调用人持有 → 执行")
    void declaredPermissionHeldExecutes() {
        stubVersion("READ_ONLY_QUERY", "ref-1", "aig:tool:product-fact-query");
        permissionHolds = true;

        assertEquals(AigToolCallStatusEnum.SUCCEEDED, service.execute(request(null, null)).status());
        assertEquals(1, executor.calls);
        assertEquals(1, permissionQueries);
    }

    @Test
    @DisplayName("§7.5：未声明权限点的工具不额外要求（且不查询身份，避免误判为无权限）")
    void blankPermissionPointDoesNotQueryIdentity() {
        stubVersion("READ_ONLY_QUERY", "ref-1");
        permissionHolds = false;

        assertEquals(AigToolCallStatusEnum.SUCCEEDED, service.execute(request(null, null)).status());
        assertEquals(1, executor.calls);
        assertEquals(0, permissionQueries, "未声明权限点就不该去问身份系统");
    }

    @Test
    @DisplayName("★ §7.5：先判绑定再判权限——越界工具报 TOOL_NOT_BOUND，不冒充权限问题")
    void bindingGateEvaluatedBeforePermission() {
        stubVersion("READ_ONLY_QUERY", "ref-1", "aig:tool:product-fact-query");
        AigAgentResourceBinding binding = new AigAgentResourceBinding();
        binding.setResourceCode("other_tool");
        binding.setEnabled("Y");
        when(resourceBindingMapper.selectList(any())).thenReturn(List.of(binding));
        permissionHolds = false;

        AigToolCallResult result = service.execute(request(4242L));

        assertEquals("TOOL_NOT_BOUND", result.errorCode(), "绑定收窄的拒绝理由更具体，应优先报出");
        assertEquals(0, permissionQueries);
        assertEquals(0, executor.calls);
    }

    private static AigToolDefinitionVersion versionRow(Long toolId, String version, String op, String ref) {
        AigToolDefinitionVersion row = new AigToolDefinitionVersion();
        row.setToolVersionId(version.hashCode() * 1L);
        row.setToolId(toolId);
        row.setVersion(version);
        row.setOperationType(op);
        row.setExecutorRef(ref);
        row.setStatus("0");
        return row;
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
