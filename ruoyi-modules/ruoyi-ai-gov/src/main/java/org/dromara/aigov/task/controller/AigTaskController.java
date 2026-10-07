package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskTypeEnum;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 统一任务 控制层（只读）。
 *
 * <p><b>本控制器刻意不提供「改状态」的接口</b>：状态变更必须经
 * {@link IAigTaskService#transition}，它带状态机校验与乐观锁；
 * 若在这里开一个「直接落某个状态」的口子，等于把编排层唯一的正确性保证绕过去了。
 * 人工动作（取消/重试/复核）各自有语义明确的专用接口，且都要求
 * {@code aig:task:operate}。</p>
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
        return vo;
    }

}
