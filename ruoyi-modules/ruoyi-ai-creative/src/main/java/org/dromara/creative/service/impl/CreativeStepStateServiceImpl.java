package org.dromara.creative.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpProjectStepState;
import org.dromara.creative.domain.DpScenarioStep;
import org.dromara.creative.domain.vo.ProjectStepStateVo;
import org.dromara.creative.helper.CreativeStepProjection;
import org.dromara.creative.mapper.DpProjectStepStateMapper;
import org.dromara.creative.service.ICreativeProjectService;
import org.dromara.creative.service.ICreativeScenarioConfigService;
import org.dromara.creative.service.ICreativeStepStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤状态的人为动作实现（V0.2 R36）。
 *
 * <p>语义与允许条件见 {@link ICreativeStepStateService} 的类注释——那里是口径的权威，
 * 这里只负责把它们落成代码。</p>
 *
 * @author creative
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeStepStateServiceImpl implements ICreativeStepStateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 跳过原因的长度下限与上限（光有"跳过"两个字不算留痕） */
    private static final int REASON_MIN = 2;
    private static final int REASON_MAX = 200;

    private final ICreativeScenarioConfigService scenarioConfigService;
    private final ICreativeProjectService projectService;
    private final DpProjectStepStateMapper stepStateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProjectStepStateVo> skip(Long taskId, String stepCode, String reason) {
        String code = requireStep(taskId, stepCode);
        String why = StringUtils.trimToNull(reason);
        if (why == null || why.length() < REASON_MIN) {
            throw new ServiceException("请写清跳过原因（至少 " + REASON_MIN + " 个字）——"
                + "跳过是人的决定，将来要靠这条记录回答「为什么这一步没做」");
        }
        if (why.length() > REASON_MAX) {
            throw new ServiceException("跳过原因过长（上限 " + REASON_MAX + " 字）");
        }
        ProjectStepStateVo current = find(taskId, code);
        if (current == null) {
            throw new ServiceException("这一步不在该交付类型的流程里，无法跳过：" + code);
        }
        if (CreativeStepProjection.DONE.equals(current.status())) {
            throw new ServiceException("这一步已经做完了，不能再标成跳过：" + current.stepName());
        }
        if (CreativeStepProjection.SKIPPED.equals(current.status())) {
            throw new ServiceException("这一步已经是跳过的状态了：" + current.stepName());
        }
        if (!Boolean.TRUE.equals(current.skippable())) {
            // 两种情况要分开说，否则用户不知道该去找谁
            if (Boolean.TRUE.equals(current.gated())) {
                throw new ServiceException("这一步有闸门审核，不能跳过——跳过等于绕过门禁；"
                    + "请按流程提交并完成审核：" + current.stepName());
            }
            throw new ServiceException("这一步是流程的必填步骤，不能跳过：" + current.stepName());
        }

        DpProjectStepState row = rowOf(taskId, code);
        if (row == null) {
            // 历史软删行会挡住这次写入（本表有唯一键 uk_dp_project_step(task_id, step_code)，
            // 而 del_flag 是逻辑删除）：R36 验收就是在这里撞到 409「数据库中已存在该记录」——
            // 上一版取消跳过来用逻辑删除，留下 del_flag=1 的残行，下一次跳过直接失败。
            // 先物理清掉再插，也让历史上已经被软删的残行有机会被顺手治好。
            int purged = stepStateMapper.hardDelete(taskId, code);
            if (purged > 0) {
                log.warn("项目 {} 步骤 {} 存在软删残行 {} 条，已物理清理（本表不允许留软删行）",
                    taskId, code, purged);
            }
            row = new DpProjectStepState();
            row.setTaskId(taskId);
            row.setStepCode(code);
            row.setStepName(current.stepName());
            row.setSortNo(current.sortNo());
            row.setStatus(CreativeStepProjection.SKIPPED);
            row.setStageCode(current.stageCode());
            row.setRemark(why);
            stepStateMapper.insert(row);
        } else {
            row.setStatus(CreativeStepProjection.SKIPPED);
            row.setRemark(why);
            stepStateMapper.updateById(row);
        }
        List<ProjectStepStateVo> after = scenarioConfigService.listProjectSteps(taskId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepCode", code);
        payload.put("stepName", current.stepName());
        payload.put("reason", why);
        // 把"跳过之后还剩多少步、进度是多少"一起记进事件：将来复盘"这个项目为什么只有 6 步"时，
        // 事件本身就能回答，而不必按**今天**的配置重算（配置可能已经改过，重算只会得到另一个答案）。
        payload.put("progress", progressText(after));
        // 事件归属该步骤：操作日志里能直接看到「谁在什么时候因为什么跳过了这一步」
        projectService.appendEvent(taskId, code, "STEP_SKIPPED", toJson(payload));
        log.info("项目 {} 跳过步骤 {}（原因：{}，进度 {}）", taskId, code, why, progressText(after));
        return after;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProjectStepStateVo> cancelSkip(Long taskId, String stepCode) {
        String code = requireStep(taskId, stepCode);
        DpProjectStepState row = rowOf(taskId, code);
        if (row == null || !CreativeStepProjection.SKIPPED.equals(row.getStatus())) {
            throw new ServiceException("这一步当前不是跳过状态，无需取消：" + code);
        }
        // **物理删除**（不是逻辑删除）：本表有唯一键 uk_dp_project_step(task_id, step_code)，
        // 留下软删行会让这一步之后任何写入都撞唯一键（重新跳过 409、moveStage 的步骤状态同步
        // 409 并把整次阶段推进一起回滚）。删掉之后这一步回到"按当前阶段投影"，
        // 与其他没落库的步骤同一口径——而"曾经跳过、又被谁取消了"由上面那条事件承担。
        stepStateMapper.hardDelete(taskId, code);
        List<ProjectStepStateVo> after = scenarioConfigService.listProjectSteps(taskId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepCode", code);
        payload.put("previousReason", row.getRemark());
        payload.put("progress", progressText(after));
        projectService.appendEvent(taskId, code, "STEP_SKIP_CANCELLED", toJson(payload));
        log.info("项目 {} 取消跳过步骤 {}（进度 {}）", taskId, code, progressText(after));
        return after;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 校验项目存在且未删除，并返回规整后的步骤编码。
     *
     * @param taskId   项目ID
     * @param stepCode 步骤编码
     * @return 步骤编码
     */
    private String requireStep(Long taskId, String stepCode) {
        if (taskId == null) {
            throw new ServiceException("taskId 不能为空。");
        }
        String code = StringUtils.trimToNull(stepCode);
        if (code == null) {
            throw new ServiceException("stepCode 不能为空。");
        }
        // 复用项目服务的存在性校验（它已经处理了删除态与项目不存在两种情况）
        projectService.getProject(taskId);
        return code;
    }

    /**
     * 取某一步的当前视图（不存在返回 null）。
     *
     * @param taskId 项目ID
     * @param code   步骤编码
     * @return 步骤状态
     */
    private ProjectStepStateVo find(Long taskId, String code) {
        return scenarioConfigService.listProjectSteps(taskId).stream()
            .filter(step -> code.equalsIgnoreCase(step.stepCode()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 取该步骤的持久化行（没有返回 null）。
     *
     * @param taskId 项目ID
     * @param code   步骤编码
     * @return 行
     */
    private DpProjectStepState rowOf(Long taskId, String code) {
        List<DpProjectStepState> rows = stepStateMapper.selectList(new LambdaQueryWrapper<DpProjectStepState>()
            .eq(DpProjectStepState::getTaskId, taskId)
            .eq(DpProjectStepState::getStepCode, code)
            .orderByDesc(DpProjectStepState::getId)
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 进度文本（形如 {@code 3/8（其中跳过 1 步）}），口径见 {@link CreativeStepProjection#progress}。
     *
     * @param steps 步骤状态列表（跳过之后的）
     * @return 进度文本
     */
    private String progressText(List<ProjectStepStateVo> steps) {
        int total = steps == null ? 0 : steps.size();
        int done = 0;
        int skipped = 0;
        for (ProjectStepStateVo step : steps == null ? List.<ProjectStepStateVo>of() : steps) {
            if (CreativeStepProjection.DONE.equals(step.status())) {
                done++;
            } else if (CreativeStepProjection.SKIPPED.equals(step.status())) {
                skipped++;
            }
        }
        int[] progress = CreativeStepProjection.progress(total, done, skipped);
        return skipped > 0
            ? progress[0] + "/" + progress[1] + "（其中跳过 " + skipped + " 步）"
            : progress[0] + "/" + progress[1];
    }
}
