package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.config.AigRouteProperties;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigLifecycleStatusEnum;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 路由引擎测试的公共装置（纯 Mockito，不依赖 Spring 与数据库）。
 *
 * <p>抽出来的原因：路由的加固项会越来越多（数据等级硬约束、健康状态、场景绑定…），
 * 每个加固点各自一份 mock 装置会迅速腐化——一处改口径、其它几处忘了跟，
 * 表现就是「有的测试还在跑旧口径」。装置与构造器集中在这里一处。</p>
 *
 * <p>类名刻意不以 {@code Test} 结尾：Surefire 默认只收 {@code *Test}/{@code Test*}/{@code *Tests}，
 * 因此本类不会被当成测试类执行（它没有任何 @Test）。</p>
 *
 * @author ai-gov
 */
abstract class AigRouteServiceTestSupport {

    /**
     * 外部模型 ID。
     */
    protected static final long EXTERNAL_MODEL_ID = 100L;

    /**
     * 本地模型 ID。
     */
    protected static final long LOCAL_MODEL_ID = 200L;

    /**
     * 被测能力编码（沿用内容域已在用的能力，避免造无意义的新名字）。
     */
    protected static final String CAPABILITY = "deliverable_consistency";

    /**
     * 场景强制绑定用例使用的场景编码。
     */
    protected static final String SCENARIO = "LONG_PAGE";

    protected AigCapabilityMapper capabilityMapper;
    protected AigRoutePolicyMapper routePolicyMapper;
    protected AigCapabilityModelMapper capabilityModelMapper;
    protected AigModelGovernanceMapper modelGovernanceMapper;
    protected AigModelViewMapper modelViewMapper;
    protected AigRouteScenarioBindingMapper bindingMapper;

    /**
     * 路由行为配置（能力标签严格模式开关）。
     */
    protected AigRouteProperties routeProperties;

    protected AigRouteServiceImpl routeService;

    /**
     * 注册实体的 MyBatis-Plus 表元数据。
     *
     * <p>路由引擎内部用 {@code LambdaQueryWrapper}（{@code .eq/.in} 都依赖实体→列的
     * lambda 缓存）。这份缓存在真实运行时由 mapper 扫描时初始化；纯 Mockito 单测里
     * 没有 Spring，缓存为空，构造 wrapper 会抛
     * {@code MybatisPlusException: can not find lambda cache for this entity}——
     * 而路由引擎刻意把异常收敛成 DENIED，表现就是「所有用例都变 DENIED」，
     * 极容易误判成业务逻辑坏了。这里显式初始化，把环境问题从断言里摘出去。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigCapability.class);
        TableInfoHelper.initTableInfo(assistant, AigRoutePolicy.class);
        TableInfoHelper.initTableInfo(assistant, AigCapabilityModel.class);
        TableInfoHelper.initTableInfo(assistant, AigModelGovernance.class);
        TableInfoHelper.initTableInfo(assistant, AigRouteScenarioBinding.class);
    }

    @BeforeEach
    void setUpRouteService() {
        capabilityMapper = mock(AigCapabilityMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        modelGovernanceMapper = mock(AigModelGovernanceMapper.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        bindingMapper = mock(AigRouteScenarioBindingMapper.class);
        // 默认「该场景 × 能力无强制绑定」：不带场景的用例根本不查这张表，
        // 带场景的用例若未显式桩绑定，也应表现为「未配置绑定 → 不收窄」。
        when(bindingMapper.selectList(any())).thenReturn(List.of());
        // 默认放行未声明标签的模型（生产默认值）；严格模式的用例自行打开开关
        routeProperties = new AigRouteProperties();
        // 调用器列表留空：这些测试只关心「路由是否把不该用的模型排除」，
        // 与调用器挑选无关；留空时 invoker=null，命中候选时决策仍是 MODEL。
        List<ModelInvoker> invokers = List.of();
        routeService = new AigRouteServiceImpl(capabilityMapper, routePolicyMapper, capabilityModelMapper,
            modelGovernanceMapper, modelViewMapper, bindingMapper, invokers, routeProperties);
    }

    /**
     * 用指定的调用器列表重建路由服务。
     *
     * <p>默认装置刻意留空调用器列表（多数用例只关心「候选模型是否被排除」）。
     * 要验证<b>派发</b>逻辑的用例（按部署类型 + 模型类型挑调用器）必须显式注入调用器，
     * 否则挑不出东西、断言会退化成「null == null」。</p>
     *
     * @param invokers 调用器列表
     */
    protected void useInvokers(List<ModelInvoker> invokers) {
        routeService = new AigRouteServiceImpl(capabilityMapper, routePolicyMapper, capabilityModelMapper,
            modelGovernanceMapper, modelViewMapper, bindingMapper, invokers, routeProperties);
    }

    /**
     * 桩：两个可用候选（外部 + 本地），分别声明给定的单次成本上限。
     *
     * <p>预算过滤必须有「上限不同」的两个候选才谈得上过滤：只有一家时，
     * 「被预算排除了」与「压根没过滤」在结果上不可区分。</p>
     *
     * @param externalAmount 外部候选声明的单次上限（null/空 = 未声明）
     * @param localAmount    本地候选声明的单次上限（null/空 = 未声明）
     */
    protected void stubTwoCandidatesWithCostLimit(String externalAmount, String localAmount) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1),
                binding(LOCAL_MODEL_ID, AigUsageTypeEnum.FALLBACK, 2)));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(EXTERNAL_MODEL_ID, "vendor/cloud-model"),
                model(LOCAL_MODEL_ID, "internal/local-model")));
        AigModelGovernance outer = governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        outer.setCostLimitAmount(amount(externalAmount));
        AigModelGovernance local = governance(LOCAL_MODEL_ID, AigDeploymentTypeEnum.LOCAL.getCode());
        local.setCostLimitAmount(amount(localAmount));
        when(modelGovernanceMapper.selectList(any())).thenReturn(List.of(outer, local));
    }

    /**
     * 桩：只绑定一个可用候选（外部），声明给定的单次成本上限。
     *
     * @param amount 声明的单次上限（null/空 = 未声明）
     */
    protected void stubSingleCandidateWithCostLimit(String amount) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(EXTERNAL_MODEL_ID, "vendor/cloud-model")));
        AigModelGovernance governance = governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        governance.setCostLimitAmount(amount(amount));
        when(modelGovernanceMapper.selectList(any())).thenReturn(List.of(governance));
    }

    /**
     * 解析金额字符串（空白视为未声明）。
     *
     * @param raw 金额字符串
     * @return 金额，未声明返回 null
     */
    private static java.math.BigDecimal amount(String raw) {
        return StringUtils.isBlank(raw) ? null : new java.math.BigDecimal(raw);
    }

    /**
     * 桩：指定场景 × 能力下的强制绑定供应商。
     *
     * @param providerIds 允许的供应商ID
     */
    protected void stubScenarioBinding(Long... providerIds) {
        List<AigRouteScenarioBinding> bindings = new ArrayList<>();
        int priority = 0;
        for (Long providerId : providerIds) {
            AigRouteScenarioBinding binding = new AigRouteScenarioBinding();
            binding.setBindId(++priority + 1000L);
            binding.setScenarioCode(SCENARIO);
            binding.setCapabilityCode(CAPABILITY);
            binding.setProviderId(providerId);
            binding.setPriority(priority);
            binding.setStatus("0");
            bindings.add(binding);
        }
        when(bindingMapper.selectList(any())).thenReturn(bindings);
    }

    /**
     * 桩：存在绑定行、但没写供应商（用于验证「配置不全 ≠ 不允许任何供应商」）。
     */
    protected void stubScenarioBindingWithoutProvider() {
        AigRouteScenarioBinding binding = new AigRouteScenarioBinding();
        binding.setBindId(1001L);
        binding.setScenarioCode(SCENARIO);
        binding.setCapabilityCode(CAPABILITY);
        binding.setProviderId(null);
        binding.setStatus("0");
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding));
    }

    /**
     * 桩：两个可用候选（外部 + 本地），分别属于供应商 {@code providerA} / {@code providerB}。
     *
     * <p>场景收窄必须有「两个不同供应商的候选」才谈得上收窄——只有一个候选时，
     * 「收窄后命中它」和「压根没收窄」在结果上不可区分，测试就会恒真。</p>
     *
     * @param providerA 外部候选的供应商ID
     * @param providerB 本地候选的供应商ID
     */
    protected void stubTwoCandidatesTwoProviders(long providerA, long providerB) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1),
                binding(LOCAL_MODEL_ID, AigUsageTypeEnum.FALLBACK, 2)));
        AigModelVo outer = model(EXTERNAL_MODEL_ID, "vendor/cloud-model");
        outer.setProviderId(providerA);
        AigModelVo local = model(LOCAL_MODEL_ID, "internal/local-model");
        local.setProviderId(providerB);
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(outer, local));
        when(modelGovernanceMapper.selectList(any()))
            .thenReturn(List.of(
                governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode()),
                governance(LOCAL_MODEL_ID, AigDeploymentTypeEnum.LOCAL.getCode())));
    }

    /**
     * 桩：能力存在且启用，未要求任何能力标签。
     */
    protected void stubCapability() {
        stubCapability(null);
    }

    /**
     * 桩：能力存在且启用，并声明要求的能力标签。
     *
     * @param requiredTags 能力要求的能力标签（逗号分隔，可空）
     */
    protected void stubCapability(String requiredTags) {
        AigCapability capability = new AigCapability();
        capability.setCapabilityId(1L);
        capability.setCapabilityCode(CAPABILITY);
        capability.setCapabilityName("成品一致性检查");
        capability.setStatus("0");
        capability.setAuditLevel("SUMMARY");
        capability.setRequiredTags(requiredTags);
        when(capabilityMapper.selectOne(any())).thenReturn(capability);
    }

    /**
     * 桩：路由策略。
     *
     * @param dataLevel        策略所属数据等级
     * @param allowExternal    是否允许外发
     * @param fallbackToManual 无可用模型时是否转人工
     */
    protected void stubPolicy(AigDataLevelEnum dataLevel, String allowExternal, String fallbackToManual) {
        stubPolicy(dataLevel, allowExternal, fallbackToManual, "N");
    }

    /**
     * 桩：路由策略（可指定「调用前是否需要审批」，C3）。
     *
     * @param dataLevel        策略所属数据等级
     * @param allowExternal    是否允许外发
     * @param fallbackToManual 无可用模型时是否转人工
     * @param requireApproval  调用前是否需要审批（Y/N）
     */
    protected void stubPolicy(AigDataLevelEnum dataLevel, String allowExternal, String fallbackToManual,
                              String requireApproval) {
        AigRoutePolicy policy = new AigRoutePolicy();
        policy.setPolicyId(9L);
        policy.setCapabilityCode(CAPABILITY);
        policy.setDataLevel(dataLevel.getCode());
        policy.setAllowExternal(allowExternal);
        policy.setRequireApproval(requireApproval);
        policy.setFallbackToManual(fallbackToManual);
        policy.setStatus("0");
        when(routePolicyMapper.selectOne(any())).thenReturn(policy);
    }

    /**
     * 桩：只绑定一个外部模型，且该模型在其它维度上完全可用、从未做过健康检查。
     */
    protected void stubExternalOnlyCandidates() {
        stubExternalOnlyCandidates(null);
    }

    /**
     * 桩：只绑定一个外部模型，指定其健康状态。
     *
     * @param healthStatus 健康状态（null 表示从未测过）
     */
    protected void stubExternalOnlyCandidates(String healthStatus) {
        stubSingleCandidate(AigDeploymentTypeEnum.EXTERNAL_API.getCode(), healthStatus);
    }

    /**
     * 桩：只绑定一个指定部署类型的候选，其它维度全部可用。
     *
     * @param deploymentType 部署类型编码
     */
    protected void stubSingleCandidate(String deploymentType) {
        stubSingleCandidate(deploymentType, null);
    }

    /**
     * 桩：只绑定一个指定部署类型的候选，其它维度全部可用。
     *
     * <p>数据等级给到最高（STRICT）以便隔离变量：这样「被排除」只可能来自
     * 本用例要验证的那一条约束，而不是等级不够——否则测试会因为另一个原因通过，
     * 证明不了想证明的事。</p>
     *
     * @param deploymentType 部署类型编码
     * @param healthStatus   健康状态（null 表示从未测过）
     */
    protected void stubSingleCandidate(String deploymentType, String healthStatus) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(EXTERNAL_MODEL_ID, "vendor/cloud-model")));
        when(modelGovernanceMapper.selectList(any()))
            .thenReturn(List.of(governance(EXTERNAL_MODEL_ID, deploymentType, healthStatus)));
    }

    /**
     * 构造能力-模型绑定。
     *
     * @param modelId   模型ID
     * @param usageType 用途
     * @param priority  优先级
     * @return 绑定记录
     */
    protected AigCapabilityModel binding(long modelId, AigUsageTypeEnum usageType, int priority) {
        AigCapabilityModel item = new AigCapabilityModel();
        item.setBindId(modelId);
        item.setCapabilityCode(CAPABILITY);
        item.setModelId(modelId);
        item.setUsageType(usageType.name());
        item.setPriority(priority);
        item.setStatus("0");
        return item;
    }

    /**
     * 构造模型主数据视图（启用）。
     *
     * @param modelId  模型ID
     * @param modelKey 模型键
     * @return 模型视图
     */
    protected AigModelVo model(long modelId, String modelKey) {
        AigModelVo vo = new AigModelVo();
        vo.setModelId(modelId);
        vo.setModelKey(modelKey);
        vo.setIsEnabled(Boolean.TRUE);
        return vo;
    }

    /**
     * 构造治理属性（不带健康状态，等价于「从未测过」）。
     *
     * @param modelId        模型ID
     * @param deploymentType 部署类型
     * @return 治理属性
     */
    protected AigModelGovernance governance(long modelId, String deploymentType) {
        return governance(modelId, deploymentType, null);
    }

    /**
     * 构造治理属性：生命周期 PRODUCTION（可调用）、最高数据等级 STRICT（够用）。
     *
     * <p>数据等级刻意给到最高，使「被排除」只可能来自本用例要验的那一条约束，
     * 而不是等级不够——否则测试会因为另一个原因通过，证明不了想证明的事。</p>
     *
     * @param modelId        模型ID
     * @param deploymentType 部署类型
     * @param healthStatus   健康状态（null 表示从未测过）
     * @return 治理属性
     */
    protected AigModelGovernance governance(long modelId, String deploymentType, String healthStatus) {
        AigModelGovernance governance = new AigModelGovernance();
        governance.setGovernanceId(modelId);
        governance.setModelId(modelId);
        governance.setDeploymentType(deploymentType);
        governance.setDataLevelMax(AigDataLevelEnum.STRICT.getCode());
        governance.setLifecycleStatus(AigLifecycleStatusEnum.PRODUCTION.getCode());
        governance.setHealthStatus(healthStatus);
        governance.setStatus("0");
        return governance;
    }

    /**
     * 把决策结果拼成一行可读描述，供断言失败时直接看出「到底卡在哪一步」。
     *
     * <p>路由引擎刻意不抛异常（异常一律收敛成 DENIED），因此断言失败时
     * 只看 expected/actual 无法区分「能力不存在」「没配策略」「候选全被排除」。
     * 把 reason 与 policyHits 带进失败消息，避免下次还要临时加打印。</p>
     *
     * @param decision 决策结果
     * @return 可读描述
     */
    protected String describe(AigRouteDecision decision) {
        return "decision=" + decision.getDecision()
            + ", reason=" + decision.getReason()
            + ", hits=" + decision.getPolicyHits();
    }

}
