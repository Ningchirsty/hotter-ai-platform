package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigLifecycleStatusEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.service.invoker.ModelInvoker;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 路由引擎「数据等级硬约束」行为锁定测试。
 *
 * <p><b>为什么必须单独测这一段</b>：{@link AigDataLevelEnum#STRICT} 的语义是
 * 「任何路由策略都不允许外发」。它与 {@code RESTRICTED} 的区别只在
 * 「策略写成 {@code allowExternal='Y'} 时是否还拦得住」——而这恰好是最容易被
 * 一次「顺手放行」的改动破坏、且破坏后**从结果上看不出来**的地方：
 * 数据照样发出去，审计里 external_call=Y 看起来也"符合策略"。</p>
 *
 * <p>因此本测试用**同一套数据**跑两遍：数据等级换成 {@code RESTRICTED} 时必须
 * 能选中外部模型（证明测试不是恒真），换成 {@code STRICT} 时必须选不中
 * （证明硬约束真的生效）。纯 Mockito、不依赖 Spring 与数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceImplStrictTest {

    /**
     * 外部模型 ID。
     */
    private static final long EXTERNAL_MODEL_ID = 100L;

    /**
     * 本地模型 ID。
     */
    private static final long LOCAL_MODEL_ID = 200L;

    private static final String CAPABILITY = "deliverable_consistency";

    private AigCapabilityMapper capabilityMapper;
    private AigRoutePolicyMapper routePolicyMapper;
    private AigCapabilityModelMapper capabilityModelMapper;
    private AigModelGovernanceMapper modelGovernanceMapper;
    private AigModelViewMapper modelViewMapper;

    private AigRouteServiceImpl routeService;

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
    }

    @BeforeEach
    void setUp() {
        capabilityMapper = mock(AigCapabilityMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        modelGovernanceMapper = mock(AigModelGovernanceMapper.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        // 调用器列表留空：本测试只关心「路由是否把外部模型排除」，
        // 与调用器挑选无关；留空时 invoker=null，决策仍是 MODEL。
        List<ModelInvoker> invokers = List.of();
        routeService = new AigRouteServiceImpl(capabilityMapper, routePolicyMapper, capabilityModelMapper,
            modelGovernanceMapper, modelViewMapper, invokers);
    }

    @Test
    @DisplayName("STRICT：策略写成 allowExternal='Y' 也必须拦下外部模型，且说明里写明「被忽略」")
    void strictMustNotSelectExternalEvenWhenPolicyAllows() {
        // 策略显式允许外发——这正是需要被硬约束盖住的情形
        stubCapability();
        stubPolicy(AigDataLevelEnum.STRICT, "Y", "Y");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT);

        assertNotEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "STRICT 级数据绝不能选中外部模型，哪怕策略 allowExternal='Y'；实际=" + describe(decision));
        // fallbackToManual='Y' → 无可用模型时转人工，而不是静默失败
        assertEquals(AigRouteDecisionEnum.MANUAL.getCode(), decision.getDecision(),
            "STRICT + fallbackToManual='Y' 应转人工；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("严格级")),
            "策略被硬约束覆盖的原因必须出现在 policyHits 里，便于审计与排障：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("对照：同一套数据换成 RESTRICTED 且策略允许外发时，外部模型必须能被选中")
    void restrictedStillAllowsExternalWhenPolicySaysSo() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.RESTRICTED, "Y", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.RESTRICTED);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "RESTRICTED 与 STRICT 必须可区分：RESTRICTED 仍可由策略显式放行外发；实际=" + describe(decision));
        assertEquals(AigDeploymentTypeEnum.EXTERNAL_API.getCode(), decision.getDeploymentType());
    }

    @Test
    @DisplayName("STRICT：外部模型被排除后，必须顺延到非外部部署模型，而不是直接判定无模型")
    void strictFallsThroughToLocalModel() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.STRICT, "Y", "N");
        // PRIMARY 是外部模型，FALLBACK 是本地模型
        when(capabilityModelMapper.selectList(any())).thenReturn(List.of(
            binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1),
            binding(LOCAL_MODEL_ID, AigUsageTypeEnum.FALLBACK, 2)));
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(
            model(EXTERNAL_MODEL_ID, "vendor/cloud-model"),
            model(LOCAL_MODEL_ID, "local-qwen")));
        when(modelGovernanceMapper.selectList(any())).thenReturn(List.of(
            governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode()),
            governance(LOCAL_MODEL_ID, AigDeploymentTypeEnum.LOCAL.getCode())));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "STRICT 下应仍能命中本地模型；实际=" + describe(decision));
        assertEquals("local-qwen", decision.getModelKey(),
            "外部模型被禁后应顺延到本地模型，而不是把整个能力判成不可用；实际=" + describe(decision));
        assertEquals(AigDeploymentTypeEnum.LOCAL.getCode(), decision.getDeploymentType());
    }

    /**
     * 把决策结果拼成一行可读描述，供断言失败时直接看出「到底卡在哪一步」。
     *
     * <p>路由引擎刻意不抛异常（异常一律收敛成 DENIED），因此断言失败时
     * 只看 expected/actual 无法区分「能力不存在」「没配策略」「候选全被排除」。
     * 这里把 reason 与 policyHits 带进失败消息，避免下次还要临时加打印。</p>
     *
     * @param decision 决策结果
     * @return 可读描述
     */
    private String describe(AigRouteDecision decision) {
        return "decision=" + decision.getDecision()
            + ", reason=" + decision.getReason()
            + ", hits=" + decision.getPolicyHits();
    }

    /**
     * 桩：能力存在且启用。
     */
    private void stubCapability() {
        AigCapability capability = new AigCapability();
        capability.setCapabilityId(1L);
        capability.setCapabilityCode(CAPABILITY);
        capability.setCapabilityName("成品一致性检查");
        capability.setStatus("0");
        capability.setAuditLevel("SUMMARY");
        when(capabilityMapper.selectOne(any())).thenReturn(capability);
    }

    /**
     * 桩：路由策略。
     *
     * @param dataLevel         策略所属数据等级
     * @param allowExternal     是否允许外发
     * @param fallbackToManual  无可用模型时是否转人工
     */
    private void stubPolicy(AigDataLevelEnum dataLevel, String allowExternal, String fallbackToManual) {
        AigRoutePolicy policy = new AigRoutePolicy();
        policy.setPolicyId(9L);
        policy.setCapabilityCode(CAPABILITY);
        policy.setDataLevel(dataLevel.getCode());
        policy.setAllowExternal(allowExternal);
        policy.setRequireApproval("N");
        policy.setFallbackToManual(fallbackToManual);
        policy.setStatus("0");
        when(routePolicyMapper.selectOne(any())).thenReturn(policy);
    }

    /**
     * 桩：只绑定一个外部模型，且该模型在其它维度上完全可用。
     */
    private void stubExternalOnlyCandidates() {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(EXTERNAL_MODEL_ID, "vendor/cloud-model")));
        when(modelGovernanceMapper.selectList(any()))
            .thenReturn(List.of(governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode())));
    }

    /**
     * 构造能力-模型绑定。
     *
     * @param modelId   模型ID
     * @param usageType 用途
     * @param priority  优先级
     * @return 绑定记录
     */
    private AigCapabilityModel binding(long modelId, AigUsageTypeEnum usageType, int priority) {
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
    private AigModelVo model(long modelId, String modelKey) {
        AigModelVo vo = new AigModelVo();
        vo.setModelId(modelId);
        vo.setModelKey(modelKey);
        vo.setIsEnabled(Boolean.TRUE);
        return vo;
    }

    /**
     * 构造治理属性：生命周期 PRODUCTION（可调用）、最高数据等级 STRICT（够用）。
     *
     * <p>数据等级刻意给到最高，使「被排除」只可能来自外发硬约束，
     * 而不是等级不够——否则测试会因为另一个原因通过，证明不了想证明的事。</p>
     *
     * @param modelId        模型ID
     * @param deploymentType 部署类型
     * @return 治理属性
     */
    private AigModelGovernance governance(long modelId, String deploymentType) {
        AigModelGovernance governance = new AigModelGovernance();
        governance.setGovernanceId(modelId);
        governance.setModelId(modelId);
        governance.setDeploymentType(deploymentType);
        governance.setDataLevelMax(AigDataLevelEnum.STRICT.getCode());
        governance.setLifecycleStatus(AigLifecycleStatusEnum.PRODUCTION.getCode());
        governance.setStatus("0");
        return governance;
    }

}
