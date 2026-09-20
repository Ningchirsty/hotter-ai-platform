package org.dromara.aigov.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigInvokeResultEnum;
import org.dromara.aigov.enums.AigManualDecisionEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.helper.AigOutputSchemaValidator;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 调用编排实现（SPEC §5.3）。
 *
 * <p>顺序固定：</p>
 * <ol>
 *     <li>生成 {@code traceId}</li>
 *     <li>{@code routeService.decide(...)}</li>
 *     <li><b>无论成功失败都写一条 {@code aig_invocation_audit}</b>（try/finally + 独立 Bean）</li>
 *     <li>决策 {@code DENIED} → 直接返回，{@code result=1}，不调用模型</li>
 *     <li>决策 {@code MANUAL} → 返回并标记 {@code pendingConfirm}，{@code manualDecision=PENDING}</li>
 *     <li>决策 {@code MODEL} → 选 invoker 调用 → 写回 latency/tokens/cost</li>
 *     <li>输出不符合 {@code output_schema} → {@code result=1}，{@code errorSummary="输出不符合Schema"}</li>
 *     <li>模型原始输出<b>不写入任何业务事实表</b>，只返回给调用方与审计引用</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigInvokeServiceImpl implements IAigInvokeService {

    /**
     * 路由引擎。
     */
    private final IAigRouteService routeService;

    /**
     * 审计记录器（独立 Bean，REQUIRES_NEW，异常不外抛）。
     */
    private final AigAuditRecorder auditRecorder;

    /**
     * 可插拔调用器（SPI）。
     */
    private final List<ModelInvoker> invokers;

    /**
     * 模型主数据只读视图 Mapper（取 secretRef/endpoint 组装调用请求）。
     */
    private final AigModelViewMapper modelViewMapper;

    @Override
    public AigInvokeVo dryRun(AigInvokeBo bo) {
        AigDataLevelEnum dataLevel = parseDataLevel(bo);
        AigRouteDecision decision = routeService.decide(bo.getCapabilityCode(), dataLevel);
        // dryRun 只做决策预览，不产生审计记录，因此不生成 traceId
        return toVo(null, decision, null, decision.getReason());
    }

    @Override
    public AigInvokeVo invoke(AigInvokeBo bo) {
        // 1. 生成 traceId
        String traceId = IdUtil.fastSimpleUUID();
        AigDataLevelEnum dataLevel = parseDataLevel(bo);
        // 2. 路由决策（不抛异常）
        AigRouteDecision decision = routeService.decide(bo.getCapabilityCode(), dataLevel);
        // 3. 组装审计上下文；无论成败都在 finally 落库
        AigAuditContext audit = buildAuditContext(traceId, bo, dataLevel, decision);
        try {
            if (AigRouteDecisionEnum.DENIED.getCode().equals(decision.getDecision())) {
                // 4. 策略拒绝：不调用模型
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(decision.getReason());
                audit.setManualDecision(AigManualDecisionEnum.NOT_REQUIRED.getCode());
                return toVo(traceId, decision, null, decision.getReason());
            }
            if (AigRouteDecisionEnum.MANUAL.getCode().equals(decision.getDecision())) {
                // 5. 转人工：标记待确认，不调用模型
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(decision.getReason());
                audit.setManualDecision(AigManualDecisionEnum.PENDING.getCode());
                return toVo(traceId, decision, null, decision.getReason());
            }
            // 6. 决策为 MODEL → 选调用器执行
            AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(decision.getDeploymentType());
            ModelInvoker invoker = resolveInvoker(decision.getInvoker(), deployment);
            if (invoker == null) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary("无可用调用器");
                return toVo(traceId, decision, null, "路由命中模型但无可用调用器（invoker）");
            }
            AigModelVo model = modelViewMapper.selectModelById(decision.getModelId());
            ModelInvokeResult result = invoker.invoke(buildRequest(bo, decision, deployment, model));
            audit.setLatencyMs((int) Math.min(result.getLatencyMs(), Integer.MAX_VALUE));
            audit.setCost(result.getCost());
            audit.setModelVersion(result.getModelVersion());
            AigInvokeVo vo = toVo(traceId, decision, result.getLatencyMs(), null);
            if (!result.isSuccess()) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(StringUtils.blankToDefault(result.getErrorSummary(), "模型调用失败"));
                vo.setReason(StringUtils.blankToDefault(result.getErrorSummary(), "模型调用失败"));
                return vo;
            }
            // 7. 输出必须符合能力输出模板，否则不视为成功
            if (!AigOutputSchemaValidator.matches(decision.getOutputSchema(), result.getOutput())) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(AigOutputSchemaValidator.MISMATCH_MESSAGE);
                vo.setReason(AigOutputSchemaValidator.MISMATCH_MESSAGE);
                return vo;
            }
            audit.setResult(AigInvokeResultEnum.SUCCESS.getCode());
            // 8. 原始输出只返回给调用方；审计只留引用，不写输出副本
            vo.setOutput(result.getOutput());
            vo.setPendingConfirm(decision.getHumanConfirmPoints());
            return vo;
        } finally {
            auditRecorder.record(audit);
        }
    }

    /**
     * 解析数据等级；非法值直接抛参数异常（属于入参错误，不产生调用行为）。
     *
     * @param bo 调用入参
     * @return 数据等级
     */
    private AigDataLevelEnum parseDataLevel(AigInvokeBo bo) {
        if (bo == null) {
            throw new ServiceException("调用入参不能为空");
        }
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(bo.getDataLevel());
        if (dataLevel == null) {
            throw new ServiceException("非法的数据等级：" + bo.getDataLevel());
        }
        return dataLevel;
    }

    /**
     * 组装审计上下文。
     *
     * @param traceId   调用链ID
     * @param bo        调用入参
     * @param dataLevel 数据等级
     * @param decision  路由决策
     * @return 审计上下文
     */
    private AigAuditContext buildAuditContext(String traceId, AigInvokeBo bo, AigDataLevelEnum dataLevel,
                                              AigRouteDecision decision) {
        AigAuditContext audit = new AigAuditContext();
        audit.setTraceId(traceId);
        audit.setCapabilityCode(bo.getCapabilityCode());
        audit.setDataLevel(dataLevel.getCode());
        audit.setModelId(decision.getModelId());
        audit.setModelKey(decision.getModelKey());
        audit.setDeploymentType(decision.getDeploymentType());
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(decision.getDeploymentType());
        audit.setExternalCall(deployment != null && deployment.isExternal());
        audit.setPolicyHits(decision.getPolicyHits());
        audit.setAuditLevel(decision.getAuditLevel());
        audit.setOutputRef(decision.getModelId() == null ? null : "trace:" + traceId);
        // 仅用于计算哈希与字段名摘要；AigAuditRecorder 保证不写正文、不写个人资料
        audit.setPrompt(bo.getPrompt());
        audit.setPayload(bo.getPayload());
        return audit;
    }

    /**
     * 组装 SPI 调用请求。
     *
     * @param bo         调用入参
     * @param decision   路由决策
     * @param deployment 部署类型
     * @param model      模型主数据（可为 null）
     * @return 调用请求
     */
    private ModelInvokeRequest buildRequest(AigInvokeBo bo, AigRouteDecision decision,
                                            AigDeploymentTypeEnum deployment, AigModelVo model) {
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setCapabilityCode(bo.getCapabilityCode());
        request.setModelId(decision.getModelId());
        request.setModelKey(decision.getModelKey());
        request.setDeploymentType(deployment);
        request.setDataLevel(AigDataLevelEnum.find(bo.getDataLevel()));
        request.setPrompt(bo.getPrompt());
        request.setPayload(bo.getPayload());
        if (model != null) {
            request.setModelType(model.getModelType());
            request.setEndpoint(model.getApiEndpoint());
            request.setSecretRef(model.getSecretRef());
        }
        return request;
    }

    /**
     * 挑选调用器：优先按决策记录的 invoker 名称匹配，其次按部署类型匹配。
     *
     * @param invokerName 决策记录的调用器名称（可为 null）
     * @param deployment  部署类型
     * @return 可用调用器，找不到返回 null
     */
    private ModelInvoker resolveInvoker(String invokerName, AigDeploymentTypeEnum deployment) {
        if (invokers == null || deployment == null) {
            return null;
        }
        ModelInvoker fallback = null;
        for (ModelInvoker invoker : invokers) {
            if (!invoker.supports(deployment) || !invoker.available()) {
                continue;
            }
            if (StringUtils.isNotBlank(invokerName) && invokerName.equals(invoker.invokerName())) {
                return invoker;
            }
            if (fallback == null) {
                fallback = invoker;
            }
        }
        return fallback;
    }

    /**
     * 决策 → 返回视图。
     *
     * @param traceId   调用链ID（dryRun 为 null）
     * @param decision  路由决策
     * @param latencyMs 耗时
     * @param reason    原因
     * @return 返回视图
     */
    private AigInvokeVo toVo(String traceId, AigRouteDecision decision, Long latencyMs, String reason) {
        AigInvokeVo vo = new AigInvokeVo();
        vo.setTraceId(traceId);
        vo.setDecision(decision.getDecision());
        vo.setModelId(decision.getModelId());
        vo.setModelKey(decision.getModelKey());
        vo.setDeploymentType(decision.getDeploymentType());
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(decision.getDeploymentType());
        vo.setExternalCall(deployment != null && deployment.isExternal());
        vo.setLatencyMs(latencyMs);
        vo.setReason(StringUtils.blankToDefault(reason, decision.getReason()));
        vo.setPolicyHits(decision.getPolicyHits());
        vo.setPendingConfirm(decision.getHumanConfirmPoints());
        return vo;
    }

}
