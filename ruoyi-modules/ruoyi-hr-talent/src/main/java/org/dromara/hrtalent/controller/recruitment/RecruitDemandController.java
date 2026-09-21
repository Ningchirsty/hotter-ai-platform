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
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandActionBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitDemandQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandChangeVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitDemandVo;
import org.dromara.hrtalent.service.recruitment.IRecruitDemandService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 招聘需求 控制层（SPEC-P2 §3.1）。
 *
 * <p>本层只做参数接收与组装 {@link R}；权限判定、状态机、变更留痕与乐观锁一律由
 * {@link IRecruitDemandService} 负责。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/demands")
public class RecruitDemandController {

    /**
     * 招聘需求服务。
     */
    private final IRecruitDemandService demandService;

    /**
     * 分页查询招聘需求（自动应用数据权限）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 招聘需求分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_LIST)
    @GetMapping
    public R<PageResult<RecruitDemandVo>> list(RecruitDemandQueryBo bo, PageQuery pageQuery) {
        return R.ok(demandService.queryPage(bo, pageQuery));
    }

    /**
     * 获取招聘需求详情。
     *
     * @param id 需求ID
     * @return 需求详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_QUERY)
    @GetMapping("/{id}")
    public R<RecruitDemandVo> getInfo(@NotNull(message = "需求ID不能为空")
                                      @PathVariable("id") Long id) {
        return R.ok(demandService.getDetail(id));
    }

    /**
     * 新增招聘需求（草稿）。
     *
     * @param bo 需求入参
     * @return 新增的需求ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_ADD)
    @Log(title = "招聘需求", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitDemandBo bo) {
        return R.ok(demandService.create(bo));
    }

    /**
     * 更新招聘需求（带 version 乐观锁）。
     *
     * @param id 需求ID
     * @param bo 需求入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_EDIT)
    @Log(title = "招聘需求", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "需求ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitDemandBo bo) {
        bo.setDemandId(id);
        demandService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除招聘需求（仅草稿可删）。
     *
     * @param ids 需求ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_EDIT)
    @Log(title = "招聘需求", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "需求ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        demandService.remove(ids);
        return R.ok();
    }

    /**
     * 执行招聘需求状态动作（submit/confirm/pause/resume/complete/close）。
     *
     * <p>动作与权限的精确映射在服务层判定；此处只做粗粒度放行，
     * 避免前端传入动作与权限串不一致时绕过校验。
     * 其中 {@code confirm}（提交确认，进入招聘中）复用
     * {@link HrTalentConstants#PERM_DEMAND_SUBMIT}。</p>
     *
     * @param id     需求ID
     * @param action 动作编码
     * @param bo     动作入参（暂停、复开、关闭必须填写原因）
     * @return 操作结果
     */
    @SaCheckPermission(value = {
        HrTalentConstants.PERM_DEMAND_SUBMIT,
        HrTalentConstants.PERM_DEMAND_PAUSE,
        HrTalentConstants.PERM_DEMAND_CLOSE
    }, mode = SaMode.OR)
    @Log(title = "招聘需求状态流转", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/actions/{action}")
    public R<Void> action(@NotNull(message = "需求ID不能为空")
                          @PathVariable("id") Long id,
                          @NotBlank(message = "动作编码不能为空")
                          @PathVariable("action") String action,
                          @Validated @RequestBody(required = false) RecruitDemandActionBo bo) {
        demandService.action(id, action, bo);
        return R.ok();
    }

    /**
     * 查询招聘需求变更历史。
     *
     * @param id 需求ID
     * @return 变更历史列表（按操作时间倒序）
     */
    @SaCheckPermission(HrTalentConstants.PERM_DEMAND_QUERY)
    @GetMapping("/{id}/changes")
    public R<List<RecruitDemandChangeVo>> changes(@NotNull(message = "需求ID不能为空")
                                                  @PathVariable("id") Long id) {
        return R.ok(demandService.queryChanges(id));
    }

}
