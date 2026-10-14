package org.dromara.aigov.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.tool.domain.AigToolCall;
import org.dromara.aigov.tool.domain.AigToolDefinition;
import org.dromara.aigov.tool.domain.AigToolDefinitionVersion;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionBo;
import org.dromara.aigov.tool.domain.bo.AigToolDefinitionVersionBo;
import org.dromara.aigov.tool.enums.AigToolCallStatusEnum;
import org.dromara.aigov.tool.enums.AigToolOperationTypeEnum;
import org.dromara.aigov.tool.exec.AigToolCallRequest;
import org.dromara.aigov.tool.exec.AigToolCallResult;
import org.dromara.aigov.tool.exec.AigToolExecutionPolicy;
import org.dromara.aigov.tool.exec.ToolExecutor;
import org.dromara.aigov.tool.mapper.AigToolCallMapper;
import org.dromara.aigov.tool.mapper.AigToolDefinitionMapper;
import org.dromara.aigov.tool.mapper.AigToolDefinitionVersionMapper;
import org.dromara.aigov.tool.service.IAigToolRegistryService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具注册与执行实现（设计 §6.5、§7.5）。
 *
 * <p><b>执行刻意不带 {@code @Transactional}</b>（与 D08 同一纪律）：工具调用是外部/长耗时动作，
 * 包在数据库事务里会让"已登记 RUNNING"的记录在崩溃时随事务回滚，于是"这次可能真的调用过"
 * 的证据丢失——而写工具最怕的正是"调用过但账上没有"。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigToolRegistryServiceImpl implements IAigToolRegistryService {

    private static final String STATUS_NORMAL = "0";

    private final AigToolDefinitionMapper definitionMapper;
    private final AigToolDefinitionVersionMapper versionMapper;
    private final AigToolCallMapper callMapper;
    private final List<ToolExecutor> executors;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDefinition(AigToolDefinitionBo bo, Long actorId) {
        if (bo == null || StringUtils.isBlank(bo.getToolCode())) {
            throw new ServiceException("工具编码不能为空");
        }
        String code = bo.getToolCode().trim();
        Long exists = definitionMapper.selectCount(new LambdaQueryWrapper<AigToolDefinition>()
            .eq(AigToolDefinition::getToolCode, code));
        if (exists != null && exists > 0L) {
            throw new ServiceException("工具编码已存在：" + code);
        }
        AigToolDefinition definition = new AigToolDefinition();
        definition.setToolCode(code);
        definition.setToolName(StringUtils.blankToDefault(bo.getToolName(), code));
        definition.setDescription(bo.getDescription());
        definition.setOwnerId(actorId);
        definition.setDataPolicy(bo.getDataPolicy());
        definition.setStatus(STATUS_NORMAL);
        definition.setDelFlag(STATUS_NORMAL);
        definition.setRemark(bo.getRemark());
        definitionMapper.insert(definition);
        return definition.getToolId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addVersion(AigToolDefinitionVersionBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getToolCode()) || StringUtils.isBlank(bo.getVersion())) {
            throw new ServiceException("工具编码与版本号不能为空");
        }
        if (AigToolOperationTypeEnum.find(bo.getOperationType()) == null) {
            throw new ServiceException("非法的工具操作类型：" + bo.getOperationType()
                + "（只能是 READ_ONLY_QUERY/GENERATE_DRAFT/MUTATE_BUSINESS_DATA/EXTERNAL_SEND）");
        }
        AigToolDefinition definition = definitionMapper.selectOne(
            new LambdaQueryWrapper<AigToolDefinition>()
                .eq(AigToolDefinition::getToolCode, bo.getToolCode().trim())
                .eq(AigToolDefinition::getStatus, STATUS_NORMAL)
                .last("limit 1"));
        if (definition == null) {
            throw new ServiceException("工具不存在或已停用：" + bo.getToolCode());
        }
        Long same = versionMapper.selectCount(new LambdaQueryWrapper<AigToolDefinitionVersion>()
            .eq(AigToolDefinitionVersion::getToolId, definition.getToolId())
            .eq(AigToolDefinitionVersion::getVersion, bo.getVersion().trim()));
        if (same != null && same > 0L) {
            throw new ServiceException("该工具已存在同版本号：" + bo.getVersion());
        }
        AigToolDefinitionVersion version = new AigToolDefinitionVersion();
        version.setToolId(definition.getToolId());
        version.setVersion(bo.getVersion().trim());
        version.setExecutorRef(bo.getExecutorRef());
        version.setOperationType(bo.getOperationType().trim());
        version.setInputSchema(bo.getInputSchema());
        version.setOutputSchema(bo.getOutputSchema());
        version.setTimeoutMs(bo.getTimeoutMs() == null ? 30000 : bo.getTimeoutMs());
        version.setMaxAttempts(bo.getMaxAttempts() == null ? 1 : bo.getMaxAttempts());
        version.setPermissionPoint(bo.getPermissionPoint());
        version.setDataLevelMax(bo.getDataLevelMax());
        version.setStatus(STATUS_NORMAL);
        version.setDelFlag(STATUS_NORMAL);
        version.setRemark(bo.getRemark());
        versionMapper.insert(version);
        return version.getToolVersionId();
    }

    @Override
    public AigToolDefinitionVersion getVersion(String toolCode, String version) {
        if (StringUtils.isBlank(toolCode) || StringUtils.isBlank(version)) {
            throw new ServiceException("工具编码与版本号不能为空");
        }
        AigToolDefinition definition = definitionMapper.selectOne(
            new LambdaQueryWrapper<AigToolDefinition>()
                .eq(AigToolDefinition::getToolCode, toolCode.trim())
                .eq(AigToolDefinition::getStatus, STATUS_NORMAL)
                .last("limit 1"));
        if (definition == null) {
            throw new ServiceException("工具不存在或已停用：" + toolCode);
        }
        AigToolDefinitionVersion row = versionMapper.selectOne(
            new LambdaQueryWrapper<AigToolDefinitionVersion>()
                .eq(AigToolDefinitionVersion::getToolId, definition.getToolId())
                .eq(AigToolDefinitionVersion::getVersion, version.trim())
                .eq(AigToolDefinitionVersion::getStatus, STATUS_NORMAL)
                .last("limit 1"));
        if (row == null) {
            throw new ServiceException("工具版本不存在或已停用：" + toolCode + "@" + version);
        }
        return row;
    }

    @Override
    public AigToolCallResult execute(AigToolCallRequest request) {
        if (request == null || StringUtils.isBlank(request.toolCode())
            || StringUtils.isBlank(request.toolVersion()) || StringUtils.isBlank(request.idempotencyKey())) {
            throw new ServiceException("工具编码、版本号与幂等键都不能为空");
        }
        AigToolDefinitionVersion version = getVersion(request.toolCode(), request.toolVersion());
        ToolExecutor executor = executorFor(version.getExecutorRef());
        String digest = AigToolExecutionPolicy.inputDigest(request.toolCode(), request.toolVersion(),
            request.inputJson());
        AigToolExecutionPolicy.Decision decision = AigToolExecutionPolicy.decide(version.getOperationType(),
            executor != null);
        if (!decision.allowed()) {
            if (!decision.confirmationRequired()) {
                // 未接入 / 操作类型非法：明确拒绝，不落账本（没有发生过调用）
                return AigToolCallResult.failed("TOOL_NOT_EXECUTABLE", decision.reason());
            }
            if (!AigToolExecutionPolicy.approvalValid(digest, request.approvalDigest(),
                request.approvalExpiresAt(), LocalDateTime.now())) {
                // 需要确认而批准无效/缺失：不执行、不落账本
                return AigToolCallResult.needsConfirmation(decision.reason());
            }
        }
        // 幂等：同一个键重试必须命中同一条 call，而不是再次变更业务数据
        AigToolCall existing = findCall(request);
        if (existing != null) {
            return replay(existing);
        }
        AigToolCall call = new AigToolCall();
        call.setToolCode(request.toolCode().trim());
        call.setToolVersion(request.toolVersion().trim());
        call.setTaskId(request.taskId());
        call.setIdempotencyKey(request.idempotencyKey().trim());
        call.setStatus(AigToolCallStatusEnum.RUNNING.getCode());
        call.setInputDigest(digest);
        call.setApprovalRef(request.approvalRef());
        call.setApprovalDigest(request.approvalDigest());
        call.setDelFlag(STATUS_NORMAL);
        callMapper.insert(call);

        AigToolCallResult result;
        try {
            result = executor.execute(request);
        } catch (Exception e) {
            // 写操作结果未知：不立即重试，标记待核查（设计 §7.6）
            log.error("工具执行异常, toolCode={}, version={}, key={}", request.toolCode(),
                request.toolVersion(), request.idempotencyKey(), e);
            result = AigToolCallResult.outcomeUnknown("执行异常，结果未知：" + e.getMessage());
        }
        AigToolCall update = new AigToolCall();
        update.setCallId(call.getCallId());
        update.setStatus(result.status().getCode());
        update.setOutputRef(result.outputRef());
        update.setReceiptRef(result.receiptRef());
        update.setErrorCode(result.errorCode());
        update.setErrorMessage(StringUtils.substring(result.errorMessage(), 0, 500));
        callMapper.updateById(update);
        log.info("工具调用完成, toolCode={}, version={}, status={}, receipt={}, replayed=false",
            request.toolCode(), request.toolVersion(), result.status().getCode(), result.receiptRef());
        return result;
    }

    /**
     * 按执行器引用找已注册的执行器。
     *
     * @param executorRef 执行器引用（可空）
     * @return 执行器；未注册返回 null（= 已登记、待接入）
     */
    private ToolExecutor executorFor(String executorRef) {
        if (StringUtils.isBlank(executorRef) || executors == null) {
            return null;
        }
        for (ToolExecutor executor : executors) {
            if (executorRef.trim().equals(executor.executorRef())) {
                return executor;
            }
        }
        return null;
    }

    /**
     * 按幂等键找既有调用。
     *
     * @param request 请求
     * @return 既有调用；没有返回 null
     */
    private AigToolCall findCall(AigToolCallRequest request) {
        return callMapper.selectOne(new LambdaQueryWrapper<AigToolCall>()
            .eq(AigToolCall::getToolCode, request.toolCode().trim())
            .eq(AigToolCall::getToolVersion, request.toolVersion().trim())
            .eq(AigToolCall::getIdempotencyKey, request.idempotencyKey().trim())
            .last("limit 1"));
    }

    /**
     * 命中幂等键：返回历史结论，不重新执行。
     *
     * <p>历史记录仍是 {@code RUNNING} 时不能当成功也不能当失败——上一次调用可能仍在跑或已中断，
     * 如实返回"结果未知待核查"。写操作在这时候重新执行正是"重复写入"的典型成因。</p>
     *
     * @param existing 既有调用
     * @return 结果
     */
    private AigToolCallResult replay(AigToolCall existing) {
        AigToolCallStatusEnum status = AigToolCallStatusEnum.find(existing.getStatus());
        if (status == null || status == AigToolCallStatusEnum.RUNNING) {
            return AigToolCallResult.outcomeUnknown("该幂等键下已有一次未收尾的调用，结果未知，请先核查凭证");
        }
        return new AigToolCallResult(status, null, existing.getOutputRef(), existing.getReceiptRef(),
            existing.getErrorCode(), existing.getErrorMessage(), true);
    }

}
