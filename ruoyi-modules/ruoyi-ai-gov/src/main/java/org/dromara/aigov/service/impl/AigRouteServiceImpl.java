package org.dromara.aigov.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigRouteProperties;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigLifecycleStatusEnum;
import org.dromara.aigov.enums.AigProviderTypeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路由引擎实现。
 *
 * <p><b>决策算法（严格按 SPEC §5.2 的 7 步顺序）</b>，逐步落地位置见各步注释与小节方法：</p>
 * <ol>
 *     <li>{@code decide} 内「// 步骤1」：能力不存在或 status≠'0' → {@code DENIED}</li>
 *     <li>「// 步骤2」：无 {@code aig_route_policy(能力,数据等级)} → {@code DENIED}（默认拒绝）</li>
 *     <li>「// 步骤3」：{@code allowExternal='N'} 时候选模型仅限非外部部署</li>
 *     <li>「// 步骤4」：{@link #narrowByScenario} 若场景强制绑定了供应商，先把候选收窄到这些供应商</li>
 *     <li>「// 步骤4」：{@link #isCandidateUsable} 逐项校验生命周期 / 数据等级 / 启用 / 健康 / 外发</li>
 *     <li>「// 步骤5」：{@link #orderBindings} 按 PRIMARY→GRAY→FALLBACK 再按 priority 升序</li>
 *     <li>「// 步骤6」：无命中 → {@code fallbackToManual='Y'} 转 {@code MANUAL}，否则 {@code DENIED}</li>
 *     <li>「// 步骤7」：因 {@code allowExternal='N'} 被排除的外部模型写入 {@code policyHits}</li>
 * </ol>
 *
 * <p><b>本实现相对 SPEC 的两处加固</b>（都只收紧、不放松）：</p>
 * <ul>
 *     <li>数据等级 {@code STRICT} 时强制 {@code allowExternal=false}——外发禁令不依赖策略行是否正确；</li>
 *     <li>健康状态为 {@code DOWN} 的模型直接排除——但**只在明确 DOWN 时排除**，
 *         未测过(null)与 DEGRADED 一律放行，避免把「没测过」判成不可用。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigRouteServiceImpl implements IAigRouteService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 允许外发。
     */
    private static final String YES = "Y";

    /**
     * 健康检查结果：不可用（由连通性测试写入，见 AigModelGovernanceServiceImpl#testConnection）。
     */
    private static final String HEALTH_DOWN = "DOWN";

    /**
     * 能力模板 Mapper。
     */
    private final AigCapabilityMapper capabilityMapper;

    /**
     * 路由策略 Mapper。
     */
    private final AigRoutePolicyMapper routePolicyMapper;

    /**
     * 能力-模型绑定 Mapper。
     */
    private final AigCapabilityModelMapper capabilityModelMapper;

    /**
     * 模型治理属性 Mapper。
     */
    private final AigModelGovernanceMapper modelGovernanceMapper;

    /**
     * 模型主数据只读视图 Mapper。
     */
    private final AigModelViewMapper modelViewMapper;

    /**
     * 场景强制绑定 Mapper（设计 §4.4 步骤 4）。
     */
    private final AigRouteScenarioBindingMapper bindingMapper;

    /**
     * 可插拔调用器（SPI）。
     */
    private final List<ModelInvoker> invokers;

    /**
     * 路由行为配置（能力标签是否必须声明）。
     */
    private final AigRouteProperties routeProperties;

    @Override
    public AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(capabilityCode);
        try {
            return doDecide(decision, capabilityCode, dataLevel, scenarioCode);
        } catch (Exception e) {
            // 路由引擎不抛异常：任何异常都以 DENIED 表达
            log.error("路由决策异常, capabilityCode={}, dataLevel={}, scenarioCode={}", capabilityCode,
                dataLevel == null ? null : dataLevel.getCode(), scenarioCode, e);
            decision.addHit("路由决策异常：" + e.getClass().getSimpleName());
            return denied(decision, "路由决策异常，已按拒绝处理");
        }
    }

    @Override
    public List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode) {
        AigRouteDecision decision = decide(capabilityCode, dataLevel, scenarioCode);
        List<String> lines = new ArrayList<>();
        lines.add("决策=" + decision.getDecision()
            + "，modelId=" + decision.getModelId()
            + "，deploymentType=" + decision.getDeploymentType()
            + "，invoker=" + decision.getInvoker()
            + "，说明=" + decision.getReason());
        lines.addAll(decision.getPolicyHits());
        return lines;
    }

    /**
     * 决策主流程（SPEC §5.2 七步）。
     *
     * @param decision       决策对象
     * @param capabilityCode 能力编码
     * @param dataLevel      数据等级
     * @param scenarioCode   场景编码（可为空，表示不做场景强制绑定收窄）
     * @return 决策结果
     */
    private AigRouteDecision doDecide(AigRouteDecision decision, String capabilityCode, AigDataLevelEnum dataLevel,
                                     String scenarioCode) {
        // 步骤1：能力必须存在且 status='0' → 否则 DENIED
        if (StringUtils.isBlank(capabilityCode)) {
            return denied(decision, "能力不存在或已停用");
        }
        AigCapability capability = capabilityMapper.selectOne(new LambdaQueryWrapper<AigCapability>()
            .eq(AigCapability::getCapabilityCode, capabilityCode));
        if (capability == null || !STATUS_NORMAL.equals(capability.getStatus())) {
            decision.addHit("能力编码=" + capabilityCode + " 不存在或已停用");
            return denied(decision, "能力不存在或已停用");
        }
        decision.setCapabilityId(capability.getCapabilityId());
        decision.setAuditLevel(capability.getAuditLevel());
        decision.setOutputSchema(capability.getOutputSchema());
        decision.setHumanConfirmPoints(splitConfirmPoints(capability.getHumanConfirmPoints()));
        decision.addHit("能力=" + capabilityCode + "（" + capability.getCapabilityName() + "）存在且启用");
        if (dataLevel == null) {
            return denied(decision, "非法的数据等级");
        }

        // 步骤2：取 aig_route_policy(capability, dataLevel)；无策略 → DENIED（默认拒绝，不默认放行）
        AigRoutePolicy policy = routePolicyMapper.selectOne(new LambdaQueryWrapper<AigRoutePolicy>()
            .eq(AigRoutePolicy::getCapabilityCode, capabilityCode)
            .eq(AigRoutePolicy::getDataLevel, dataLevel.getCode())
            .eq(AigRoutePolicy::getStatus, STATUS_NORMAL));
        if (policy == null) {
            decision.addHit("未配置 能力=" + capabilityCode + " × 数据等级=" + dataLevel.getCode() + " 的路由策略");
            return denied(decision, "未配置该数据等级的路由策略");
        }
        boolean allowExternal = YES.equalsIgnoreCase(policy.getAllowExternal());
        boolean fallbackToManual = YES.equalsIgnoreCase(policy.getFallbackToManual());
        // 严格级数据（STRICT）硬约束：任何路由策略都不允许外发。
        // 刻意放在这里而不是只靠 isCandidateUsable 的 allowExternal 判断——
        // 外发禁令不该依赖某一行策略配置是否正确：策略写错也必须拦得住。
        if (dataLevel.externalForbidden()) {
            if (allowExternal) {
                decision.addHit("数据等级=" + dataLevel.getCode()
                    + " 为严格级：策略 allowExternal='Y' 被忽略，本次仅允许非外部部署模型");
            } else {
                decision.addHit("数据等级=" + dataLevel.getCode() + " 为严格级：禁止外发");
            }
            allowExternal = false;
        }
        decision.addHit("命中路由策略 policyId=" + policy.getPolicyId()
            + "，数据等级=" + dataLevel.getCode()
            + "，preferredDeployment=" + StringUtils.blankToDefault(policy.getPreferredDeployment(), "-")
            + "，allowExternal=" + policy.getAllowExternal()
            + "，requireApproval=" + policy.getRequireApproval()
            + "，fallbackToManual=" + policy.getFallbackToManual());

        // 步骤3 + 步骤5：候选绑定按 PRIMARY → GRAY → FALLBACK，再按 priority 升序
        List<AigCapabilityModel> bindings = capabilityModelMapper.selectList(new LambdaQueryWrapper<AigCapabilityModel>()
            .eq(AigCapabilityModel::getCapabilityCode, capabilityCode)
            .eq(AigCapabilityModel::getStatus, STATUS_NORMAL));
        List<AigCapabilityModel> ordered = orderBindings(bindings);
        if (ordered.isEmpty()) {
            decision.addHit("能力=" + capabilityCode + " 未绑定任何启用状态的模型");
            return noModel(decision, fallbackToManual);
        }
        Map<Long, AigModelVo> modelMap = loadModels(ordered);
        Map<Long, AigModelGovernance> governanceMap = loadGovernance(ordered);
        // 步骤4（设计 §4.4）：场景强制绑定 Provider —— 命中则把候选收窄为「仅指定供应商」。
        // 放在逐项校验之前，是为了让「被场景排除」与「被治理排除」在 policyHits 里分成两类原因：
        // 前者是业务约定，后者是安全/能力约束，混在一起会让排障时要读完整串才判断得出。
        List<AigCapabilityModel> narrowed = narrowByScenario(decision, ordered, modelMap, scenarioCode, capabilityCode);
        if (narrowed.isEmpty()) {
            // ordered 此处必非空（上方已判空返回），故收窄后为空只可能是场景绑定筛掉的。
            // 必须在这里收敛：若继续往下走，说明会退化成「能力未绑定任何模型」，
            // 让人以为是没配绑定，而真实原因是场景把候选全排除了——方向完全不同的两个问题。
            decision.addHit("场景强制绑定后无可用候选，按策略收敛（fallbackToManual=" + fallbackToManual + "）");
            return noModel(decision, fallbackToManual);
        }
        ordered = narrowed;
        if (!allowExternal) {
            // 步骤3：候选模型仅限 deploymentType ∈ {LOCAL, GROUP}
            decision.addHit("策略 allowExternal='N'：本次调用仅允许本地/集团共享部署模型，外部部署模型将被排除");
        }

        // 步骤4：逐项校验候选模型可用性；步骤7：记录因 allowExternal='N' 被排除的外部模型
        // 注意：这里**不再命中即返回**，而是收集「全部可用候选」形成有序 fallback 链。
        // 主候选调用失败时，调用编排按这个顺序顺延（见 AigRouteCandidate）。
        for (AigCapabilityModel binding : ordered) {
            AigModelVo model = modelMap.get(binding.getModelId());
            AigModelGovernance governance = governanceMap.get(binding.getModelId());
            AigDeploymentTypeEnum deployment = isCandidateUsable(decision, binding, model, governance, dataLevel,
                allowExternal, capability.getRequiredTags());
            if (deployment == null) {
                continue;
            }
            // 派发按「部署类型 + 模型类型」双维度：同一个 EXTERNAL_API 下可能既有对话模型
            // （/chat/completions）又有图像模型（/images/generations），只看部署类型会派错。
            ModelInvoker invoker = resolveInvoker(deployment, capabilityCode, model.getModelType());
            boolean primary = decision.getCandidates().isEmpty();
            AigRouteCandidate candidate = buildCandidate(decision, binding, model, deployment, invoker);
            decision.getCandidates().add(candidate);
            if (!primary) {
                // 备选候选只留一行摘要：主候选那段详细说明已够排障，
                // 每个候选都铺一遍会把 policyHits 撑成读不下去的一坨。
                decision.addHit("备选候选 #" + candidate.getOrder()
                    + "：modelId=" + candidate.getModelId()
                    + "，modelKey=" + candidate.getModelKey()
                    + "，modelType=" + StringUtils.blankToDefault(candidate.getModelType(), "-")
                    + "，usageType=" + candidate.getUsageType()
                    + "，invoker=" + StringUtils.blankToDefault(candidate.getInvoker(), "(未找到)")
                    + "——主候选重试耗尽或不可恢复时按序顺延");
                continue;
            }
            // 步骤5 命中第一个可用模型：保持既有顶层字段与说明的形态（dryRun/审计/前端都读它们）
            decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
            decision.setModelId(binding.getModelId());
            decision.setModelKey(model.getModelKey());
            decision.setDeploymentType(deployment.getCode());
            decision.setReason("命中模型 " + model.getModelKey() + "（" + deployment.getDesc() + "）");
            decision.addHit("选中模型：modelId=" + binding.getModelId()
                + "，modelKey=" + model.getModelKey()
                + "，modelType=" + StringUtils.blankToDefault(model.getModelType(), "-")
                + "，usageType=" + binding.getUsageType()
                + "，priority=" + binding.getPriority()
                + "，deploymentType=" + deployment.getCode()
                + "，dataLevelMax=" + governance.getDataLevelMax());
            if (invoker == null) {
                decision.addHit("未找到同时支持部署类型 " + deployment.getCode() + " 与模型类型 "
                    + StringUtils.blankToDefault(model.getModelType(), "(空)") + " 的可用调用器（invoker）");
            } else {
                decision.setInvoker(invoker.invokerName());
                // providerType 是可覆写的 SPI 方法，覆写有缺陷时可能返回 null；
                // 若在这里直接 .getCode() 会 NPE，而 decide() 会把异常收敛成 DENIED——
                // 表现是「候选模型明明可用却整条路由被拒」，最难查。故显式兜底。
                AigProviderTypeEnum providerType = invoker.providerType();
                decision.addHit("调用器=" + invoker.invokerName()
                    + "，Provider类型=" + (providerType == null
                    ? AigProviderTypeEnum.UNKNOWN.getCode() : providerType.getCode()));
                if (deployment == AigDeploymentTypeEnum.EXTERNAL_API) {
                    // EXTERNAL_API 由治理层直连供应商端点：登记了什么就用什么，
                    // 不存在「实际模型由别人决定」这回事，提示必须说清楚。
                    decision.addHit("外部 API：按治理台登记的端点与模型标识直连供应商");
                } else if (deployment == AigDeploymentTypeEnum.GROUP
                    || deployment == AigDeploymentTypeEnum.EXTERNAL_ENTERPRISE) {
                    // 明确提示：走 snail-ai 时实际执行的模型由 Agent 决定
                    decision.addHit("集团共享/外部企业服务经 snail-ai Agent 执行，实际模型由 Agent 决定，"
                        + "治理层仅判定「该模型是否可用」");
                }
            }
        }
        if (!decision.getCandidates().isEmpty()) {
            return decision;
        }

        // 步骤6：无命中 → fallbackToManual='Y' → MANUAL，否则 DENIED
        return noModel(decision, fallbackToManual);
    }

    /**
     * 步骤4：场景强制绑定 Provider（设计 §4.4 第 4 步）。
     *
     * <p><b>它只做减法</b>：把不属于指定供应商的候选从列表里去掉，<b>不新增</b>任何候选，
     * 也不放宽任何治理条件。这意味着「绑定配错」的最坏后果是
     * 「该场景无模型可用 → 转人工/拒绝」，而不会变成「数据被发给了不该发的地方」——
     * 一个配置项如果错了就能把数据发出去，无论写多少文档都拦不住误操作。</p>
     *
     * <p><b>三种「不生效」都被显式记录</b>，因为这正是「配了以为生效、其实没生效」的高发区：</p>
     * <ul>
     *     <li>场景为空 → 不产生提示（没有场景概念的调用占绝大多数，刷一行无意义提示会淹没有效信息）；</li>
     *     <li>该「场景 × 能力」没有绑定行 → 记录一行「未配置强制绑定，供应商不受场景限制」；</li>
     *     <li>有绑定行但都没写供应商 → 记录「不做收窄，请补全配置」，
     *         <b>刻意不</b>解释成「允许零个供应商」：那会把这个场景的调用全部掐死，
     *         而管理员以为自己只是填漏了一格。</li>
     * </ul>
     *
     * @param decision       决策对象（写入收窄说明与逐条排除原因）
     * @param ordered        已按用途/优先级排好序的候选绑定
     * @param modelMap       modelId → 模型主数据
     * @param scenarioCode   本次场景编码（可空）
     * @param capabilityCode 能力编码
     * @return 收窄后的候选绑定（可能为空，表示全被场景排除）
     */
    private List<AigCapabilityModel> narrowByScenario(AigRouteDecision decision, List<AigCapabilityModel> ordered,
                                                      Map<Long, AigModelVo> modelMap, String scenarioCode,
                                                      String capabilityCode) {
        if (StringUtils.isBlank(scenarioCode)) {
            return ordered;
        }
        String scenario = scenarioCode.trim().toUpperCase();
        List<AigRouteScenarioBinding> bindings = bindingMapper.selectList(
            new LambdaQueryWrapper<AigRouteScenarioBinding>()
                .eq(AigRouteScenarioBinding::getScenarioCode, scenario)
                .eq(AigRouteScenarioBinding::getCapabilityCode, capabilityCode)
                .eq(AigRouteScenarioBinding::getStatus, STATUS_NORMAL));
        if (CollUtil.isEmpty(bindings)) {
            decision.addHit("场景=" + scenario + " 未配置强制绑定，供应商不受场景限制");
            return ordered;
        }
        Set<Long> allowed = new LinkedHashSet<>();
        for (AigRouteScenarioBinding binding : bindings) {
            if (binding != null && binding.getProviderId() != null) {
                allowed.add(binding.getProviderId());
            }
        }
        if (allowed.isEmpty()) {
            decision.addHit("场景=" + scenario + " 的强制绑定未指定供应商，本次不做场景收窄；"
                + "请补全配置（按当前口径，配置不全等同于未配置，而不是「不允许任何供应商」）");
            return ordered;
        }
        decision.addHit("场景=" + scenario + " 强制绑定供应商 " + allowed
            + "：仅保留这些供应商下的候选，其余候选将被排除");
        List<AigCapabilityModel> narrowed = new ArrayList<>();
        for (AigCapabilityModel binding : ordered) {
            AigModelVo model = modelMap.get(binding.getModelId());
            if (model == null) {
                // 模型主数据缺失时无法判定它属于哪家：原样保留，让后续校验给出
                // 「sai_model_config 不存在」这个准确原因，而不是伪造一个供应商不匹配的理由
                narrowed.add(binding);
                continue;
            }
            Long providerId = model.getProviderId();
            if (providerId != null && allowed.contains(providerId)) {
                narrowed.add(binding);
                continue;
            }
            decision.addHit("排除 modelId=" + binding.getModelId()
                + "（modelKey=" + model.getModelKey() + "，供应商=" + providerId
                + "）：场景=" + scenario + " 强制绑定，仅允许供应商 " + allowed);
        }
        return narrowed;
    }

    /**
     * 由绑定与模型组装一个有序候选。
     *
     * @param decision   决策对象（用其候选数量推导序号）
     * @param binding    绑定记录
     * @param model      模型主数据
     * @param deployment 部署类型
     * @param invoker    决策时解析出的调用器（可为 null）
     * @return 候选
     */
    private AigRouteCandidate buildCandidate(AigRouteDecision decision, AigCapabilityModel binding,
                                             AigModelVo model, AigDeploymentTypeEnum deployment,
                                             ModelInvoker invoker) {
        AigRouteCandidate candidate = new AigRouteCandidate();
        candidate.setOrder(decision.getCandidates().size() + 1);
        candidate.setModelId(binding.getModelId());
        candidate.setModelKey(model.getModelKey());
        candidate.setModelType(model.getModelType());
        candidate.setDeploymentType(deployment.getCode());
        candidate.setUsageType(binding.getUsageType());
        candidate.setPriority(binding.getPriority());
        candidate.setInvoker(invoker == null ? null : invoker.invokerName());
        return candidate;
    }

    /**
     * 步骤4：判定单个候选模型是否可用；不可用则记录原因并返回 null。
     *
     * <p>校验项（全部通过才可用）：</p>
     * <ol>
     *     <li>治理属性存在（{@code aig_model_governance}）</li>
     *     <li>{@code lifecycle_status} 必须 {@code callable()}</li>
     *     <li>{@code data_level_max.rank() >= 本次 dataLevel.rank()} ← 核心：模型等级不够则不可用</li>
     *     <li>{@code sai_model_config.is_enabled = 1}</li>
     *     <li>健康状态非明确 {@code DOWN}（DOWN 才拦，未测过/降级放行）</li>
     *     <li>部署类型 {@code external()==false} 或 {@code policy.allowExternal=='Y'}</li>
     *     <li>能力标签覆盖 {@code requiredTags}（未声明默认放行并提示，见 {@link #isTagMismatch}）</li>
     * </ol>
     *
     * @param decision      决策对象（写入过滤原因）
     * @param binding       绑定记录
     * @param model         模型主数据（可为 null）
     * @param governance    治理属性（可为 null）
     * @param dataLevel     本次数据等级
     * @param allowExternal 策略是否允许外发
     * @param requiredTags  能力要求的能力标签（逗号分隔，可空）
     * @return 可用的部署类型；不可用返回 null
     */
    private AigDeploymentTypeEnum isCandidateUsable(AigRouteDecision decision, AigCapabilityModel binding,
                                                    AigModelVo model, AigModelGovernance governance,
                                                    AigDataLevelEnum dataLevel, boolean allowExternal,
                                                    String requiredTags) {
        String modelLabel = "modelId=" + binding.getModelId();
        if (governance == null) {
            decision.addHit("排除 " + modelLabel + "：未登记治理属性（aig_model_governance）");
            return null;
        }
        // 4.1 生命周期必须可调用
        AigLifecycleStatusEnum lifecycle = AigLifecycleStatusEnum.find(governance.getLifecycleStatus());
        if (lifecycle == null || !lifecycle.isCallable()) {
            decision.addHit("排除 " + modelLabel + "：生命周期=" + governance.getLifecycleStatus() + " 不可调用");
            return null;
        }
        // 4.2 模型允许的最高数据等级必须 >= 本次数据等级（核心）
        AigDataLevelEnum levelMax = AigDataLevelEnum.find(governance.getDataLevelMax());
        if (levelMax == null || levelMax.getRank() < dataLevel.getRank()) {
            decision.addHit("排除 " + modelLabel + "：允许最高数据等级=" + governance.getDataLevelMax()
                + " 低于本次数据等级=" + dataLevel.getCode());
            return null;
        }
        if (model == null) {
            decision.addHit("排除 " + modelLabel + "：模型主数据（sai_model_config）不存在");
            return null;
        }
        // 4.3 模型必须启用
        if (!Boolean.TRUE.equals(model.getIsEnabled())) {
            decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey() + "）：模型已停用");
            return null;
        }
        AigDeploymentTypeEnum deployment = AigDeploymentTypeEnum.find(governance.getDeploymentType());
        if (deployment == null) {
            decision.addHit("排除 " + modelLabel + "：部署类型非法（" + governance.getDeploymentType() + "）");
            return null;
        }
        // 4.5 健康状态：**只在明确 DOWN 时排除**。
        // 为什么不是「要求 UP」：health_status 由连通性测试（人工点一下）写入，
        // 绝大多数模型从未测过（null）。若要求 UP，等于把「没测过」判成不可用，
        // 会让所有既有路由瞬间失效——那是比「可能调到坏模型」严重得多的回归。
        // 因此口径是：测出 DOWN 才拦，未知(DEGRADED/null)一律放行。
        if (HEALTH_DOWN.equalsIgnoreCase(governance.getHealthStatus())) {
            decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey()
                + "）：最近一次健康检查为 DOWN（healthTime=" + governance.getHealthTime() + "），"
                + "暂不参与路由，避免把预算花在已知不可用的模型上");
            return null;
        }
        // 4.4 / 步骤3 / 步骤7：allowExternal='N' 时排除外部部署类型，并记录该原因
        if (!allowExternal && deployment.isExternal()) {
            decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey() + "）：策略 allowExternal='N'，"
                + "该模型部署类型=" + deployment.getCode() + " 属于外部调用，数据外发被策略禁止");
            return null;
        }
        // 4.6 能力标签匹配：模型声明的能力标签必须覆盖能力要求的标签
        if (isTagMismatch(decision, requiredTags, governance, model)) {
            return null;
        }
        return deployment;
    }

    /**
     * 4.6 能力标签匹配。
     *
     * <p><b>它修的是什么</b>：{@code aig_capability.required_tags}（如 {@code 'VISION'}）此前
     * 一直被登记却**从不与模型比对**，因为模型侧根本没有「我支持什么能力」这一列。
     * 后果不是"少了个校验"，而是<b>静默出错</b>——把纯文本模型绑到「看图」能力上，
     * 路由判 {@code MODEL} 并"成功返回"，产出的却是一份没看过图的结论，
     * 外形与真实结论一模一样，事后无法区分。</p>
     *
     * <p><b>为什么未声明默认放行</b>：{@code capability_tags} 是新列，既有模型全为 NULL。
     * 若默认严格，一次上线会把所有既有模型同时排除干净。因此：
     * 未声明 → 放行 + 写入可见提示；配 {@code aigov.route.require-model-tags=true} 后严格排除。</p>
     *
     * <p><b>为什么不从 {@code model_type} 推导标签</b>：多模态对话模型（如 gpt-4o）在
     * {@code model_type} 上同样是 {@code CHAT}，推导成 TEXT 会被「看图」能力<b>假排除</b>——
     * 把本来能用的模型挡在外面，比漏拦更难查。标签只能显式声明。</p>
     *
     * @param decision     决策对象（写入提示或排除原因）
     * @param requiredTags 能力要求的能力标签（逗号分隔，可空）
     * @param governance   治理属性（读模型声明的标签）
     * @param model        模型主数据（用于可读提示）
     * @return 应排除返回 true
     */
    private boolean isTagMismatch(AigRouteDecision decision, String requiredTags,
                                  AigModelGovernance governance, AigModelVo model) {
        List<String> required = splitTags(requiredTags);
        if (required.isEmpty()) {
            // 能力没要求标签 → 无从比对，不产生任何提示（避免每行决策都刷无意义的噪音）
            return false;
        }
        String modelLabel = "modelId=" + model.getModelId();
        List<String> declared = splitTags(governance.getCapabilityTags());
        if (declared.isEmpty()) {
            if (routeProperties.isRequireModelTags()) {
                decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey()
                    + "）：能力要求能力标签 " + required + "，但模型**未声明** capability_tags，"
                    + "且 aigov.route.require-model-tags=true（严格模式不允许未声明）");
                return true;
            }
            decision.addHit("注意 " + modelLabel + "（modelKey=" + model.getModelKey()
                + "）：能力要求能力标签 " + required + "，但模型未声明 capability_tags，"
                + "**无法校验是否具备该能力**（当前放行）。建议在治理台补齐该模型的 capability_tags；"
                + "补齐后可开 aigov.route.require-model-tags=true 彻底拦掉不匹配的绑定");
            return false;
        }
        List<String> missing = new ArrayList<>();
        for (String tag : required) {
            boolean hit = declared.stream().anyMatch(item -> item.equalsIgnoreCase(tag));
            if (!hit) {
                missing.add(tag);
            }
        }
        if (missing.isEmpty()) {
            return false;
        }
        decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey()
            + "）：能力要求能力标签 " + required + "，模型只声明了 " + declared
            + "，缺少 " + missing + "。把模型绑到它不具备的能力上会产出看起来正常的错误结果，"
            + "故在此拦下");
        return true;
    }

    /**
     * 解析能力标签：逗号/分号/顿号分隔，去空白与空项，保留原始大小写（比对时忽略大小写）。
     *
     * @param raw 原始文本（可空）
     * @return 标签列表，无内容返回空列表
     */
    private List<String> splitTags(String raw) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return tags;
        }
        for (String item : raw.split("[,;，；、]")) {
            String tag = item.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * 步骤5：绑定排序 —— PRIMARY 优先 → GRAY → FALLBACK，同级按 priority 升序。
     *
     * @param bindings 绑定列表
     * @return 排序后的绑定列表
     */
    private List<AigCapabilityModel> orderBindings(List<AigCapabilityModel> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        List<AigCapabilityModel> ordered = new ArrayList<>(bindings);
        ordered.sort(Comparator
            .comparingInt((AigCapabilityModel item) -> usageOrder(item.getUsageType()))
            .thenComparingInt(item -> item.getPriority() == null ? Integer.MAX_VALUE : item.getPriority())
            .thenComparing(AigCapabilityModel::getBindId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }

    /**
     * 用途排序权重：PRIMARY(0) → GRAY(1) → FALLBACK(2) → 其它(3)。
     *
     * @param usageType 用途
     * @return 排序权重
     */
    private int usageOrder(String usageType) {
        AigUsageTypeEnum usage = AigUsageTypeEnum.find(usageType);
        if (usage == null) {
            return 3;
        }
        return switch (usage) {
            case PRIMARY -> 0;
            case GRAY -> 1;
            case FALLBACK -> 2;
        };
    }

    /**
     * 步骤6：无可用模型时的收敛。
     *
     * @param decision         决策对象
     * @param fallbackToManual 策略是否允许转人工
     * @return 决策结果
     */
    private AigRouteDecision noModel(AigRouteDecision decision, boolean fallbackToManual) {
        if (fallbackToManual) {
            decision.setDecision(AigRouteDecisionEnum.MANUAL.getCode());
            decision.setReason("无可用模型，转人工");
            decision.addHit("无可用模型，策略 fallbackToManual='Y'，转人工待办（manualDecision=PENDING）");
            return decision;
        }
        decision.addHit("无可用模型，策略 fallbackToManual='N'，拒绝调用");
        return denied(decision, "无可用模型且未允许转人工");
    }

    /**
     * 装载候选模型主数据（按 modelId 索引）。
     *
     * @param bindings 绑定列表
     * @return modelId → 模型视图
     */
    private Map<Long, AigModelVo> loadModels(List<AigCapabilityModel> bindings) {
        List<Long> modelIds = new ArrayList<>();
        for (AigCapabilityModel binding : bindings) {
            if (binding.getModelId() != null && !modelIds.contains(binding.getModelId())) {
                modelIds.add(binding.getModelId());
            }
        }
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        List<AigModelVo> models = modelViewMapper.selectModelListByIds(modelIds);
        Map<Long, AigModelVo> map = new LinkedHashMap<>();
        if (models != null) {
            for (AigModelVo model : models) {
                if (model != null && model.getModelId() != null) {
                    map.put(model.getModelId(), model);
                }
            }
        }
        return map;
    }

    /**
     * 装载候选模型的治理属性（按 modelId 索引）。
     *
     * @param bindings 绑定列表
     * @return modelId → 治理属性
     */
    private Map<Long, AigModelGovernance> loadGovernance(List<AigCapabilityModel> bindings) {
        List<Long> modelIds = new ArrayList<>();
        for (AigCapabilityModel binding : bindings) {
            if (binding.getModelId() != null && !modelIds.contains(binding.getModelId())) {
                modelIds.add(binding.getModelId());
            }
        }
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        List<AigModelGovernance> list = modelGovernanceMapper.selectList(new LambdaQueryWrapper<AigModelGovernance>()
            .in(AigModelGovernance::getModelId, modelIds));
        Map<Long, AigModelGovernance> map = new LinkedHashMap<>();
        if (list != null) {
            for (AigModelGovernance item : list) {
                if (item != null && item.getModelId() != null) {
                    map.put(item.getModelId(), item);
                }
            }
        }
        return map;
    }

    /**
     * 挑选可用调用器：**先按能力精配，再按部署类型兜底**。
     *
     * <p>为什么必须两段式：同一种部署类型（如 {@code LOCAL}）下存在多个调用器，
     * 分别实现不同业务能力（人才匹配、资料解析、资料预检…）。若只看部署类型，
     * 只能取「列表里第一个可用的」，结果取决于 Spring Bean 装配顺序——不确定，
     * 会出现「资料解析被派给了人才匹配的调用器」这类错配。</p>
     *
     * <p>为什么还要看 <b>模型类型</b>：同一个 {@code EXTERNAL_API} 下可能既有对话模型
     * （{@code /chat/completions}）又有图像模型（{@code /images/generations}）——
     * 端点和请求体都不同。只看部署类型会让二者互相抢，而「模型类型」是数据库里
     * 真实存在的 {@code sai_model_config.model_type}，据此派发既确定又准确。</p>
     *
     * <p><b>刻意没有「忽略模型类型」的第三段兜底</b>：那样会让图像调用器抓到对话模型。
     * 模型类型不匹配时如实返回 {@code null}，由调用方给出「无可用调用器」的明确失败。</p>
     *
     * @param deployment     部署类型
     * @param capabilityCode 业务能力编码
     * @param modelType      模型类型（{@code sai_model_config.model_type}，可为空）
     * @return 可用调用器，找不到返回 null
     */
    private ModelInvoker resolveInvoker(AigDeploymentTypeEnum deployment, String capabilityCode, String modelType) {
        if (invokers == null) {
            return null;
        }
        // 第一段：声明处理该能力 且 认领该模型类型的调用器优先
        for (ModelInvoker invoker : invokers) {
            if (invoker.supports(deployment) && invoker.available()
                && invoker.supportsCapability(capabilityCode)
                && invoker.supportsModelType(modelType)) {
                return invoker;
            }
        }
        // 第二段：兜底——只按部署类型 + 模型类型匹配（snail-ai 等通用调用器走这里）
        for (ModelInvoker invoker : invokers) {
            if (invoker.supports(deployment) && invoker.available()
                && invoker.supportsModelType(modelType)) {
                return invoker;
            }
        }
        return null;
    }

    /**
     * 构造拒绝结论。
     *
     * @param decision 决策对象
     * @param reason   原因
     * @return 决策结果
     */
    private AigRouteDecision denied(AigRouteDecision decision, String reason) {
        decision.setDecision(AigRouteDecisionEnum.DENIED.getCode());
        decision.setReason(reason);
        return decision;
    }

    /**
     * 解析人工确认点（逗号/分号/顿号分隔）。
     *
     * @param raw 原始文本
     * @return 确认点列表，无内容返回空列表
     */
    private List<String> splitConfirmPoints(String raw) {
        List<String> points = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return points;
        }
        for (String item : raw.split("[,;，；、]")) {
            if (StringUtils.isNotBlank(item)) {
                points.add(item.trim());
            }
        }
        return points;
    }

}
