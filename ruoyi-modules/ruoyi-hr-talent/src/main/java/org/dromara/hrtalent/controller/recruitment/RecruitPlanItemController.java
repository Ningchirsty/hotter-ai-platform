package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemVo;
import org.dromara.hrtalent.domain.vo.recruitment.RolloverChainVo;
import org.dromara.hrtalent.service.recruitment.IRecruitPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月度计划任务 控制层（SPEC-P2 §3.2）。
 *
 * <p>路径固定为 {@code /recruit/plan-items}。本层不做任何合并判断：
 * {@code /similar} 只返回「存在相似计划」的提示，新增与结转一律由服务层新建独立记录。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/plan-items")
public class RecruitPlanItemController {

    /**
     * 月度计划服务。
     */
    private final IRecruitPlanService recruitPlanService;

    /**
     * 跨计划分页查询计划任务。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_LIST)
    @GetMapping
    public R<PageResult<RecruitPlanItemVo>> list(RecruitPlanItemQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitPlanService.queryItemPage(bo, pageQuery));
    }

    /**
     * 相似计划提示（同公司 + 同部门 + 同岗位）。
     *
     * <p>仅用于前端提示，<b>不参与</b>任何合并、覆盖或复用逻辑。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 相似任务分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_LIST)
    @GetMapping("/similar")
    public R<PageResult<RecruitPlanItemVo>> similar(RecruitPlanItemQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitPlanService.similar(bo, pageQuery));
    }

    /**
     * 编辑计划任务（仅允许调整人数、负责人、紧急程度等允许字段）。
     *
     * @param id 计划任务ID
     * @param bo 任务入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_EDIT)
    @Log(title = "月度计划任务", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "计划任务ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitPlanItemBo bo) {
        bo.setItemId(id);
        recruitPlanService.updateItem(bo);
        return R.ok();
    }

    /**
     * 执行计划任务动作（pause 暂停 / resume 恢复 / cancel 取消）。
     *
     * @param id     计划任务ID
     * @param action 动作编码
     * @param bo     动作入参（暂停与取消必须填写原因）
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_EDIT)
    @Log(title = "月度计划任务状态流转", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/actions/{action}")
    public R<Void> action(@NotNull(message = "计划任务ID不能为空")
                          @PathVariable("id") Long id,
                          @NotBlank(message = "动作编码不能为空")
                          @PathVariable("action") String action,
                          @Validated @RequestBody(required = false) RecruitPlanItemActionBo bo) {
        RecruitPlanItemActionBo params = bo == null ? new RecruitPlanItemActionBo() : bo;
        params.setItemId(id);
        recruitPlanService.itemAction(id, action, params);
        return R.ok();
    }

    /**
     * 查询计划任务的跨月结转链。
     *
     * @param id 计划任务ID
     * @return 结转链
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_QUERY)
    @GetMapping("/{id}/rollover-chain")
    public R<RolloverChainVo> rolloverChain(@NotNull(message = "计划任务ID不能为空")
                                            @PathVariable("id") Long id) {
        return R.ok(recruitPlanService.rolloverChain(id));
    }

    /**
     * 触发单条计划任务状态重算（管理员手工校准）。
     *
     * @param id 计划任务ID
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_EDIT)
    @Log(title = "月度计划任务状态重算", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/refresh-status")
    public R<Void> refreshStatus(@NotNull(message = "计划任务ID不能为空")
                                 @PathVariable("id") Long id) {
        recruitPlanService.refreshItemStatus(id);
        return R.ok();
    }

}
