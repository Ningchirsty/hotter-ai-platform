package org.dromara.hrtalent.controller.talent;

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
import org.dromara.hrtalent.domain.bo.talent.TalentPrecheckBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileBo;
import org.dromara.hrtalent.domain.bo.talent.TalentProfileQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentPrecheckVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileChangeVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileDetailVo;
import org.dromara.hrtalent.domain.vo.talent.TalentProfileVo;
import org.dromara.hrtalent.service.talent.ITalentProfileService;
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

/**
 * 人才主档 控制层（SPEC-P3 §2.1）。
 *
 * <p>路径固定为 {@code /talent/profiles}，与前端菜单契约一致；本层只做参数接收与组装 {@link R}，
 * 权限串一律取 {@link HrTalentConstants} 常量。可见范围、查重、变更历史与乐观锁全部由
 * {@link ITalentProfileService} 负责。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/profiles")
public class TalentProfileController {

    /**
     * 人才主档服务。
     */
    private final ITalentProfileService talentProfileService;

    /**
     * 分页查询人才主档（自动套人才可见范围，电话/邮箱脱敏）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 人才主档分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_LIST)
    @GetMapping
    public R<PageResult<TalentProfileVo>> list(TalentProfileQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentProfileService.queryPage(bo, pageQuery));
    }

    /**
     * 获取人才主档详情。
     *
     * @param id 人才主档ID
     * @return 人才主档详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}")
    public R<TalentProfileDetailVo> getInfo(@NotNull(message = "人才ID不能为空")
                                            @PathVariable("id") Long id) {
        return R.ok(talentProfileService.getDetail(id));
    }

    /**
     * 人才重复预检（不落库，返回强/中/弱匹配摘要）。
     *
     * @param bo 预检入参
     * @return 分级预检结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_CANDIDATE_ADD)
    @PostMapping("/precheck")
    public R<TalentPrecheckVo> precheck(@Validated @RequestBody TalentPrecheckBo bo) {
        return R.ok(talentProfileService.precheck(bo));
    }

    /**
     * 创建人才主档（入库前查重，强/中匹配时拒绝静默创建）。
     *
     * @param bo 人才主档入参
     * @return 新建的人才主档ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_ADD)
    @Log(title = "人才主档", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody TalentProfileBo bo) {
        return R.ok(talentProfileService.create(bo, true, false, null));
    }

    /**
     * 更新人才主档（关键字段变更写入变更历史，带 version 乐观锁）。
     *
     * @param id 人才主档ID
     * @param bo 人才主档入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才主档", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "人才ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody TalentProfileBo bo) {
        bo.setTalentId(id);
        talentProfileService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除人才主档（仅无应聘记录可删）。
     *
     * @param ids 人才主档ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才主档", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "人才ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        talentProfileService.remove(ids);
        return R.ok();
    }

    /**
     * 归档人才主档。
     *
     * @param id     人才主档ID
     * @param reason 归档原因，可为空
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_ARCHIVE)
    @Log(title = "人才主档归档", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{id}/archive")
    public R<Void> archive(@NotNull(message = "人才ID不能为空")
                           @PathVariable("id") Long id,
                           @RequestParam(value = "reason", required = false) String reason) {
        talentProfileService.archive(id, reason);
        return R.ok();
    }

    /**
     * 查询人才关键字段变更历史。
     *
     * @param id        人才主档ID
     * @param pageQuery 分页参数
     * @return 变更历史分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{id}/changes")
    public R<PageResult<TalentProfileChangeVo>> changes(@NotNull(message = "人才ID不能为空")
                                                        @PathVariable("id") Long id,
                                                        PageQuery pageQuery) {
        return R.ok(talentProfileService.queryChanges(id, pageQuery));
    }

}
