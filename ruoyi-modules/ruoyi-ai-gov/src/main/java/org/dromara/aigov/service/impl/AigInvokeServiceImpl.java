package org.dromara.aigov.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
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

    /**
     * 失败自动重试配置（只对可重试的错误分类生效）。
     */
    private final AigRetryProperties retryProperties;

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
            ModelInvokeRequest request = buildRequest(bo, decision, deployment, model);
            // 6.1 按错误分类决定是否退避重试：只对可重试类（限流/超时/不可用）生效
            long startedAt = System.currentTimeMillis();
            int maxAttempts = retryProperties.isEnabled() ? Math.max(1, retryProperties.getMaxAttempts()) : 1;
            ModelInvokeResult result;
            AigErrorClassEnum errorClass = null;
            int attempts = 0;
            while (true) {
                attempts++;
                result = invoker.invoke(request);
                if (result.isSuccess()) {
                    break;
                }
                errorClass = safeClassify(invoker, result);
                if (!errorClass.isRetryable() || attempts >= maxAttempts) {
                    break;
                }
                long backoff = backoffMs(attempts);
                // 退避原因写进 hits：否则事后只看「这次调用花了 3 秒」无法解释为什么
                audit.getPolicyHits().add("第 " + attempts + " 次尝试失败（" + errorClass.getCode()
                    + "：" + errorClass.getDesc() + "），" + backoff + "ms 后重试第 "
                    + (attempts + 1) + "/" + maxAttempts + " 次");
                if (!sleepQuietly(backoff)) {
                    // 线程被中断：不再重试，按当前失败结果收敛
                    audit.getPolicyHits().add("重试等待被中断，按当前失败结果收敛");
                    break;
                }
            }
            // 耗时按「端到端」记：含退避等待，因为那是调用方真实等待的时间。
            // 单次尝试的耗时留在 ModelInvokeResult 里，需要时可从 hits 与日志追溯。
            long elapsedMs = System.currentTimeMillis() - startedAt;
            audit.setRetryCount(attempts - 1);
            audit.setLatencyMs((int) Math.min(elapsedMs, Integer.MAX_VALUE));
            audit.setCost(result.getCost());
            audit.setModelVersion(result.getModelVersion());
            AigInvokeVo vo = toVo(traceId, decision, elapsedMs, null);
            if (!result.isSuccess()) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(StringUtils.blankToDefault(result.getErrorSummary(), "模型调用失败"));
                vo.setReason(StringUtils.blankToDefault(result.getErrorSummary(), "模型调用失败"));
                recordErrorClass(audit, decision, errorClass);
                return vo;
            }
            // 7. 输出必须符合能力输出模板，否则不视为成功
            if (!AigOutputSchemaValidator.matches(decision.getOutputSchema(), result.getOutput())) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(AigOutputSchemaValidator.MISMATCH_MESSAGE);
                vo.setReason(AigOutputSchemaValidator.MISMATCH_MESSAGE);
                // 「输出不符合 Schema」重试多少次都是同一个结果，必须转人工补正
                recordErrorClass(audit, decision, AigErrorClassEnum.INVALID_REQUEST);
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
     * 归类失败，并对「调用器返回 null」兜底。
     *
     * <p>为什么需要兜底：{@code classifyError} 是带默认实现的方法，但调用器可以覆写它。
     * 一个返回 null 的覆写（或单测里未打桩的 mock）会让后续取 {@code getCode()}
     * 直接 NPE——而「分类这一步失败」绝不该让一次模型调用以异常收场。
     * 退化成 UNKNOWN（不可重试、不自动转人工）是更安全的行为。</p>
     *
     * @param invoker 调用器
     * @param result  失败结果
     * @return 错误分类，恒不为 null
     */
    private AigErrorClassEnum safeClassify(ModelInvoker invoker, ModelInvokeResult result) {
        AigErrorClassEnum errorClass = invoker.classifyError(result);
        return errorClass == null ? AigErrorClassEnum.UNKNOWN : errorClass;
    }

    /**
     * 指数退避时长：base, base*2, base*4 … 且不超过上限。
     *
     * @param attempt 已失败的尝试次数（1 起）
     * @return 退避毫秒数
     */
    private long backoffMs(int attempt) {
        long base = Math.max(1L, retryProperties.getBaseBackoffMs());
        long ceiling = Math.max(base, retryProperties.getMaxBackoffMs());
        long shift = Math.min(attempt - 1, 16);
        long value = base << shift;
        return value <= 0 ? ceiling : Math.min(value, ceiling);
    }

    /**
     * 退避等待；不抛异常，改为返回是否完整睡完。
     *
     * @param millis 等待毫秒数
     * @return true 正常等待结束；false 线程在等待中被中断
     */
    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            // 恢复中断标记：吞掉中断信号会让上层再也感知不到取消请求
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 把错误分类落进审计，并据此决定处置。
     *
     * <p><b>刻意不改动 {@code reason} 文案</b>：内容域与创作域已有基于失败原因文本的
     * 判断与断言（例如 {@code cp_output_check.failure_reason} 会原样展示给用户），
     * 在这条链路上改文案会波及它们。分类信息写进 {@code policyHits}（调用方与审计都能看到），
     * 处置写进 {@code manualDecision}。</p>
     *
     * @param audit      审计上下文
     * @param decision   路由决策
     * @param errorClass 错误分类
     */
    private void recordErrorClass(AigAuditContext audit, AigRouteDecision decision, AigErrorClassEnum errorClass) {
        audit.getPolicyHits().add("错误分类=" + errorClass.getCode() + "（" + errorClass.getDesc() + "）"
            + "，允许自动重试=" + (errorClass.isRetryable() ? "是" : "否")
            + "，需转人工=" + (errorClass.isNeedsHuman() ? "是" : "否"));
        if (errorClass.isNeedsHuman()) {
            // 设计 §13.3：不可重试的错误必须转人工，不能被当成「偶发失败」悄悄吞掉
            audit.setManualDecision(AigManualDecisionEnum.PENDING.getCode());
        }
        if (errorClass.isCircuitBreak()) {
            // 鉴权失败：继续调用只会把账号打到风控，因此立刻告警并建议熔断
            log.error("Provider 鉴权失败，应立刻熔断该 Provider：traceId={}, capability={}, modelKey={}, reason={}",
                audit.getTraceId(), audit.getCapabilityCode(), decision.getModelKey(), audit.getErrorSummary());
            audit.getPolicyHits().add("熔断建议：鉴权失败，在密钥修正前不应继续调用该 Provider");
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
        // 输出模板必须带上：聊天类调用器要靠它提示模型「按这些字段输出 JSON」，
        // 否则拿到自由文本，这一步之后的 AigOutputSchemaValidator 必然判不合格。
        request.setOutputSchema(decision.getOutputSchema());
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
        // 调用器名要下发：部署类型只说明「哪一类模型」，真正执行的是哪个调用器
        // （直连供应商 vs 走集团 snail-ai）排障时必须一眼可见，否则只能靠猜。
        vo.setInvoker(decision.getInvoker());
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(decision.getDeploymentType());
        vo.setExternalCall(deployment != null && deployment.isExternal());
        vo.setLatencyMs(latencyMs);
        vo.setReason(StringUtils.blankToDefault(reason, decision.getReason()));
        vo.setPolicyHits(decision.getPolicyHits());
        vo.setPendingConfirm(decision.getHumanConfirmPoints());
        return vo;
    }

}
