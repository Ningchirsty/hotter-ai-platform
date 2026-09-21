package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanItemQueryBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitPlanQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanVo;
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
 * 公司月度计划 控制层（SPEC-P2 §3.2）。
 *
 * <p>路径固定为 {@code /recruit/plans}，与前端菜单契约一致；权限串一律取
 * {@link HrTalentConstants} 常量。表头唯一性、任务不合并、状态机与人数口径全部由
 * {@link IRecruitPlanService} 负责。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/plans")
public class RecruitPlanController {

    /**
     * 月度计划服务。
     */
    private final IRecruitPlanService recruitPlanService;

    /**
     * 分页查询公司月度计划表头。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 计划分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_LIST)
    @GetMapping
    public R<PageResult<RecruitPlanVo>> list(RecruitPlanQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitPlanService.queryPage(bo, pageQuery));
    }

    /**
     * 获取月度计划详情。
     *
     * @param id 计划ID
     * @return 计划详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_QUERY)
    @GetMapping("/{id}")
    public R<RecruitPlanVo> getInfo(@NotNull(message = "计划ID不能为空")
                                    @PathVariable("id") Long id) {
        return R.ok(recruitPlanService.getDetail(id));
    }

    /**
     * 新建公司月度计划表头。
     *
     * @param bo 计划入参
     * @return 新增的计划ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_ADD)
    @Log(title = "公司月度计划", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitPlanBo bo) {
        return R.ok(recruitPlanService.create(bo));
    }

    /**
     * 修改公司月度计划表头（仅草稿；不允许修改公司与计划月份）。
     *
     * @param id 计划ID
     * @param bo 计划入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_EDIT)
    @Log(title = "公司月度计划", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "计划ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitPlanBo bo) {
        bo.setPlanId(id);
        recruitPlanService.update(bo);
        return R.ok();
    }

    /**
     * 执行计划表头动作（confirm 确认 / close 关闭）。
     *
     * @param id     计划ID
     * @param action 动作编码
     * @return 操作结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_PLAN_CONFIRM, HrTalentConstants.PERM_PLAN_CLOSE},
        mode = SaMode.OR)
    @Log(title = "公司月度计划状态流转", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/actions/{action}")
    public R<Void> action(@NotNull(message = "计划ID不能为空")
                          @PathVariable("id") Long id,
                          @NotBlank(message = "动作编码不能为空")
                          @PathVariable("action") String action) {
        recruitPlanService.action(id, action);
        return R.ok();
    }

    /**
     * 分页查询指定计划表头下的计划任务。
     *
     * @param planId    计划表头ID
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 任务分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_QUERY)
    @GetMapping("/{planId}/items")
    public R<PageResult<RecruitPlanItemVo>> items(@NotNull(message = "计划ID不能为空")
                                                  @PathVariable("planId") Long planId,
                                                  RecruitPlanItemQueryBo bo,
                                                  PageQuery pageQuery) {
        return R.ok(recruitPlanService.queryItemPageOfPlan(planId, bo, pageQuery));
    }

    /**
     * 在计划表头下新增独立计划任务（即使存在相同公司 / 部门 / 岗位也新建，不合并）。
     *
     * @param planId 计划表头ID
     * @param bo     任务入参
     * @return 新增的任务ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PLAN_ADD)
    @Log(title = "月度计划任务", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{planId}/items")
    public R<Long> addItem(@NotNull(message = "计划ID不能为空")
                           @PathVariable("planId") Long planId,
                           @Validated({Default.class, AddGroup.class}) @RequestBody RecruitPlanItemBo bo) {
        return R.ok(recruitPlanService.addItem(planId, bo));
    }

}
