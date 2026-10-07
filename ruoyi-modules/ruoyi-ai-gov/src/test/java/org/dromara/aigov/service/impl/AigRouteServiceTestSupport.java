package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.AigRoutePolicy;
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
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

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

    protected AigCapabilityMapper capabilityMapper;
    protected AigRoutePolicyMapper routePolicyMapper;
    protected AigCapabilityModelMapper capabilityModelMapper;
    protected AigModelGovernanceMapper modelGovernanceMapper;
    protected AigModelViewMapper modelViewMapper;

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
    }

    @BeforeEach
    void setUpRouteService() {
        capabilityMapper = mock(AigCapabilityMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        modelGovernanceMapper = mock(AigModelGovernanceMapper.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        // 调用器列表留空：这些测试只关心「路由是否把不该用的模型排除」，
        // 与调用器挑选无关；留空时 invoker=null，命中候选时决策仍是 MODEL。
        List<ModelInvoker> invokers = List.of();
        routeService = new AigRouteServiceImpl(capabilityMapper, routePolicyMapper, capabilityModelMapper,
            modelGovernanceMapper, modelViewMapper, invokers);
    }

    /**
     * 桩：能力存在且启用。
     */
    protected void stubCapability() {
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
     * @param dataLevel        策略所属数据等级
     * @param allowExternal    是否允许外发
     * @param fallbackToManual 无可用模型时是否转人工
     */
    protected void stubPolicy(AigDataLevelEnum dataLevel, String allowExternal, String fallbackToManual) {
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
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(EXTERNAL_MODEL_ID, "vendor/cloud-model")));
        when(modelGovernanceMapper.selectList(any()))
            .thenReturn(List.of(governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode(),
                healthStatus)));
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
