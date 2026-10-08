package org.dromara.aigov.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
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
import org.dromara.aigov.service.IAigUserQuotaService;
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

    /**
     * 人均配额（C3）：按调用人计「调用次数」，自然日/自然月。
     *
     * <p>只在确实要调用模型之前判；命中上限则整笔拒绝（fail-closed）。</p>
     */
    private final IAigUserQuotaService quotaService;

    @Override
    public AigInvokeVo dryRun(AigInvokeBo bo) {
        AigDataLevelEnum dataLevel = parseDataLevel(bo);
        AigRouteDecision decision = routeService.decide(bo.getCapabilityCode(), dataLevel, routeHint(bo));
        // dryRun 只做决策预览，不产生审计记录，因此不生成 traceId
        return toVo(null, decision, null, decision.getReason());
    }

    @Override
    public AigInvokeVo invoke(AigInvokeBo bo) {
        // 1. 生成 traceId
        String traceId = IdUtil.fastSimpleUUID();
        AigDataLevelEnum dataLevel = parseDataLevel(bo);
        // 2. 路由决策（不抛异常）；带路由提示：场景命中强制绑定时候选收窄为指定供应商、
        //    声明了本次预算时排除单次上限高于预算的模型。两者都只收窄，不放宽治理口径。
        AigRouteDecision decision = routeService.decide(bo.getCapabilityCode(), dataLevel, routeHint(bo));
        // 3. 组装审计上下文；无论成败都在 finally 落库
        AigAuditContext audit = buildAuditContext(traceId, bo, dataLevel, decision);
        try {
            if (AigRouteDecisionEnum.DENIED.getCode().equals(decision.getDecision())) {
                // 4. 策略拒绝：不调用模型
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(decision.getReason());
                // 分类要落库：策略拒绝是「版本/配置不允许」这类严重错误，
                // 灰度的「无严重错误」判据必须看得见它，而不是只能从中文文案里猜
                audit.setErrorClass(AigErrorClassEnum.POLICY_DENIED.getCode());
                audit.setManualDecision(AigManualDecisionEnum.NOT_REQUIRED.getCode());
                return toVo(traceId, decision, null, decision.getReason(),
                    AigErrorClassEnum.POLICY_DENIED.getCode());
            }
            if (AigRouteDecisionEnum.MANUAL.getCode().equals(decision.getDecision())) {
                // 5. 转人工：标记待确认，不调用模型
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(decision.getReason());
                audit.setErrorClass(AigErrorClassEnum.POLICY_DENIED.getCode());
                audit.setManualDecision(AigManualDecisionEnum.PENDING.getCode());
                // 转人工同样归为「策略类、不可重试」：重试还是同一个结论，只会重复打扰人
                return toVo(traceId, decision, null, decision.getReason(),
                    AigErrorClassEnum.POLICY_DENIED.getCode());
            }
            // 6. 人均配额（C3）：只在「确实要调用模型」之前判。
            //    放在策略拒绝/转人工之后是刻意的：那两条分支不消耗额度，也不该被额度抢先拦截——
            //    否则人会看到「配额超了」，而真实原因是策略不允许（数据不能出域之类），
            //    两者该做的处置完全不同。
            //    调用人取自与审计行同一处解析（AigAuditRecorder#resolveCallerId），
            //    保证「被限额的人」与「账本上记的人」永远是同一个。
            quotaService.assertWithinQuota(AigAuditRecorder.resolveCallerId(audit));
            // 7. 决策为 MODEL → 按有序候选依次执行，失败则顺延（有序 fallback）
            List<AigRouteCandidate> candidates = decision.getCandidates();
            if (candidates.isEmpty()) {
                // 兼容：决策未携带候选列表（旧调用方或手工构造）时退化为「单一模型」路径
                AigRouteCandidate single = new AigRouteCandidate();
                single.setOrder(1);
                single.setModelId(decision.getModelId());
                single.setModelKey(decision.getModelKey());
                single.setDeploymentType(decision.getDeploymentType());
                single.setInvoker(decision.getInvoker());
                candidates = List.of(single);
            }
            int maxAttempts = retryProperties.isEnabled() ? Math.max(1, retryProperties.getMaxAttempts()) : 1;
            long startedAt = System.currentTimeMillis();
            int totalAttempts = 0;
            boolean executed = false;
            boolean succeeded = false;
            ModelInvokeResult result = null;
            AigErrorClassEnum errorClass = null;
            String errorSummary = null;
            for (int index = 0; index < candidates.size(); index++) {
                AigRouteCandidate candidate = candidates.get(index);
                boolean lastCandidate = index == candidates.size() - 1;
                // 用循环下标而不是 candidate.getOrder() 拼消息：order 是我们自己填的展示字段，
                // 外部构造的决策可能为 null，一旦参与算术就会 NPE 并把整次调用带崩。
                int orderNo = index + 1;
                // 决策与审计都指向「这次实际要试的候选」：fallback 之后真正跑的是备选模型，
                // 审计若仍记主候选就是一笔假账，比没有审计更坏。
                applyCandidate(decision, audit, candidate);
                AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(candidate.getDeploymentType());
                AigModelVo model = modelViewMapper.selectModelById(candidate.getModelId());
                // 供应商维度同样必须跟着「这次真正要试的候选」：审计漏了它，
                // 「这家供应商这个月花了多少、外发了多少次」就只能靠模型ID join 现查，
                // 而 join 出来的是今天的归属，不是当时那次的。
                audit.setProviderId(model == null ? null : model.getProviderId());
                ModelInvoker invoker = resolveInvoker(candidate.getInvoker(), deployment,
                    model == null ? null : model.getModelType());
                if (invoker == null) {
                    // 该候选没有可用调用器：跳过而不是整体失败——后面还有候选可试
                    audit.getPolicyHits().add("候选 #" + orderNo + "（" + candidate.getModelKey()
                        + "）无可用调用器，顺延下一候选");
                    continue;
                }
                executed = true;
                ModelInvokeRequest request = buildRequest(bo, decision, deployment, model);
                // 6.1 同一候选内退避重试：只对可重试类（限流/超时/不可用）生效
                int attempts = 0;
                errorClass = null;
                while (true) {
                    attempts++;
                    totalAttempts++;
                    result = invoker.invoke(request);
                    if (result.isSuccess()) {
                        break;
                    }
                    errorClass = safeClassify(invoker, result);
                    errorSummary = StringUtils.blankToDefault(result.getErrorSummary(), "模型调用失败");
                    if (!errorClass.isRetryable() || attempts >= maxAttempts) {
                        break;
                    }
                    long backoff = backoffMs(attempts);
                    // 退避原因写进 hits：否则事后只看「这次调用花了 3 秒」无法解释为什么
                    audit.getPolicyHits().add("候选 #" + orderNo + " 第 " + attempts
                        + " 次尝试失败（" + errorClass.getCode() + "：" + errorClass.getDesc() + "），"
                        + backoff + "ms 后重试第 " + (attempts + 1) + "/" + maxAttempts + " 次");
                    if (!sleepQuietly(backoff)) {
                        // 线程被中断：不再重试，按当前失败结果收敛
                        audit.getPolicyHits().add("重试等待被中断，按当前失败结果收敛");
                        break;
                    }
                }
                if (result.isSuccess()) {
                    // 7. 输出必须符合能力输出模板，否则不视为成功。
                    // 但**不重试同一模型**：同样的输入与提示词，重试只会得到同样的输出；
                    // 换一个模型才可能有帮助——故归类为「值得 fallback」的 OUTPUT_UNPARSABLE。
                    if (AigOutputSchemaValidator.matches(decision.getOutputSchema(), result.getOutput())) {
                        succeeded = true;
                        break;
                    }
                    errorClass = AigErrorClassEnum.OUTPUT_UNPARSABLE;
                    errorSummary = AigOutputSchemaValidator.MISMATCH_MESSAGE;
                    result = ModelInvokeResult.failure(AigErrorClassEnum.OUTPUT_UNPARSABLE.getCode(), null,
                        errorSummary, result.getLatencyMs());
                }
                // 6.2 候选级 fallback：只有「换个 Provider 可能有救」的错误才顺延。
                // 入参类错误（换谁都一样被拒）继续顺延，只是把同一个失败乘以候选数。
                if (!errorClass.isWorthFallback() || lastCandidate) {
                    break;
                }
                audit.getPolicyHits().add("候选 #" + orderNo + "（" + candidate.getModelKey()
                    + "）失败（" + errorClass.getCode() + "），按有序 fallback 顺延到候选 #"
                    + (orderNo + 1));
            }
            // 耗时按「端到端」记：含退避等待与多候选尝试，因为那是调用方真实等待的时间。
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (!executed) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary("无可用调用器");
                // 同一分类既要下发给上层，也要落进审计——两处不一致时，
                // 「按审计统计的严重错误」与「调用方看到的错误码」会各说各话
                audit.setErrorClass(AigErrorClassEnum.INVALID_REQUEST.getCode());
                return toVo(traceId, decision, elapsedMs, "路由命中模型但无可用调用器（invoker）",
                    AigErrorClassEnum.INVALID_REQUEST.getCode());
            }
            audit.setRetryCount(Math.max(0, totalAttempts - 1));
            audit.setLatencyMs((int) Math.min(elapsedMs, Integer.MAX_VALUE));
            audit.setCost(result.getCost());
            audit.setModelVersion(result.getModelVersion());
            // 用量回执：调用器解析出来的 tokens 此前只活在 ModelInvokeResult 里，
            // 出了这次方法调用就丢了——而「这家用了多少」是费用对账与限流的基础。
            audit.setUsageJson(buildUsageJson(result));
            AigInvokeVo vo = toVo(traceId, decision, elapsedMs, null);
            if (!succeeded) {
                audit.setResult(AigInvokeResultEnum.FAILED.getCode());
                audit.setErrorSummary(StringUtils.blankToDefault(errorSummary, "模型调用失败"));
                vo.setReason(StringUtils.blankToDefault(errorSummary, "模型调用失败"));
                if (errorClass == null) {
                    errorClass = AigErrorClassEnum.UNKNOWN;
                }
                // 把内部已算好的错误分类下发：上层要据此决定「重试/换候选/转人工/停在失败」，
                // 让它去解析 reason 文案等于把已确定的结论重新猜一遍（文案一改就错）
                vo.setErrorCode(errorClass.getCode());
                recordErrorClass(audit, decision, errorClass);
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
     * 把决策与审计都指向「本次实际要试的候选」。
     *
     * <p>为什么必须做：有序 fallback 之后真正执行的是<b>备选</b>模型。若审计
     * {@code model_id/model_key/deployment_type/external_call} 仍停留在主候选，
     * 事后回看审计会以为一直是主候选在跑——那是**假账**，比没有审计更坏
     * （尤其 {@code external_call}：主候选是本地、备选是外部时，假账会把「数据外发过」
     * 记成「没外发」）。</p>
     *
     * @param decision  决策对象（使 toVo 下发的模型/调用器与实际一致）
     * @param audit     审计上下文
     * @param candidate 本次要试的候选
     */
    private void applyCandidate(AigRouteDecision decision, AigAuditContext audit, AigRouteCandidate candidate) {
        decision.setModelId(candidate.getModelId());
        decision.setModelKey(candidate.getModelKey());
        decision.setDeploymentType(candidate.getDeploymentType());
        decision.setInvoker(candidate.getInvoker());
        audit.setModelId(candidate.getModelId());
        audit.setModelKey(candidate.getModelKey());
        audit.setDeploymentType(candidate.getDeploymentType());
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(candidate.getDeploymentType());
        audit.setExternalCall(deployment != null && deployment.isExternal());
    }

    /**
     * 把入参里的场景与预算组装成路由提示。
     *
     * @param bo 调用入参
     * @return 路由提示；两者都为空时返回 null（路由按「无提示」快路径处理）
     */
    private AigRouteHint routeHint(AigInvokeBo bo) {
        return AigRouteHint.of(bo.getScenarioCode(), bo.getMaxCost());
    }

    /**
     * 把调用器回填的用量组装成审计用的 JSON。
     *
     * <p><b>为什么手工拼而不走序列化器</b>：这里只可能是「若干个数字键值对」，
     * 没有字符串、没有嵌套、没有需要转义的内容，因此不存在拼接注入或转义遗漏的风险；
     * 而引入 {@code JsonMapper} 会让本类多一个构造参数，把「用量怎么序列化」这种
     * 边缘关注点渗进调用编排的装配里。真正的扩展点在列：不同供应商回执的用量字段
     * 差异很大，将来按供应商归一化时在这里补键即可。</p>
     *
     * <p>两个值都为空时返回 {@code null} 而不是 {@code {}}：图像模型普遍不回执 token，
     * 给每次出图都写一个空对象会把这一列变成噪音，也让「没有用量」和「用量为零」混为一谈。</p>
     *
     * @param result 最后一次尝试的结果（可为 null）
     * @return 用量 JSON，无任何用量时返回 null
     */
    private String buildUsageJson(ModelInvokeResult result) {
        if (result == null || (result.getTokensUsed() == null && result.getCost() == null)) {
            return null;
        }
        StringBuilder json = new StringBuilder(48).append('{');
        if (result.getTokensUsed() != null) {
            json.append("\"tokensUsed\":").append(result.getTokensUsed());
        }
        if (result.getCost() != null) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("\"cost\":").append(result.getCost().toPlainString());
        }
        return json.append('}').toString();
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
        // 机器可读的那一份落独立列：policy_hit 是给人看的文本、且会被截断到 255，
        // 而「有没有严重错误」的判据必须建立在不会被截掉的列上
        audit.setErrorClass(errorClass.getCode());
        audit.getPolicyHits().add("错误分类=" + errorClass.getCode() + "（" + errorClass.getDesc() + "）"
            + "，允许自动重试=" + (errorClass.isRetryable() ? "是" : "否")
            + "，需转人工=" + (errorClass.isNeedsHuman() ? "是" : "否"));
        if (errorClass.isNeedsHuman()) {
            // 设计 §13.3：不可重试的错误必须转人工，不能被当成「偶发失败」悄悄吞掉
            audit.setManualDecision(AigManualDecisionEnum.PENDING.getCode());
        }
        if (errorClass.isCircuitBreak()) {
            // 熔断不等于「鉴权失败」：额度/余额不足同样会熔断（继续调用必然同样失败）。
            // 文案按分类拼，不写死——否则额度问题会被记成「鉴权失败」，
            // 运维就会去找一把「更好的密钥」，而真正的动作在财务侧。
            log.error("Provider 调用建议熔断：分类={}（{}）, traceId={}, capability={}, modelKey={}, reason={}",
                errorClass.getCode(), errorClass.getDesc(), audit.getTraceId(), audit.getCapabilityCode(),
                decision.getModelKey(), audit.getErrorSummary());
            audit.getPolicyHits().add("熔断建议：" + errorClass.getDesc()
                + "（" + errorClass.getCode() + "），处置完成前不应继续调用该 Provider");
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
        audit.setScenarioCode(bo.getScenarioCode());
        audit.setAgentVersionId(bo.getAgentVersionId());
        audit.setInputSnapshotRef(bo.getInputSnapshotRef());
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
     * 挑选调用器：优先按决策记录的 invoker 名称匹配，其次按「部署类型 + 模型类型」匹配。
     *
     * <p>必须带 {@code modelType}：同一个 {@code EXTERNAL_API} 下对话模型与图像模型
     * 属于两个不同调用器，只按部署类型兜底会派错（或被无谓地抢走）。</p>
     *
     * @param invokerName 决策记录的调用器名称（可为 null）
     * @param deployment  部署类型
     * @param modelType   模型类型（{@code sai_model_config.model_type}，可为空）
     * @return 可用调用器，找不到返回 null
     */
    private ModelInvoker resolveInvoker(String invokerName, AigDeploymentTypeEnum deployment, String modelType) {
        if (invokers == null || deployment == null) {
            return null;
        }
        ModelInvoker fallback = null;
        for (ModelInvoker invoker : invokers) {
            if (!invoker.supports(deployment) || !invoker.available()
                || !invoker.supportsModelType(modelType)) {
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
        return toVo(traceId, decision, latencyMs, reason, null);
    }

    /**
     * 组装返回视图。
     *
     * @param traceId    调用链ID（dryRun 为空）
     * @param decision   路由决策
     * @param latencyMs  耗时（可空）
     * @param reason     原因（可空，取决策里的）
     * @param errorCode  错误分类编码（成功时为空）
     * @return 返回视图
     */
    private AigInvokeVo toVo(String traceId, AigRouteDecision decision, Long latencyMs, String reason,
                             String errorCode) {
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
        vo.setErrorCode(errorCode);
        return vo;
    }

}
