package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentFactManualBo;
import org.dromara.content.domain.vo.CpFactSnapshotVo;
import org.dromara.content.domain.vo.ContentFactFieldOptionVo;
import org.dromara.content.service.IContentFactService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品事实快照 控制层。
 *
 * <p><b>权限说明</b>：确认/否决事实属于「推进任务」的编辑动作，故复用
 * {@code content:task:edit}，未另建权限点。互动卡上的裁定走 {@code content:card:handle}。</p>
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/fact")
public class ContentFactController {

    /**
     * 事实服务
     */
    private final IContentFactService factService;

    /**
     * 按任务列出事实行。
     *
     * @param taskId 任务ID
     * @return 事实行
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_QUERY)
    @GetMapping("/list")
    public R<List<CpFactSnapshotVo>> list(@NotNull(message = "任务ID不能为空") @RequestParam("taskId") Long taskId) {
        return R.ok(factService.list(taskId));
    }

    /**
     * 确认单条候选值。
     *
     * @param snapshotId 快照行ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "产品事实", businessType = BusinessType.UPDATE)
    @PostMapping("/{snapshotId}/confirm")
    public R<Void> confirm(@NotNull(message = "事实行ID不能为空") @PathVariable("snapshotId") Long snapshotId) {
        factService.confirm(snapshotId);
        return R.ok();
    }

    /**
     * 否决单条候选值。
     *
     * @param snapshotId 快照行ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "产品事实", businessType = BusinessType.UPDATE)
    @PostMapping("/{snapshotId}/reject")
    public R<Void> reject(@NotNull(message = "事实行ID不能为空") @PathVariable("snapshotId") Long snapshotId) {
        factService.reject(snapshotId);
        return R.ok();
    }

    /**
     * 一键确认无争议项（同字段只有一个候选的行）。
     *
     * @param taskId 任务ID
     * @return 本次确认条数
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "产品事实", businessType = BusinessType.UPDATE)
    @PostMapping("/confirmUnambiguous")
    public R<Integer> confirmUnambiguous(@NotNull(message = "任务ID不能为空") @RequestParam("taskId") Long taskId) {
        return R.ok(factService.confirmUnambiguous(taskId));
    }

    /**
     * 人工录入事实。
     *
     * @param bo 录入参数
     * @return 新增快照行ID
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "产品事实", businessType = BusinessType.INSERT)
    @PostMapping("/manual")
    public R<Long> manual(@Validated @RequestBody ContentFactManualBo bo) {
        return R.ok(factService.addManual(bo.getTaskId(), bo.getFieldCode(), bo.getValue(), bo.getRemark()));
    }

    /**
     * 某任务可录入的事实字段选项。
     *
     * <p>供「手工录入事实」把字段编码做成下拉：闸门只认与规则完全一致的编码，
     * 让用户手打编码等于给了一个必然踩空的机会。权限复用 {@code content:task:query}
     * （只是读任务相关的字段清单），不强制要求闸门规则的维护权限。</p>
     *
     * @param taskId 任务ID
     * @return 字段选项（本交付类型的闸门要求项在前）
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_QUERY)
    @GetMapping("/fieldOptions")
    public R<List<ContentFactFieldOptionVo>> fieldOptions(
        @NotNull(message = "任务ID不能为空") @RequestParam("taskId") Long taskId) {
        return R.ok(factService.fieldOptions(taskId));
    }

}
