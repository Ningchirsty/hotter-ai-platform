package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.TlTalentAccessGrant;
import org.dromara.talent.domain.bo.TlAccessGrantBo;
import org.dromara.talent.domain.bo.TlTalentArchiveBo;
import org.dromara.talent.domain.bo.TlTalentBo;
import org.dromara.talent.domain.bo.TlTalentContactBo;
import org.dromara.talent.domain.bo.TlTalentQueryBo;
import org.dromara.talent.domain.vo.TlTalentContactVo;
import org.dromara.talent.domain.vo.TlTalentDetailVo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;
import org.dromara.talent.domain.vo.TlTalentVo;
import org.dromara.talent.service.ITalentAccessGrantService;
import org.dromara.talent.service.ITalentProfileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人才档案 控制层
 *
 * <p>本层只做参数接收、调用 Service、组装 {@link R}；
 * 区域数据范围、单条授权、字段脱敏与审计一律由 Service + TalentScopeHelper 负责。</p>
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/profile")
public class TalentProfileController {

    private final ITalentProfileService talentProfileService;
    private final ITalentAccessGrantService talentAccessGrantService;

    /**
     * 分页查询人才档案列表。
     *
     * @param bo        查询条件（regionCode 仅作过滤，不作数据范围依据）
     * @param pageQuery 分页参数
     * @return 人才档案分页结果
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_LIST)
    @GetMapping("/list")
    public R<PageResult<TlTalentVo>> list(@Validated({Default.class, QueryGroup.class}) TlTalentQueryBo bo,
                                          PageQuery pageQuery) {
        return R.ok(talentProfileService.queryPage(bo, pageQuery));
    }

    /**
     * 获取人才档案详细信息（Service 内执行区域/单条授权二次校验）。
     *
     * @param talentId 人才ID
     * @return 人才档案详情
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/{talentId}")
    public R<TlTalentDetailVo> getInfo(@NotNull(message = "主键不能为空")
                                       @PathVariable("talentId") Long talentId) {
        return R.ok(talentProfileService.getDetail(talentId));
    }

    /**
     * 获取人才完整手机号明文（Service 内写 VIEW_FULL_PHONE 审计）。
     *
     * @param talentId 人才ID
     * @return 完整手机号
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_PHONE)
    @GetMapping("/fullPhone/{talentId}")
    public R<String> fullPhone(@NotNull(message = "主键不能为空")
                               @PathVariable("talentId") Long talentId) {
        return R.ok(talentProfileService.getFullPhone(talentId));
    }

    /**
     * 重复预检（不落库）。
     *
     * @param bo 人才档案参数
     * @return 疑似重复清单
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_ADD)
    @PostMapping("/preCheck")
    public R<List<TlTalentDuplicateVo>> preCheck(@Validated({Default.class, AddGroup.class})
                                                 @RequestBody TlTalentBo bo) {
        return R.ok(talentProfileService.preCheck(bo));
    }

    /**
     * 新增人才档案。
     *
     * @param bo 人才档案参数
     * @return 新增的人才ID
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_ADD)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody TlTalentBo bo) {
        return R.ok(talentProfileService.create(bo));
    }

    /**
     * 修改人才档案。
     *
     * @param bo 人才档案参数
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_EDIT)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody TlTalentBo bo) {
        talentProfileService.update(bo);
        return R.ok();
    }

    /**
     * 归档人才档案。
     *
     * @param bo 归档参数
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_ARCHIVE)
    @RepeatSubmit
    @PutMapping("/archive")
    public R<Void> archive(@Validated({Default.class, EditGroup.class}) @RequestBody TlTalentArchiveBo bo) {
        talentProfileService.archive(bo);
        return R.ok();
    }

    /**
     * 查询人才的联系/面试反馈列表。
     *
     * @param talentId 人才ID
     * @return 联系反馈列表
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_QUERY)
    @GetMapping("/contact/{talentId}")
    public R<List<TlTalentContactVo>> contactList(@NotNull(message = "主键不能为空")
                                                  @PathVariable("talentId") Long talentId) {
        return R.ok(talentProfileService.listContacts(talentId));
    }

    /**
     * 新增联系/面试反馈（不改档案主字段）。
     *
     * @param bo 联系反馈参数
     * @return 新增的联系反馈ID
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_QUERY)
    @RepeatSubmit
    @PostMapping("/contact")
    public R<Long> addContact(@Validated({Default.class, AddGroup.class}) @RequestBody TlTalentContactBo bo) {
        return R.ok(talentProfileService.addContact(bo));
    }

    /**
     * 查询人才的单条授权列表。
     *
     * @param talentId 人才ID
     * @return 授权列表
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_GRANT)
    @GetMapping("/grant/{talentId}")
    public R<List<TlTalentAccessGrant>> grantList(@NotNull(message = "主键不能为空")
                                                  @PathVariable("talentId") Long talentId) {
        return R.ok(talentAccessGrantService.listByTalent(talentId));
    }

    /**
     * 新增单条授权。
     *
     * @param bo 授权参数
     * @return 新增的授权ID
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_GRANT)
    @RepeatSubmit
    @PostMapping("/grant")
    public R<Long> grantAdd(@Validated({Default.class, AddGroup.class}) @RequestBody TlAccessGrantBo bo) {
        return R.ok(talentAccessGrantService.create(bo));
    }

    /**
     * 撤销单条授权。
     *
     * @param grantId 授权ID
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_GRANT)
    @RepeatSubmit
    @DeleteMapping("/grant/{grantId}")
    public R<Void> grantRevoke(@NotNull(message = "主键不能为空")
                               @PathVariable("grantId") Long grantId) {
        talentAccessGrantService.revoke(grantId);
        return R.ok();
    }
}
