package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.hrtalent.domain.bo.recruitment.BackgroundDetailViewBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitBackgroundQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundDetailVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitBackgroundVo;
import org.dromara.hrtalent.service.recruitment.IRecruitBackgroundService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招聘背调 控制层（SPEC-P3 §2.4）。
 *
 * <p>路径固定为 {@code /recruit/background-checks}，与前端菜单契约一致；权限串一律取
 * {@link HrTalentConstants} 常量。</p>
 *
 * <p><b>敏感接口</b>：{@code GET /recruit/background-checks/{id}/detail} 使用独立权限
 * {@code recruit:background:view-sensitive}，并在服务层先写 {@code background_view} 审计再返回明细；
 * 列表与普通详情不返回 {@code detail_cipher}（设计文档 §8.7、§15.2）。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/background-checks")
public class RecruitBackgroundController {

    /**
     * 背调服务。
     */
    private final IRecruitBackgroundService recruitBackgroundService;

    /**
     * 分页查询背调记录（不含敏感明细）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 背调分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_BACKGROUND_LIST)
    @GetMapping
    public R<PageResult<RecruitBackgroundVo>> list(RecruitBackgroundQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitBackgroundService.queryPage(bo, pageQuery));
    }

    /**
     * 获取背调普通详情（不含敏感明细）。
     *
     * @param id 背调记录ID
     * @return 背调普通详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_BACKGROUND_LIST)
    @GetMapping("/{id}")
    public R<RecruitBackgroundVo> getInfo(@NotNull(message = "背调记录ID不能为空")
                                          @PathVariable("id") Long id) {
        return R.ok(recruitBackgroundService.getDetail(id));
    }

    /**
     * 新增背调记录。
     *
     * @param bo 背调入参
     * @return 新增的背调记录ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_BACKGROUND_ADD)
    @Log(title = "背调记录", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitBackgroundBo bo) {
        return R.ok(recruitBackgroundService.create(bo));
    }

    /**
     * 更新背调记录。
     *
     * @param id 背调记录ID
     * @param bo 背调入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_BACKGROUND_EDIT)
    @Log(title = "背调记录", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "背调记录ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitBackgroundBo bo) {
        bo.setBackgroundId(id);
        recruitBackgroundService.update(bo);
        return R.ok();
    }

    /**
     * 查看背调敏感明细（独立权限 + 先写审计再返回）。
     *
     * <p>{@code purpose}（查看用途）由服务层强制必填校验：为空时记 {@code denied} 审计并拒绝，
     * 保证「不允许无用途查看」在入参绑定之后仍能留痕。</p>
     *
     * @param id 背调记录ID
     * @param bo 查看用途入参
     * @return 含明文敏感说明的背调明细
     */
    @SaCheckPermission(HrTalentConstants.PERM_BACKGROUND_VIEW_SENSITIVE)
    @GetMapping("/{id}/detail")
    public R<RecruitBackgroundDetailVo> detail(@NotNull(message = "背调记录ID不能为空")
                                               @PathVariable("id") Long id,
                                               @Validated BackgroundDetailViewBo bo) {
        return R.ok(recruitBackgroundService.viewDetail(id, bo == null ? null : bo.getPurpose()));
    }

}
