package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.dromara.aigov.service.IAigCapabilityOverviewService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 能力概览聚合查询实现。
 *
 * <h3>实现纪律</h3>
 * <ul>
 *     <li><b>一次批量查询，不做 N+1</b>：能力、候选、策略、场景、24h 调用各一次查询，按
 *         {@code capabilityCode} 在内存里归并（能力数量是几十到几百，内存归并完全够用，
 *         而 N+1 会在"一屏 12 张卡"时变成 40+ 次往返）；</li>
 *     <li><b>24h 统计只取两列再在应用侧计数</b>：审计表是追加型、窗口有界（近 24h 行数量级在千~万），
 *         这里刻意不写 {@code selectMaps + groupBy} —— 那需要依赖列别名在不同方言下的返回键名，
 *         而"口径不清"比"多几次循环"更难查（见 §29.5 的教训：统计口径必须写清）；</li>
 *     <li><b>健康口径与路由一致</b>：路由只把明确"坏"的状态排除（{@code DOWN} / {@code UNHEALTHY}），
 *         从未探测过的模型不会被当成坏模型 —— 这里的 {@code UNHEALTHY} 判定与
 *         {@code AigRouteServiceImpl} 读取的口径保持一致，否则界面会报"健康异常"而路由其实放行。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigCapabilityOverviewServiceImpl implements IAigCapabilityOverviewService {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 审计结果：失败（0 成功 / 1 失败，见 {@code AigInvokeResultEnum}）。
     */
    private static final String AUDIT_RESULT_FAILED = "1";

    /**
     * 需要配置策略的数据等级全集（与 {@code AigDataLevelEnum} 一致）。
     */
    private static final List<String> ALL_DATA_LEVELS =
        List.of("PUBLIC", "INTERNAL", "RESTRICTED", "STRICT");

    /**
     * 候选健康汇总：没有任何候选。
     */
    private static final String HEALTH_NONE = "NONE";

    /**
     * 候选健康汇总：全部已知健康。
     */
    private static final String HEALTH_HEALTHY = "HEALTHY";

    /**
     * 候选健康汇总：存在明确不可用的候选。
     */
    private static final String HEALTH_UNHEALTHY = "UNHEALTHY";

    /**
     * 候选健康汇总：候选都没探测过（未知）。
     */
    private static final String HEALTH_UNKNOWN = "UNKNOWN";

    private final AigCapabilityMapper capabilityMapper;

    private final AigCapabilityModelMapper capabilityModelMapper;

    private final AigRoutePolicyMapper routePolicyMapper;

    private final AigRouteScenarioBindingMapper scenarioBindingMapper;

    private final AigInvocationAuditMapper auditMapper;

    private final AigModelViewMapper modelViewMapper;

    @Override
    public List<AigCapabilityOverviewVo> overview(AigCapabilityBo bo, String requiredTags,
                                                  String issueCode, String candidateState) {
        AigCapabilityBo query = bo == null ? new AigCapabilityBo() : bo;
        List<AigCapability> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<AigCapability>()
            .like(StringUtils.isNotBlank(query.getCapabilityCode()), AigCapability::getCapabilityCode,
                query.getCapabilityCode())
            .like(StringUtils.isNotBlank(query.getCapabilityName()), AigCapability::getCapabilityName,
                query.getCapabilityName())
            .like(StringUtils.isNotBlank(requiredTags), AigCapability::getRequiredTags, requiredTags)
            .eq(StringUtils.isNotBlank(query.getStatus()), AigCapability::getStatus, query.getStatus()));
        if (capabilities.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>(capabilities.size());
        for (AigCapability capability : capabilities) {
            codes.add(capability.getCapabilityCode());
        }

        Map<String, List<AigCapabilityModel>> candidatesByCode = loadCandidates(codes);
        Map<Long, AigModelVo> modelById = loadModels(candidatesByCode);
        Map<String, Set<String>> levelsByCode = loadPolicyLevels(codes);
        Map<String, Integer> sceneCountByCode = loadSceneCounts(codes);
        Map<String, int[]> auditByCode = loadAudit24h(codes);

        List<AigCapabilityOverviewVo> rows = new ArrayList<>(capabilities.size());
        for (AigCapability capability : capabilities) {
            AigCapabilityOverviewVo vo = toVo(capability, candidatesByCode.getOrDefault(capability.getCapabilityCode(),
                List.of()), modelById, levelsByCode.get(capability.getCapabilityCode()), sceneCountByCode,
                auditByCode);
            if (!matchesIssue(vo, issueCode) || !matchesCandidateState(vo, candidateState)) {
                continue;
            }
            rows.add(vo);
        }
        // 排序：问题多的在前（先看到要修的），其次候选少的在前，最后按最近改动
        rows.sort(Comparator
            .comparingInt((AigCapabilityOverviewVo row) -> row.getIssueCodes().size()).reversed()
            .thenComparingInt(AigCapabilityOverviewVo::getCandidateCount)
            .thenComparing(AigCapabilityOverviewVo::getUpdateTime,
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())));
        return rows;
    }

    /**
     * 批量装载"启用的模型候选"。
     *
     * @param codes 能力编码
     * @return 能力编码 → 候选列表
     */
    private Map<String, List<AigCapabilityModel>> loadCandidates(List<String> codes) {
        List<AigCapabilityModel> all = capabilityModelMapper.selectList(new LambdaQueryWrapper<AigCapabilityModel>()
            .in(AigCapabilityModel::getCapabilityCode, codes)
            .eq(AigCapabilityModel::getStatus, STATUS_NORMAL)
            .orderByAsc(AigCapabilityModel::getPriority));
        Map<String, List<AigCapabilityModel>> grouped = new LinkedHashMap<>();
        for (AigCapabilityModel row : all) {
            grouped.computeIfAbsent(row.getCapabilityCode(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 批量装载候选模型的主数据与治理属性（用于 modelKey 与健康判定）。
     *
     * @param candidatesByCode 能力编码 → 候选列表
     * @return 模型ID → 模型视图
     */
    private Map<Long, AigModelVo> loadModels(Map<String, List<AigCapabilityModel>> candidatesByCode) {
        Set<Long> modelIds = new HashSet<>();
        for (List<AigCapabilityModel> rows : candidatesByCode.values()) {
            for (AigCapabilityModel row : rows) {
                if (row.getModelId() != null) {
                    modelIds.add(row.getModelId());
                }
            }
        }
        Map<Long, AigModelVo> modelById = new HashMap<>();
        if (modelIds.isEmpty()) {
            return modelById;
        }
        List<AigModelVo> models = modelViewMapper.selectModelListByIds(new ArrayList<>(modelIds));
        if (models != null) {
            for (AigModelVo model : models) {
                if (model != null && model.getModelId() != null) {
                    modelById.put(model.getModelId(), model);
                }
            }
        }
        return modelById;
    }

    /**
     * 批量装载已配置的数据等级集合。
     *
     * @param codes 能力编码
     * @return 能力编码 → 已配置等级集合
     */
    private Map<String, Set<String>> loadPolicyLevels(List<String> codes) {
        List<AigRoutePolicy> policies = routePolicyMapper.selectList(new LambdaQueryWrapper<AigRoutePolicy>()
            .in(AigRoutePolicy::getCapabilityCode, codes)
            .eq(AigRoutePolicy::getStatus, STATUS_NORMAL));
        Map<String, Set<String>> levelsByCode = new HashMap<>();
        for (AigRoutePolicy policy : policies) {
            if (StringUtils.isBlank(policy.getDataLevel())) {
                continue;
            }
            levelsByCode.computeIfAbsent(policy.getCapabilityCode(), key -> new HashSet<>())
                .add(policy.getDataLevel().toUpperCase(Locale.ROOT));
        }
        return levelsByCode;
    }

    /**
     * 批量装载场景指定条数。
     *
     * @param codes 能力编码
     * @return 能力编码 → 条数
     */
    private Map<String, Integer> loadSceneCounts(List<String> codes) {
        List<AigRouteScenarioBinding> bindings = scenarioBindingMapper.selectList(
            new LambdaQueryWrapper<AigRouteScenarioBinding>()
                .in(AigRouteScenarioBinding::getCapabilityCode, codes)
                .eq(AigRouteScenarioBinding::getStatus, STATUS_NORMAL));
        Map<String, Integer> counts = new HashMap<>();
        for (AigRouteScenarioBinding binding : bindings) {
            counts.merge(binding.getCapabilityCode(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 装载近 24h 的调用与失败次数。
     *
     * @param codes 能力编码
     * @return 能力编码 → [调用数, 失败数]
     */
    private Map<String, int[]> loadAudit24h(List<String> codes) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<AigInvocationAudit> rows = auditMapper.selectList(new LambdaQueryWrapper<AigInvocationAudit>()
            .select(AigInvocationAudit::getCapabilityCode, AigInvocationAudit::getResult)
            .in(AigInvocationAudit::getCapabilityCode, codes)
            .ge(AigInvocationAudit::getOperateTime, since));
        Map<String, int[]> counters = new HashMap<>();
        for (AigInvocationAudit row : rows) {
            int[] counter = counters.computeIfAbsent(row.getCapabilityCode(), key -> new int[2]);
            counter[0]++;
            if (AUDIT_RESULT_FAILED.equals(row.getResult())) {
                counter[1]++;
            }
        }
        return counters;
    }

    /**
     * 组装一行概览。
     *
     * @param capability       能力
     * @param candidates       候选（可能为空）
     * @param modelById        模型视图
     * @param coveredLevels    已配置等级
     * @param sceneCountByCode 场景条数
     * @param auditByCode      24h 计数
     * @return 概览行
     */
    private AigCapabilityOverviewVo toVo(AigCapability capability, List<AigCapabilityModel> candidates,
                                         Map<Long, AigModelVo> modelById, Set<String> coveredLevels,
                                         Map<String, Integer> sceneCountByCode, Map<String, int[]> auditByCode) {
        AigCapabilityOverviewVo vo = new AigCapabilityOverviewVo();
        vo.setCapabilityId(capability.getCapabilityId());
        vo.setCapabilityCode(capability.getCapabilityCode());
        vo.setCapabilityName(capability.getCapabilityName());
        vo.setRequiredTags(capability.getRequiredTags());
        vo.setStatus(capability.getStatus());
        vo.setUpdateTime(capability.getUpdateTime() == null ? capability.getCreateTime() : capability.getUpdateTime());
        vo.setCandidateCount(candidates.size());
        vo.setPrimaryModelKey(pickPrimaryModelKey(candidates, modelById));
        vo.setCandidateHealthAgg(aggregateHealth(candidates, modelById));
        int covered = coveredLevels == null ? 0 : coveredLevels.size();
        vo.setPolicyCoveredLevels(covered);
        vo.setPolicyTotalLevels(ALL_DATA_LEVELS.size());
        vo.setSceneCount(sceneCountByCode.getOrDefault(capability.getCapabilityCode(), 0));
        int[] audit = auditByCode.get(capability.getCapabilityCode());
        vo.setCalls24h(audit == null ? 0 : audit[0]);
        vo.setFails24h(audit == null ? 0 : audit[1]);
        vo.setIssueCodes(resolveIssues(candidates, covered));
        return vo;
    }

    /**
     * 取首选候选的模型键：用途 PRIMARY（没有则按用途权重）→ 优先级最小。
     *
     * @param candidates 候选
     * @param modelById  模型视图
     * @return 模型键；取不到返回 null
     */
    private String pickPrimaryModelKey(List<AigCapabilityModel> candidates, Map<Long, AigModelVo> modelById) {
        AigCapabilityModel best = null;
        for (AigCapabilityModel candidate : candidates) {
            if (best == null || compareCandidate(candidate, best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        AigModelVo model = modelById.get(best.getModelId());
        return model == null ? null : model.getModelKey();
    }

    /**
     * 候选排序：用途权重 → 优先级 → 模型ID（保证稳定）。
     *
     * @param left  候选
     * @param right 候选
     * @return 比较结果
     */
    private int compareCandidate(AigCapabilityModel left, AigCapabilityModel right) {
        int usage = Integer.compare(usageOrder(left.getUsageType()), usageOrder(right.getUsageType()));
        if (usage != 0) {
            return usage;
        }
        int priority = Integer.compare(priorityOf(left), priorityOf(right));
        if (priority != 0) {
            return priority;
        }
        return Long.compare(modelIdOf(left), modelIdOf(right));
    }

    /**
     * 用途权重（与路由一致的排序意图：PRIMARY 最优先）。
     *
     * @param usageType 用途
     * @return 权重
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

    private int priorityOf(AigCapabilityModel candidate) {
        return candidate.getPriority() == null ? Integer.MAX_VALUE : candidate.getPriority();
    }

    private long modelIdOf(AigCapabilityModel candidate) {
        return candidate.getModelId() == null ? Long.MAX_VALUE : candidate.getModelId();
    }

    /**
     * 汇总候选健康度。
     *
     * @param candidates 候选
     * @param modelById  模型视图
     * @return {@code NONE} / {@code UNHEALTHY} / {@code HEALTHY} / {@code UNKNOWN}
     */
    private String aggregateHealth(List<AigCapabilityModel> candidates, Map<Long, AigModelVo> modelById) {
        if (candidates.isEmpty()) {
            return HEALTH_NONE;
        }
        boolean anyUnknown = false;
        for (AigCapabilityModel candidate : candidates) {
            AigModelVo model = modelById.get(candidate.getModelId());
            String status = model == null ? null : model.getHealthStatus();
            if (isUnhealthy(status)) {
                return HEALTH_UNHEALTHY;
            }
            if (StringUtils.isBlank(status)) {
                anyUnknown = true;
            }
        }
        // 都没探测过 → 未知（不是"健康"）：界面据此提示"尚未探测"，而不是给一个假的绿灯
        return anyUnknown ? HEALTH_UNKNOWN : HEALTH_HEALTHY;
    }

    /**
     * 是否明确不可用（口径与路由一致：只看 DOWN / UNHEALTHY）。
     *
     * @param healthStatus 健康状态
     * @return 明确不可用返回 true
     */
    private boolean isUnhealthy(String healthStatus) {
        if (StringUtils.isBlank(healthStatus)) {
            return false;
        }
        String normalized = healthStatus.trim().toUpperCase(Locale.ROOT);
        return "DOWN".equals(normalized) || "UNHEALTHY".equals(normalized);
    }

    /**
     * 计算问题原因码。
     *
     * @param candidates    候选
     * @param coveredLevels 已配置等级数
     * @return 原因码（可能为空）
     */
    private List<String> resolveIssues(List<AigCapabilityModel> candidates, int coveredLevels) {
        List<String> issues = new ArrayList<>();
        if (candidates.isEmpty()) {
            issues.add("NO_MODEL_CANDIDATE");
        }
        if (coveredLevels == 0) {
            issues.add("NO_ROUTE_POLICY");
        } else if (coveredLevels < ALL_DATA_LEVELS.size()) {
            issues.add("POLICY_INCOMPLETE");
        }
        return issues;
    }

    /**
     * 按问题码过滤。
     *
     * @param vo        概览行
     * @param issueCode 问题码（可空）
     * @return 命中返回 true
     */
    private boolean matchesIssue(AigCapabilityOverviewVo vo, String issueCode) {
        if (StringUtils.isBlank(issueCode)) {
            return true;
        }
        String expected = issueCode.trim().toUpperCase(Locale.ROOT);
        for (String code : vo.getIssueCodes()) {
            if (code.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按候选有无过滤。
     *
     * @param vo             概览行
     * @param candidateState {@code HAS} / {@code NO}（可空）
     * @return 命中返回 true
     */
    private boolean matchesCandidateState(AigCapabilityOverviewVo vo, String candidateState) {
        if (StringUtils.isBlank(candidateState)) {
            return true;
        }
        String expected = candidateState.trim().toUpperCase(Locale.ROOT);
        boolean has = vo.getCandidateCount() != null && vo.getCandidateCount() > 0;
        if ("HAS".equals(expected)) {
            return has;
        }
        if ("NO".equals(expected)) {
            return !has;
        }
        // 认不出的取值按"不过滤"处理；这里不抛错，避免一个筛选参数写错就整页打不开
        log.warn("未识别的候选筛选参数，已忽略：candidateState={}", candidateState);
        return true;
    }

}
