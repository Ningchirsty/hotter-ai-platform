package org.dromara.creative.helper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.creative.domain.DpProjectStepState;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.mapper.DpProjectStepStateMapper;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目步骤状态的**唯一写入者**（V0.2 D2）。
 *
 * <p>它只被 {@code CreativeProjectServiceImpl#moveStage} 调用一次——这是有意的最小化：
 * 状态纪律的三条（单一写入点 / visual_stage 兼容投影 / 同时写事件）里，
 * "同时写事件"由 moveStage 原有的 {@code insertEvent} 负责，"单一写入点"由本类只有一个调用方保证。
 * <b>查询路径永不调用这里</b>（读接口自己按阶段推导投影，不写库）。</p>
 *
 * <p>写之前先读一次配置（{@code dp_scenario_step}）：配置读不到就**什么都不写**并记一条日志——
 * 派生表写不了不该让阶段变更失败（阶段与事件才是权威）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeStepStateWriter {

    private final DpProjectStepStateMapper stepStateMapper;
    private final ICreativeScenarioConfigService scenarioConfigService;

    /**
     * 阶段变更后同步步骤状态。
     *
     * @param taskId    项目ID
     * @param stageCode 变更后的阶段编码
     * @param deliveryType 交付类型（决定用哪份步骤配置；空则不写）
     * @return 写入/更新的行数（0 表示没有可用配置或状态未变）
     */
    public int sync(Long taskId, String stageCode, String deliveryType) {
        List<DpScenarioStep> steps = scenarioConfigService.listSteps(deliveryType);
        if (steps.isEmpty()) {
            log.warn("项目 {} 的场景（{}）没有配置步骤，跳过步骤状态同步", taskId, deliveryType);
            return 0;
        }
        List<CreativeStepProjection.ConfiguredStep> configured = steps.stream()
            .map(s -> new CreativeStepProjection.ConfiguredStep(
                s.getStepCode(), s.getStepName(), s.getStageCodes(), s.getSortNo()))
            .toList();
        List<CreativeStepProjection.StepState> projected =
            CreativeStepProjection.project(configured, stageCode);
        Long profileId = steps.get(0).getProfileId();

        // 现有行 → 按 step_code 建索引（本表就十来行，一次查完）
        Map<String, DpProjectStepState> existing = new HashMap<>();
        for (DpProjectStepState row : stepStateMapper.selectList(new LambdaQueryWrapper<DpProjectStepState>()
            .eq(DpProjectStepState::getTaskId, taskId))) {
            existing.put(row.getStepCode(), row);
        }

        LocalDateTime now = LocalDateTime.now();
        int touched = 0;
        for (CreativeStepProjection.StepState state : projected) {
            DpProjectStepState row = existing.get(state.stepCode());
            if (row == null) {
                // 本表有唯一键 uk_dp_project_step(task_id, step_code)，而 del_flag 是逻辑删除列：
                // 只要留下一行软删，这里 insert 就会撞唯一键 → 409 → 整个 moveStage 事务回滚
                // （阶段推进失败）。所以插入前先物理清掉可能的软删残行（正常情况返回 0）。
                int purged = stepStateMapper.hardDelete(taskId, state.stepCode());
                if (purged > 0) {
                    log.warn("项目 {} 步骤 {} 存在软删残行 {} 条，同步前已物理清理（本表不允许留软删行）",
                        taskId, state.stepCode(), purged);
                }
                // 主键交给 MyBatis-Plus（全局 idType=ASSIGN_ID），与 dp_stage_event 的写法一致：
                // 少一处「自己造 ID」的分支，也就少一处能在单测里踩到 Spring 容器的静态依赖。
                DpProjectStepState fresh = new DpProjectStepState();
                fresh.setTaskId(taskId);
                fresh.setProfileId(profileId);
                fresh.setStepCode(state.stepCode());
                fresh.setStepName(state.stepName());
                fresh.setSortNo(state.sortNo());
                fresh.setStatus(state.status());
                fresh.setStageCode(stageCode);
                // 时间戳口径与下面的更新分支一致：ACTIVE/DONE 都算"已经开始"，只有 DONE 算"已完成"。
                // （曾经这里只给 DONE 补 started_at，于是"新项目第一次同步就把某步写成进行中"时
                //  该行没有开始时间——更新分支却会补，两条路径口径不一致。）
                boolean done = CreativeStepProjection.DONE.equals(state.status());
                boolean started = done || CreativeStepProjection.ACTIVE.equals(state.status());
                fresh.setStartedAt(started ? now : null);
                fresh.setCompletedAt(done ? now : null);
                stepStateMapper.insert(fresh);
                touched++;
                continue;
            }
            boolean changed = !state.status().equals(row.getStatus());
            if (!changed) {
                continue;
            }
            // 只补时间戳：进入 ACTIVE 记开始；进入 DONE 记完成（已完成的不要被后来的阶段覆盖成空）
            row.setStatus(state.status());
            row.setStageCode(stageCode);
            row.setStepName(state.stepName());
            row.setSortNo(state.sortNo());
            if (CreativeStepProjection.ACTIVE.equals(state.status()) && row.getStartedAt() == null) {
                row.setStartedAt(now);
            }
            if (CreativeStepProjection.DONE.equals(state.status()) && row.getCompletedAt() == null) {
                row.setCompletedAt(now);
            }
            stepStateMapper.updateById(row);
            touched++;
        }
        if (touched > 0) {
            log.info("项目 {} 步骤状态同步完成：stage={} 更新 {} 行（共 {} 步）",
                taskId, stageCode, touched, projected.size());
        }
        return touched;
    }
}
