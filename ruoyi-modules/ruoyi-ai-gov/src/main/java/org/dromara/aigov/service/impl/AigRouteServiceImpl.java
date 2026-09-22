package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由引擎实现。
 *
 * <p><b>决策算法（严格按 SPEC §5.2 的 7 步顺序）</b>，逐步落地位置见各步注释与小节方法：</p>
 * <ol>
 *     <li>{@code decide} 内「// 步骤1」：能力不存在或 status≠'0' → {@code DENIED}</li>
 *     <li>「// 步骤2」：无 {@code aig_route_policy(能力,数据等级)} → {@code DENIED}（默认拒绝）</li>
 *     <li>「// 步骤3」：{@code allowExternal='N'} 时候选模型仅限非外部部署</li>
 *     <li>「// 步骤4」：{@link #isCandidateUsable} 逐项校验生命周期 / 数据等级 / 启用 / 外发</li>
 *     <li>「// 步骤5」：{@link #orderBindings} 按 PRIMARY→GRAY→FALLBACK 再按 priority 升序</li>
 *     <li>「// 步骤6」：无命中 → {@code fallbackToManual='Y'} 转 {@code MANUAL}，否则 {@code DENIED}</li>
 *     <li>「// 步骤7」：因 {@code allowExternal='N'} 被排除的外部模型写入 {@code policyHits}</li>
 * </ol>
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
     * 可插拔调用器（SPI）。
     */
    private final List<ModelInvoker> invokers;

    @Override
    public AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(capabilityCode);
        try {
            return doDecide(decision, capabilityCode, dataLevel);
        } catch (Exception e) {
            // 路由引擎不抛异常：任何异常都以 DENIED 表达
            log.error("路由决策异常, capabilityCode={}, dataLevel={}", capabilityCode,
                dataLevel == null ? null : dataLevel.getCode(), e);
            decision.addHit("路由决策异常：" + e.getClass().getSimpleName());
            return denied(decision, "路由决策异常，已按拒绝处理");
        }
    }

    @Override
    public List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel) {
        AigRouteDecision decision = decide(capabilityCode, dataLevel);
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
     * @return 决策结果
     */
    private AigRouteDecision doDecide(AigRouteDecision decision, String capabilityCode, AigDataLevelEnum dataLevel) {
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
        if (!allowExternal) {
            // 步骤3：候选模型仅限 deploymentType ∈ {LOCAL, GROUP}
            decision.addHit("策略 allowExternal='N'：本次调用仅允许本地/集团共享部署模型，外部部署模型将被排除");
        }

        // 步骤4：逐项校验候选模型可用性；步骤7：记录因 allowExternal='N' 被排除的外部模型
        for (AigCapabilityModel binding : ordered) {
            AigModelVo model = modelMap.get(binding.getModelId());
            AigModelGovernance governance = governanceMap.get(binding.getModelId());
            AigDeploymentTypeEnum deployment = isCandidateUsable(decision, binding, model, governance, dataLevel, allowExternal);
            if (deployment == null) {
                continue;
            }
            // 步骤5 命中第一个可用模型
            decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
            decision.setModelId(binding.getModelId());
            decision.setModelKey(model.getModelKey());
            decision.setDeploymentType(deployment.getCode());
            decision.setReason("命中模型 " + model.getModelKey() + "（" + deployment.getDesc() + "）");
            decision.addHit("选中模型：modelId=" + binding.getModelId()
                + "，modelKey=" + model.getModelKey()
                + "，usageType=" + binding.getUsageType()
                + "，priority=" + binding.getPriority()
                + "，deploymentType=" + deployment.getCode()
                + "，dataLevelMax=" + governance.getDataLevelMax());
            ModelInvoker invoker = resolveInvoker(deployment, capabilityCode);
            if (invoker == null) {
                decision.addHit("未找到支持 " + deployment.getCode() + " 且可用的调用器（invoker）");
            } else {
                decision.setInvoker(invoker.invokerName());
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
            return decision;
        }

        // 步骤6：无命中 → fallbackToManual='Y' → MANUAL，否则 DENIED
        return noModel(decision, fallbackToManual);
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
     *     <li>部署类型 {@code external()==false} 或 {@code policy.allowExternal=='Y'}</li>
     * </ol>
     *
     * @param decision      决策对象（写入过滤原因）
     * @param binding       绑定记录
     * @param model         模型主数据（可为 null）
     * @param governance    治理属性（可为 null）
     * @param dataLevel     本次数据等级
     * @param allowExternal 策略是否允许外发
     * @return 可用的部署类型；不可用返回 null
     */
    private AigDeploymentTypeEnum isCandidateUsable(AigRouteDecision decision, AigCapabilityModel binding,
                                                    AigModelVo model, AigModelGovernance governance,
                                                    AigDataLevelEnum dataLevel, boolean allowExternal) {
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
        // 4.4 / 步骤3 / 步骤7：allowExternal='N' 时排除外部部署类型，并记录该原因
        if (!allowExternal && deployment.isExternal()) {
            decision.addHit("排除 " + modelLabel + "（modelKey=" + model.getModelKey() + "）：策略 allowExternal='N'，"
                + "该模型部署类型=" + deployment.getCode() + " 属于外部调用，数据外发被策略禁止");
            return null;
        }
        return deployment;
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
     * @param deployment     部署类型
     * @param capabilityCode 业务能力编码
     * @return 可用调用器，找不到返回 null
     */
    private ModelInvoker resolveInvoker(AigDeploymentTypeEnum deployment, String capabilityCode) {
        if (invokers == null) {
            return null;
        }
        // 第一段：声明处理该能力的调用器优先
        for (ModelInvoker invoker : invokers) {
            if (invoker.supports(deployment) && invoker.available()
                && invoker.supportsCapability(capabilityCode)) {
                return invoker;
            }
        }
        // 第二段：兜底——只按部署类型匹配（保持既有行为，snail-ai 等通用调用器走这里）
        for (ModelInvoker invoker : invokers) {
            if (invoker.supports(deployment) && invoker.available()) {
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
