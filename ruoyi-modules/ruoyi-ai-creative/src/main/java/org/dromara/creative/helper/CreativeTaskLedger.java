package org.dromara.creative.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskTypeEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.state.AigTaskStateMachine;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 创作域与治理层任务账本的接线（设计 §9「新任务走 {@code aig_task}」的起步形态：
 * <b>登记 + 状态回写</b>，用户 2026-10-08 拍定，粒度＝<b>一候选一任务</b>）。
 *
 * <p><b>为什么要有这一层</b>：出图的执行留在图像内核（{@code ImageTaskSubmissionService}），
 * 治理台要看到的是**活任务**而不是只读镜像。所以创作域在提交出图时把这次工作**登记**成一条
 * {@code aig_task}（{@code execution_mode=EXTERNAL}：平台不执行、也不扫描它），
 * 并在两处把结果**回写**过去：内核状态变化、人工选定/否决。</p>
 *
 * <h3>三条刻意的取舍</h3>
 * <ol>
 *     <li><b>登记与候选入库同一事务</b>：登记失败就整笔失败（要么都留、要么都不留）。
 *         静默不登记等于产出了一份没有治理账的东西——那正是本模块一路在堵的口子。</li>
 *     <li><b>回写挂"状态真的变了"这个钩子，且失败不拖垮刷新</b>：{@code refreshRows} 里
 *         {@code applyKernelState} 返回 true 才算变化；写在这里，打开列表（{@code queryPage}
 *         也会调刷新）不会反复写事件。回写是**观测面**，它出问题不该让创作域刷新失败，但必须可见。</li>
 *     <li><b>失败不回落到平台的"重试/转人工"</b>：EXTERNAL 任务用普通 {@link AigTaskStatusEnum}
 *         迁移落 {@code FAILED}，<b>不</b>走 {@code recordFailure} —— 那会按可重试分类算出
 *         {@code RETRY_WAIT}，而平台不会重试业务域的任务，治理台就会显示一个"等待重试"、
 *         但永远没人重试的任务。创作域的"重试"是**新建候选**（新的一行 → 新的一条任务）。</li>
 * </ol>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeTaskLedger {

    /**
     * 业务域编码（{@code aig_task.project_type}）
     */
    public static final String PROJECT_TYPE = "CREATIVE";

    /**
     * Provider 编码（登记与排障定位用；执行方是图像内核）
     */
    public static final String PROVIDER_CODE = "IMAGE_KERNEL";

    /**
     * 能力编码：业务上这次要的是「出图」，与它由内核还是外部模型服务无关
     */
    public static final String CAPABILITY_CODE = "image_generation";

    /**
     * 项目未声明数据等级时的兜底，与创作域既有的 Brain 适配器同口径（那里也是 INTERNAL）
     */
    private static final String DEFAULT_DATA_LEVEL = "INTERNAL";

    private final IAigTaskService taskService;

    /**
     * 登记一条治理任务（幂等；同一候选重复调用返回同一条）。
     *
     * <p>调用点必须在候选**已经入库之后**（幂等键用候选主键），且与候选入库同一个事务。</p>
     *
     * @param row     候选记录（须已入库：id / inputJson / imageTaskId 就绪）
     * @param project 视觉项目（取数据等级这一真相源）
     * @return 治理任务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long register(DpGeneration row, CreativeProjectVo project) {
        if (row == null || row.getId() == null) {
            throw new ServiceException("登记治理任务失败：候选还没有主键（没有它就无法生成稳定的幂等键）");
        }
        if (StringUtils.isBlank(row.getInputJson())) {
            throw new ServiceException("登记治理任务失败：候选没有输入快照（generationId=" + row.getId()
                + "）——没有它事后无法复现，登记也就失去了意义");
        }
        AigTaskCreateBo bo = new AigTaskCreateBo();
        bo.setTaskType(AigTaskTypeEnum.IMAGE_GENERATION.getCode());
        bo.setCapabilityCode(CAPABILITY_CODE);
        bo.setProjectType(PROJECT_TYPE);
        bo.setProjectId(row.getTaskId());
        bo.setDataLevel(resolveDataLevel(project));
        // 出图走本地/内网图像内核：业务侧不允许外发（与内核侧的数据等级约束同向收紧，不放宽）
        bo.setAllowExternal("N");
        bo.setIdempotencyKey(idempotencyKey(row.getId()));
        // 与 dp_generation.input_json 是**同一串字节**：任务的快照哈希与候选的输入留痕指向同一份内容。
        // 不另拼一份——两份快照迟早会漂移，而「当时按什么出的」只能有一个答案
        bo.setSnapshotJson(row.getInputJson());
        bo.setNegativeConstraints(row.getNegativePrompt());
        bo.setRemark("创作域出图候选 #" + row.getCandidateNo() + "（" + row.getWorkflowCode() + "）");
        AigTask task = taskService.createDispatched(bo, PROVIDER_CODE,
            row.getImageTaskId() == null ? null : String.valueOf(row.getImageTaskId()));
        log.info("创作域候选已登记治理任务, generationId={}, aigTaskId={}, imageTaskId={}",
            row.getId(), task.getTaskId(), row.getImageTaskId());
        return task.getTaskId();
    }

    /**
     * 创建幂等键（一候选一任务）。
     *
     * @param generationId 候选ID
     * @return 幂等键
     */
    public static String idempotencyKey(Long generationId) {
        return "creative:generation:" + generationId;
    }

    /**
     * 内核状态变化 → 回写任务状态。
     *
     * <p>只在调用方确认「状态真的变了」之后调用（见 {@code refreshRows} 的注释）。</p>
     *
     * @param row 候选（已更新为新状态）
     * @param to  新状态
     */
    public void writebackKernel(DpGeneration row, DpGenerationStatusEnum to) {
        AigTaskStatusEnum target = CreativeTaskStatusMapper.forKernel(to);
        if (target == null) {
            return;
        }
        AigTask task = loadRegistered(row);
        if (task == null) {
            return;
        }
        if (target == AigTaskStatusEnum.find(task.getStatus())) {
            // 已经是目标状态：不写第二个事件（刷新很频繁，重复事件会把事件流淹掉）
            return;
        }
        StringBuilder detail = new StringBuilder("创作域回写：候选 #").append(row.getId())
            .append(" 内核状态=").append(to.getCode());
        if (StringUtils.isNotBlank(row.getErrorMessage())) {
            detail.append("，原因=").append(row.getErrorMessage());
        }
        try {
            if (target == AigTaskStatusEnum.FAILED) {
                // 失败要走带错误信息的重载：把分类与原因写进 aig_task.error_code/error_message，
                // 否则治理台的「错误码」列永远为空，只知道失败、不知道是哪一类
                taskService.transition(task.getTaskId(), task.getVersion(), target, detail.toString(), null,
                    CreativeTaskStatusMapper.errorClassFor(to), row.getErrorMessage());
            } else {
                taskService.transition(task.getTaskId(), task.getVersion(), target, detail.toString(), null);
            }
        } catch (Exception e) {
            // 回写是观测面：失败不能让创作域刷新失败，但必须留下痕迹
            log.warn("回写任务状态失败（不影响创作域刷新）, generationId={}, aigTaskId={}, target={}: {}",
                row.getId(), task.getTaskId(), target.getCode(), e.getMessage());
        }
    }

    /**
     * 人工结论（选定/筛除）→ 回写任务，走**两步**：{@code REVIEW_PENDING → APPROVED/REJECTED}。
     *
     * @param row      候选
     * @param decision 结论（只认 APPROVED/REJECTED，其它忽略）
     */
    public void writebackDecision(DpGeneration row, DpGenerationStatusEnum decision) {
        List<AigTaskStatusEnum> steps = CreativeTaskStatusMapper.forDecision(decision);
        if (steps.isEmpty()) {
            return;
        }
        AigTask task = loadRegistered(row);
        if (task == null) {
            return;
        }
        for (AigTaskStatusEnum step : steps) {
            AigTaskStatusEnum current = AigTaskStatusEnum.find(task.getStatus());
            if (current == step) {
                continue;
            }
            if (!AigTaskStateMachine.canTransition(current, step)) {
                log.warn("任务侧不接受这次回写（可能已被平台或人工改过）, generationId={}, aigTaskId={}, {}->{}",
                    row.getId(), task.getTaskId(), task.getStatus(), step.getCode());
                return;
            }
            task = taskService.transition(task.getTaskId(), task.getVersion(), step,
                "创作域回写人工结论：" + decision.getDesc() + "（候选 #" + row.getId() + "）", null);
        }
    }

    /**
     * 读该候选已登记的任务。
     *
     * @param row 候选
     * @return 任务；未登记（历史候选）或读不到时返回 null（调用方按「不动」处理）
     */
    private AigTask loadRegistered(DpGeneration row) {
        if (row == null || row.getAigTaskId() == null) {
            return null;
        }
        try {
            return taskService.getTask(row.getAigTaskId());
        } catch (Exception e) {
            log.warn("读治理任务失败, generationId={}, aigTaskId={}: {}",
                row.getId(), row.getAigTaskId(), e.getMessage());
            return null;
        }
    }

    /**
     * 解析数据等级：取项目上声明的那个（唯一的真相源），未声明时按既有口径兜底。
     *
     * @param project 视觉项目
     * @return 数据等级
     */
    private String resolveDataLevel(CreativeProjectVo project) {
        String level = project == null ? null : project.getDataLevel();
        if (StringUtils.isBlank(level)) {
            log.warn("创作项目未声明数据等级，登记治理任务时按 {} 处理（与创作域 Brain 适配器同口径）",
                DEFAULT_DATA_LEVEL);
            return DEFAULT_DATA_LEVEL;
        }
        return level.trim();
    }

}
