package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobAssignBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitJobVo;
import org.dromara.hrtalent.service.recruitment.IRecruitJobService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招聘岗位执行项 控制层（SPEC-P2 §3.4）。
 *
 * <p>路径固定为 {@code /recruit/jobs}，与前端菜单契约一致；本层只做参数接收与组装 {@link R}，
 * 权限串一律取 {@link HrTalentConstants} 常量。状态机、薪资与枚举校验、乐观锁与人员分配
 * 全部由 {@link IRecruitJobService} 负责。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/jobs")
public class RecruitJobController {

    /**
     * 岗位执行项服务。
     */
    private final IRecruitJobService recruitJobService;

    /**
     * 分页查询岗位执行项。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 岗位分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_LIST)
    @GetMapping
    public R<PageResult<RecruitJobVo>> list(RecruitJobQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitJobService.queryPage(bo, pageQuery));
    }

    /**
     * 获取岗位详情。
     *
     * @param id 岗位执行项ID
     * @return 岗位详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_QUERY)
    @GetMapping("/{id}")
    public R<RecruitJobVo> getInfo(@NotNull(message = "岗位ID不能为空")
                                   @PathVariable("id") Long id) {
        return R.ok(recruitJobService.getDetail(id));
    }

    /**
     * 新增岗位执行项。
     *
     * @param bo 岗位入参
     * @return 新增的岗位执行项ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_ADD)
    @Log(title = "岗位执行项", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitJobBo bo) {
        return R.ok(recruitJobService.create(bo));
    }

    /**
     * 更新岗位执行项（带 version 乐观锁）。
     *
     * @param id 岗位执行项ID
     * @param bo 岗位入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_EDIT)
    @Log(title = "岗位执行项", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "岗位ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitJobBo bo) {
        bo.setJobId(id);
        recruitJobService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除岗位执行项。
     *
     * @param ids 岗位执行项ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_EDIT)
    @Log(title = "岗位执行项", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "岗位ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        recruitJobService.remove(ids);
        return R.ok();
    }

    /**
     * 执行岗位动作（close 关闭 / reopen 重新开放）。
     *
     * @param id     岗位执行项ID
     * @param action 动作编码
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_CLOSE)
    @Log(title = "岗位执行项状态流转", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/actions/{action}")
    public R<Void> action(@NotNull(message = "岗位ID不能为空")
                          @PathVariable("id") Long id,
                          @NotBlank(message = "动作编码不能为空")
                          @PathVariable("action") String action) {
        recruitJobService.action(id, action);
        return R.ok();
    }

    /**
     * 分配岗位人员（招聘负责人、协助人、一面/二面面试官）。
     *
     * <p>该接口只调整上述人员字段，不修改岗位状态、名称、薪资等其它字段。</p>
     *
     * @param id 岗位执行项ID
     * @param bo 分配入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_JOB_ASSIGN)
    @Log(title = "岗位执行项分配", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/assign")
    public R<Void> assign(@NotNull(message = "岗位ID不能为空")
                          @PathVariable("id") Long id,
                          @Validated @RequestBody RecruitJobAssignBo bo) {
        bo.setJobId(id);
        recruitJobService.assign(bo);
        return R.ok();
    }

}
