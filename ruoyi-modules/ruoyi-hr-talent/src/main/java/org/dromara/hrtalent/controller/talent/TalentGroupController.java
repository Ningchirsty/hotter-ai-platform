package org.dromara.hrtalent.controller.talent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
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
import org.dromara.hrtalent.domain.bo.talent.TalentGroupBo;
import org.dromara.hrtalent.domain.bo.talent.TalentGroupQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentGroupVo;
import org.dromara.hrtalent.service.talent.ITalentGroupService;
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
 * 人才分组 控制层（SPEC-P4 §2.3 C 线）。
 *
 * <p>路径固定为 {@code /talent/groups}，成员挂 {@code /talent/groups/{id}/members}。</p>
 *
 * <p><b>权限说明（SPEC §2.3）</b>：公共分组与分组成员管理使用
 * {@link HrTalentConstants#PERM_POOL_MEMBER}；<b>个人收藏</b>属于用户个人快速访问，
 * 只要求人才档案列表权限 {@link HrTalentConstants#PERM_PROFILE_LIST} 即可，
 * 因此本控制层的分组接口采用「二者满足其一」的 OR 模式。
 * 权限放宽不会带来越权：服务层对公共分组的创建与维护额外要求人才池管理员
 * （{@code TalentScopeDomainService} 的集团级管理员判定），
 * 且个人收藏<b>不改变人才数据权限</b>（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/groups")
public class TalentGroupController {

    /**
     * 人才分组服务。
     */
    private final ITalentGroupService talentGroupService;

    /**
     * 分页查询分组（公共分组按可见范围，个人收藏只返回本人）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 分组分页结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @GetMapping
    public R<PageResult<TalentGroupVo>> list(TalentGroupQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentGroupService.queryPage(bo, pageQuery));
    }

    /**
     * 获取分组详情。
     *
     * @param id 分组ID
     * @return 分组详情
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @GetMapping("/{id}")
    public R<TalentGroupVo> getInfo(@NotNull(message = "分组ID不能为空")
                                    @PathVariable("id") Long id) {
        return R.ok(talentGroupService.getDetail(id));
    }

    /**
     * 新增分组（公共分组需人才池管理员权限，个人收藏默认归当前用户）。
     *
     * @param bo 分组入参
     * @return 新增的分组ID
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @Log(title = "人才分组", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody TalentGroupBo bo) {
        return R.ok(talentGroupService.create(bo));
    }

    /**
     * 更新分组。
     *
     * @param id 分组ID
     * @param bo 分组入参
     * @return 操作结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @Log(title = "人才分组", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "分组ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody TalentGroupBo bo) {
        bo.setGroupId(id);
        talentGroupService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除分组（只删除分组与成员关系，不删除人才主档）。
     *
     * @param id 分组ID
     * @return 操作结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @Log(title = "人才分组", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{id}")
    public R<Void> remove(@NotNull(message = "分组ID不能为空")
                          @PathVariable("id") Long id) {
        talentGroupService.remove(id);
        return R.ok();
    }

    /**
     * 分页查询分组成员（自动叠加人才可见范围）。
     *
     * @param id        分组ID
     * @param pageQuery 分页参数
     * @return 成员分页结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @GetMapping("/{id}/members")
    public R<PageResult<TalentGroupMemberVo>> members(@NotNull(message = "分组ID不能为空")
                                                      @PathVariable("id") Long id,
                                                      PageQuery pageQuery) {
        return R.ok(talentGroupService.queryMembers(id, pageQuery));
    }

    /**
     * 加入分组（重复加入按幂等处理，返回已有关系）。
     *
     * @param id       分组ID
     * @param talentId 人才主档ID
     * @return 成员关系（新建或已存在）
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @Log(title = "人才分组成员", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{id}/members")
    public R<TalentGroupMemberVo> addMember(@NotNull(message = "分组ID不能为空")
                                            @PathVariable("id") Long id,
                                            @NotNull(message = "人才ID不能为空")
                                            @RequestParam("talentId") Long talentId) {
        return R.ok(talentGroupService.addMember(id, talentId));
    }

    /**
     * 移出分组成员（只结束关系，不删除人才主档）。
     *
     * @param id       分组ID
     * @param memberId 成员关系ID
     * @return 操作结果
     */
    @SaCheckPermission(value = {HrTalentConstants.PERM_POOL_MEMBER, HrTalentConstants.PERM_PROFILE_LIST},
        mode = SaMode.OR)
    @Log(title = "人才分组成员", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@NotNull(message = "分组ID不能为空")
                                @PathVariable("id") Long id,
                                @NotNull(message = "成员关系ID不能为空")
                                @PathVariable("memberId") Long memberId) {
        talentGroupService.removeMember(id, memberId);
        return R.ok();
    }

}
