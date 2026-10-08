package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigModelHealthProbeProperties;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.aigov.service.IAigModelHealthProbeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型健康探测实现（M-003）。
 *
 * <p><b>为什么要有它</b>：2026-10-08 的只读巡检查出，生产里真正参与路由的 3 个模型
 * 没有一个"近期测过且健康"——`qwen2.5:7b-instruct` 从未测过、`qwen2.5vl:3b` 有状态无时间、
 * `local-content` 已 14 天未复测。探测能力（`POST /aigov/model/{id}/test`）早就存在，
 * 缺的只是"到点自动跑"。</p>
 *
 * <p><b>两条刻意的设计</b>：</p>
 * <ol>
 *     <li><b>只为参与路由的模型花钱</b>（{@code onlyBound}）：探测是对外真实调用，
 *         没有绑定的模型即使不健康也不影响任何一次路由，为它们付费没有收益。</li>
 *     <li><b>跳过刚测过的</b>（{@code staleHours}）：让"把周期调短"不会等比例放大成本。</li>
 * </ol>
 *
 * <p>单个模型探测失败<b>不中断整轮</b>：一个供应商超时不代表其余模型不该测。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigModelHealthProbeServiceImpl implements IAigModelHealthProbeService {

    private final AigModelGovernanceMapper modelGovernanceMapper;
    private final AigCapabilityModelMapper capabilityModelMapper;
    private final IAigModelGovernanceService modelGovernanceService;
    private final AigModelHealthProbeProperties properties;

    /**
     * 后台执行探测。用单线程<b>串行</b>执行：探测是外呼，并发会把同一供应商的连接同时拉满，
     * 串行最温和；用守护线程是因为它是后台作业，进程退出时不该被它拖住。
     *
     * <p><b>注意：单线程执行器不等于去重</b>——它会把第二次提交<b>排队</b>，
     * 于是"连续两次提交"仍然会真的外呼两轮。去重靠 {@link #probeInFlight}。</p>
     *
     * <p>故意不加 {@code final}：{@code AigModelHealthProbeServiceTest} 需要把它换成
     * "提交即拒绝"的替身来验证 {@code REJECTED} 分支不会把状态位永久卡死
     * （那个分支只有在执行器不可用时才会发生，生产里没有别的办法复现）。</p>
     */
    private ExecutorService probeExecutor = newSingleThreadProbeExecutor();

    private static ExecutorService newSingleThreadProbeExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "aigov-model-health-probe");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * "是否已有一轮探测在跑"的状态位。
     *
     * <p>它存在的唯一理由就是<b>去重</b>：没有它，两次提交会排队执行、外呼两轮、账单翻倍。</p>
     */
    private final AtomicBoolean probeInFlight = new AtomicBoolean(false);

    @Override
    public SubmitOutcome triggerAsync() {
        if (!properties.isEnabled()) {
            // 开关关闭时不提交任何任务。即使将来有人绕过 HTTP 入口直接调用，
            // 也不会出现"以为已经探测了、其实什么都没做"
            return SubmitOutcome.DISABLED;
        }
        // CAS 而非 get-then-set：并发提交时只有一个能把状态位从 false 翻成 true
        if (!probeInFlight.compareAndSet(false, true)) {
            return SubmitOutcome.ALREADY_RUNNING;
        }
        try {
            probeExecutor.execute(this::runProbeQuietly);
            return SubmitOutcome.ACCEPTED;
        } catch (RejectedExecutionException e) {
            // 提交失败必须把状态位放回去，否则这个开关会永远卡在 true，
            // 从此每一次提交都被误判成"已有一轮在跑"
            probeInFlight.set(false);
            log.warn("模型健康探测提交失败（执行器不可用）", e);
            return SubmitOutcome.REJECTED;
        }
    }

    /**
     * 后台跑一轮并把结果写进日志；异常只记日志，不向调用方抛。
     *
     * <p>无论成功、失败还是抛异常，都必须释放 {@link #probeInFlight}——
     * 漏掉这一步就等于永久禁止后续探测。</p>
     */
    private void runProbeQuietly() {
        try {
            AigModelHealthProbeVo r = probeOnce();
            if (r.getUnhealthy() > 0 || r.getSkipped() > 0) {
                log.warn("模型健康探测：测 {} 个，健康 {}，不健康 {} {}，跳过 {}，本轮未处理 {}",
                    r.getProbed(), r.getHealthy(), r.getUnhealthy(), r.getUnhealthyModels(),
                    r.getSkipped(), r.getDeferred());
            } else {
                log.info("模型健康探测：测 {} 个，全部健康，本轮未处理 {}",
                    r.getProbed(), r.getDeferred());
            }
        } catch (Exception e) {
            log.error("模型健康探测后台执行异常（本轮结束，下一轮继续）", e);
        } finally {
            probeInFlight.set(false);
        }
    }

    @Override
    public AigModelHealthProbeVo probeOnce() {
        AigModelHealthProbeVo result = new AigModelHealthProbeVo();
        if (!properties.isEnabled()) {
            result.setSkippedReason("aigov.model.health-probe.enabled=false");
            return result;
        }

        List<AigModelGovernance> all = modelGovernanceMapper.selectList(
            new LambdaQueryWrapper<AigModelGovernance>()
                .eq(AigModelGovernance::getDelFlag, "0")
                .eq(AigModelGovernance::getStatus, "0"));
        if (all.isEmpty()) {
            result.setExecuted(true);
            return result;
        }

        Set<Long> bound = properties.isOnlyBound() ? boundModelIds() : Set.of();
        LocalDateTime staleBefore = properties.getStaleHours() > 0
            ? LocalDateTime.now().minusHours(properties.getStaleHours()) : null;

        // 最陈旧的先测：这样 maxPerRound 截断时，先被照顾的是最需要复测的那些
        List<AigModelGovernance> candidates = all.stream()
            .filter(g -> g.getModelId() != null)
            .filter(g -> !properties.isOnlyBound() || bound.contains(g.getModelId()))
            .filter(g -> isStale(g, staleBefore))
            .sorted(Comparator.comparing(AigModelGovernance::getHealthTime,
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();

        int limit = properties.getMaxPerRound();
        List<AigModelGovernance> round = limit > 0 && candidates.size() > limit
            ? candidates.subList(0, limit) : candidates;
        result.setDeferred(candidates.size() - round.size());
        result.setExecuted(true);

        for (AigModelGovernance g : round) {
            probeOne(result, g.getModelId());
        }
        return result;
    }

    /**
     * 探测单个模型并计入汇总。
     *
     * @param result  汇总对象
     * @param modelId 模型ID
     */
    private void probeOne(AigModelHealthProbeVo result, Long modelId) {
        try {
            AigModelTestVo vo = modelGovernanceService.testConnection(modelId);
            result.setProbed(result.getProbed() + 1);
            if (Boolean.TRUE.equals(vo.getOk())) {
                result.setHealthy(result.getHealthy() + 1);
            } else {
                result.setUnhealthy(result.getUnhealthy() + 1);
                result.getUnhealthyModels().add(String.valueOf(modelId));
            }
        } catch (Exception e) {
            // 单个模型失败不中断整轮，也不计入 unhealthy——
            // "探测本身失败"（未配置端点/不支持探测）与"探到了但不健康"是两件事，
            // 混在一起会让 unhealthy 这个数不可信
            result.setSkipped(result.getSkipped() + 1);
            log.warn("模型健康探测失败（本轮跳过，继续下一个）modelId={}：{}", modelId, e.getMessage());
        }
    }

    /**
     * 判定某个模型是否需要在本轮复测。
     *
     * @param g           治理行
     * @param staleBefore 超过该时间点未测的算陈旧；null 表示不按时间过滤
     * @return true = 需要复测
     */
    private boolean isStale(AigModelGovernance g, LocalDateTime staleBefore) {
        if (staleBefore == null) {
            return true;
        }
        // 从未测过（health_time 为空）的一律要测——这正是需要暴露的状态
        return g.getHealthTime() == null || g.getHealthTime().isBefore(staleBefore);
    }

    /**
     * 取"有启用绑定"的模型ID集合。
     *
     * @return 模型ID集合
     */
    private Set<Long> boundModelIds() {
        List<AigCapabilityModel> binds = capabilityModelMapper.selectList(
            new LambdaQueryWrapper<AigCapabilityModel>()
                .eq(AigCapabilityModel::getStatus, "0")
                .eq(AigCapabilityModel::getDelFlag, "0"));
        Set<Long> ids = new HashSet<>();
        for (AigCapabilityModel b : binds) {
            if (b.getModelId() != null) {
                ids.add(b.getModelId());
            }
        }
        return ids;
    }

}
