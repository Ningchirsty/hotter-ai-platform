package org.dromara.content.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.content.domain.vo.CpTaskVo;
import org.dromara.content.domain.vo.ContentTaskDetailVo;
import org.dromara.content.helper.ContentGateEngine;
import org.dromara.content.service.IContentTaskService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 内容生产任务 控制层。
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/task")
public class ContentTaskController {

    /**
     * 任务服务
     */
    private final IContentTaskService taskService;

    /**
     * 任务分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_LIST)
    @GetMapping("/list")
    public R<PageResult<CpTaskVo>> list(@Validated({Default.class, QueryGroup.class}) ContentTaskBo bo,
                                        PageQuery pageQuery) {
        return R.ok(taskService.queryPage(bo, pageQuery));
    }

    /**
     * 任务详情（含附件、事实、卡片、作业、闸门结论）。
     *
     * @param taskId 任务ID
     * @return 详情
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_QUERY)
    @GetMapping("/{taskId}")
    public R<ContentTaskDetailVo> getInfo(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.getDetail(taskId));
    }

    /**
     * 新建任务。
     *
     * @param bo 任务参数
     * @return 任务ID
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_ADD)
    @RepeatSubmit
    @Log(title = "内容任务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody ContentTaskBo bo) {
        return R.ok(taskService.create(bo));
    }

    /**
     * 修改任务。
     *
     * @param bo 任务参数
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "内容任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody ContentTaskBo bo) {
        taskService.update(bo);
        return R.ok();
    }

    /**
     * 删除任务。
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_REMOVE)
    @Log(title = "内容任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskId}")
    public R<Void> remove(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        taskService.remove(taskId);
        return R.ok();
    }

    /**
     * 上传资料附件。
     *
     * @param taskId    任务ID
     * @param dataLevel 该文件的数据等级（可空）
     * @param file      文件
     * @return 附件ID
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "内容资料", businessType = BusinessType.INSERT)
    @PostMapping("/{taskId}/file")
    public R<Long> uploadFile(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId,
                              @RequestParam(value = "dataLevel", required = false) String dataLevel,
                              @RequestParam("file") MultipartFile file) {
        return R.ok(taskService.uploadFile(taskId, dataLevel, file));
    }

    /**
     * 附件列表。
     *
     * @param taskId 任务ID
     * @return 附件列表
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_QUERY)
    @GetMapping("/{taskId}/files")
    public R<List<CpTaskFileVo>> files(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.listFiles(taskId));
    }

    /**
     * 触发解析（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "内容任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/parse")
    public R<Long> parse(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.triggerParse(taskId));
    }

    /**
     * 触发预检（异步）。
     *
     * @param taskId 任务ID
     * @return 作业ID
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "内容任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/precheck")
    public R<Long> precheck(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.triggerPrecheck(taskId));
    }

    /**
     * 重算闸门并刷新任务状态。
     *
     * @param taskId 任务ID
     * @return 判定结论
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @PostMapping("/{taskId}/recheck")
    public R<ContentGateEngine.GateResult> recheck(@NotNull(message = "任务ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(taskService.recheck(taskId));
    }

}
