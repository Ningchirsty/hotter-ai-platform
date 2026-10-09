package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultSelectBo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskExecuteVo;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskExecutionModeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskTypeEnum;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 统一任务 控制层。
 *
 * <p><b>本控制器刻意不提供「改状态」的接口</b>：状态变更必须经
 * {@link IAigTaskService#transition}，它带状态机校验与乐观锁；
 * 若在这里开一个「直接落某个状态」的口子，等于把编排层唯一的正确性保证绕过去了。
 * 人工动作（取消/重试/复核）各自有语义明确的专用接口，且都要求
 * {@code aig:task:operate}。</p>
 *
 * <p>唯一例外的写入是<b>建任务</b>（{@code POST /aigov/task}）：它只创建
 * {@code DRAFT} 并把快照冻结，状态推进仍由调度器与既有动作负责——
 * 建任务不是"改状态"，而是把"运维手工起一条平台任务"从"直接写库"拉回正规入口。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/task")
public class AigTaskController {

    private final IAigTaskService taskService;

    private final IAigTaskScheduler taskScheduler;

    private final IAigTaskExecutor taskExecutor;

    /**
     * 登记一条<b>平台执行</b>的任务（人工/运维入口）。
     *
     * <p><b>为什么需要它</b>：此前任务只能由业务域在进程内创建（{@code IAigTaskService.create}），
     * HTTP 面只有查询/取消/重放/执行——于是"运维想手工建一条任务跑一次"这件事<b>只能靠直接写库</b>，
     * 而写库会绕过快照冻结、幂等键与数据等级收紧。补这个接口是为了让那条路有正规入口，
     * 不是为了给业务域用（业务域仍应进程内建任务，少一次 HTTP 往返与鉴权往返）。</p>
     *
     * <p><b>建出来的是 {@code DRAFT}</b>：策略预检由调度器扫描驱动（{@code DRAFT → POLICY_CHECKING →
     * QUEUED/NEED_HUMAN/REJECTED}），因此调用方拿到的是任务ID，随后应查详情看它落到哪；
     * 这里刻意不"建完直接入队"——那会把策略校验这一步跳过。</p>
     *
     * <p><b>权限沿用 {@code aig:task:operate}</b>：建任务会真的产生一次平台执行
     * （可能计费），它与"取消/重放/执行"是同一类动作；不新开权限点也避免多一个要种子菜单行的权限码。</p>
     *
     * @param bo 创建入参（任务类型、能力编码、数据等级、不可变输入快照 JSON 等）
     * @return 任务ID
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping
    public R<Long> create(@Validated @RequestBody AigTaskCreateBo bo) {
        return R.ok(taskService.create(bo));
    }

    /**
     * 分页查询任务。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    @SaCheckPermission(AigConstants.PERM_TASK_LIST)
    @GetMapping("/list")
    public R<PageResult<AigTaskVo>> list(@Validated({Default.class, QueryGroup.class}) AigTaskQueryBo bo,
                                         PageQuery pageQuery) {
        return R.ok(taskService.queryPage(bo, pageQuery));
    }

    /**
     * 取任务详情：任务 + 输入快照 + 事件流 + 候选结果。
     *
     * @param taskId 任务ID
     * @return 详情
     */
    @SaCheckPermission(AigConstants.PERM_TASK_QUERY)
    @GetMapping("/{taskId}")
    public R<AigTaskDetailVo> getInfo(@NotNull(message = "任务ID不能为空")
                                      @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.getDetail(taskId));
    }

    /**
     * 人工请求取消任务（迁移到 CANCEL_REQUESTED，等 Provider 确认）。
     *
     * <p>刻意不直接置 CANCELLED：异步作业一旦提交，取消不是本地能立刻完成的，
     * 对方可能仍在生成并计费。直接置 CANCELLED 会出现「账上已取消、对方仍在跑」，
     * 事后无从对账。</p>
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本（乐观锁）
     * @param reason          取消原因
     * @return 迁移后的任务
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping("/{taskId}/cancel")
    public R<AigTaskVo> cancel(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId,
                               @NotNull(message = "期望版本不能为空") @RequestParam("expectedVersion")
                               Integer expectedVersion,
                               @RequestParam(value = "reason", required = false) String reason) {
        AigTask moved = taskService.transition(taskId, expectedVersion, AigTaskStatusEnum.CANCEL_REQUESTED,
            "人工请求取消" + StringUtils.blankToDefault(reason, ""), null);
        return R.ok(toVo(moved));
    }

    /**
     * 人工把「待人工处理」的任务重新入队（修好输入/配置后重跑）。
     *
     * @param taskId          任务ID
     * @param expectedVersion 期望版本（乐观锁）
     * @param note            说明
     * @return 迁移后的任务
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping("/{taskId}/requeue")
    public R<AigTaskVo> requeue(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId,
                                @NotNull(message = "期望版本不能为空") @RequestParam("expectedVersion")
                                Integer expectedVersion,
                                @RequestParam(value = "note", required = false) String note) {
        AigTask moved = taskService.transition(taskId, expectedVersion, AigTaskStatusEnum.QUEUED,
            "人工重新入队" + StringUtils.blankToDefault(note, ""), null);
        return R.ok(toVo(moved));
    }

    /**
     * 执行一次任务（任务层 → 统一调用入口）。
     *
     * <p><b>这个接口平时不该被前端调</b>：正常路径是业务域建任务后<b>直接调用</b>
     * {@code IAigTaskExecutor}（同进程、无需绕 HTTP）。这里开放出来是为了①联调期手动触发
     * ②运维重跑——两者都要求 {@code aig:task:operate}。</p>
     *
     * <p>提示词与载荷由请求体给出，而不是从快照推导：快照是业务域自己组装的 JSON，
     * 治理层不知道它的字段含义。执行前会校验快照哈希未被改写。</p>
     *
     * @param taskId 任务ID（以路径为准，覆盖请求体里的同名字段，避免两处不一致）
     * @param bo     执行入参（提示词 / 载荷 / 可选预算）
     * @return 执行结果（含任务结局与调用细节）
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping("/{taskId}/execute")
    public R<AigTaskExecuteVo> execute(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId,
                                       @RequestBody(required = false) AigTaskExecuteBo bo) {
        AigTaskExecuteBo payload = bo == null ? new AigTaskExecuteBo() : bo;
        payload.setTaskId(taskId);
        return R.ok(taskExecutor.execute(payload));
    }

    /**
     * 人工选定候选资产（把候选置为「已选定」）。
     *
     * <p>这是「自动流程只筛除、不放行」的唯一出口：自动写入 APPROVED 会被拒绝，
     * 因此候选要成为交付物只能走这里，并记录选定人。</p>
     *
     * @param taskId   任务ID（以路径为准）
     * @param resultId 候选结果ID
     * @param remark   选定说明（写入事件流，便于事后回答「为什么选了它」）
     * @return 选定后的结果ID
     */
    @SaCheckPermission(AigConstants.PERM_TASK_SELECT)
    @RepeatSubmit
    @PostMapping("/{taskId}/result/{resultId}/select")
    public R<Long> selectCandidate(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId,
                                   @NotNull(message = "候选结果ID不能为空")
                                   @PathVariable("resultId") Long resultId,
                                   @RequestParam(value = "remark", required = false) String remark) {
        AigTaskResultSelectBo bo = new AigTaskResultSelectBo();
        bo.setTaskId(taskId);
        bo.setResultId(resultId);
        bo.setRemark(remark);
        return R.ok(taskService.selectCandidate(bo));
    }

    /**
     * 手动触发一次调度扫描（供 SnailJob / 运维 cron 调用）。
     *
     * <p>{@code aigov.task.scheduler.enabled=false} 时这是唯一的触发路径；
     * 多实例同时调用是安全的（推进走乐观锁，抢输的计入 skipped）。</p>
     *
     * @return 扫描结果
     */
    @SaCheckPermission(AigConstants.PERM_TASK_OPERATE)
    @RepeatSubmit
    @PostMapping("/scheduler/sweep")
    public R<AigTaskSweepVo> sweep() {
        return R.ok(taskScheduler.sweep());
    }

    /**
     * 任务实体 → 列表视图（供人工操作的返回值复用同一套字段口径）。
     *
     * @param task 任务实体
     * @return 列表视图
     */
    private AigTaskVo toVo(AigTask task) {
        AigTaskVo vo = new AigTaskVo();
        BeanUtil.copyProperties(task, vo);
        AigTaskStatusEnum status = AigTaskStatusEnum.find(task.getStatus());
        vo.setStatusLabel(status == null ? task.getStatus() : status.getDesc());
        AigTaskTypeEnum type = AigTaskTypeEnum.find(task.getTaskType());
        vo.setTaskTypeLabel(type == null ? task.getTaskType() : type.getDesc());
        AigTaskExecutionModeEnum mode = AigTaskExecutionModeEnum.find(task.getExecutionMode());
        vo.setExecutionModeLabel(mode == null ? task.getExecutionMode() : mode.getDesc());
        return vo;
    }

}
