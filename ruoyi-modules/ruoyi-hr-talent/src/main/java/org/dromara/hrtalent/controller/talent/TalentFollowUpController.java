package org.dromara.hrtalent.controller.talent;

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
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpBo;
import org.dromara.hrtalent.domain.bo.talent.TalentFollowUpQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentFollowUpVo;
import org.dromara.hrtalent.service.talent.ITalentFollowUpService;
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
 * 人才跟进 控制层（SPEC-P4 §2.4、设计文档 §8.16）。
 *
 * <p>路径固定为 {@code /talent/profiles/{id}/follow-ups}，与前端菜单契约一致；
 * 本层只做参数接收与组装 {@link R}，权限串一律取 {@link HrTalentConstants} 常量。</p>
 *
 * <p><b>边界</b>：跟进与招聘阶段历史分开（§8.16）——本控制器不提供也不触发任何应聘阶段流转接口，
 * 跟进记录只落 {@code hr_talent_follow_up}。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/profiles/{id}/follow-ups")
public class TalentFollowUpController {

    /**
     * 人才跟进服务。
     */
    private final ITalentFollowUpService talentFollowUpService;

    /**
     * 分页查询某位人才的跟进记录（访问前校验该人才可见）。
     *
     * @param id        人才主档ID
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 跟进记录分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_LIST)
    @GetMapping
    public R<PageResult<TalentFollowUpVo>> list(@NotNull(message = "人才ID不能为空")
                                               @PathVariable("id") Long id,
                                               TalentFollowUpQueryBo bo,
                                               PageQuery pageQuery) {
        return R.ok(talentFollowUpService.queryPage(id, bo, pageQuery));
    }

    /**
     * 新增跟进记录。
     *
     * @param id 人才主档ID
     * @param bo 跟进入参
     * @return 新增的跟进记录ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才跟进", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@NotNull(message = "人才ID不能为空")
                       @PathVariable("id") Long id,
                       @Validated({Default.class, AddGroup.class}) @RequestBody TalentFollowUpBo bo) {
        return R.ok(talentFollowUpService.create(id, bo));
    }

    /**
     * 更新跟进记录（人才归属不可通过本接口迁移）。
     *
     * @param id       人才主档ID
     * @param followId 跟进记录ID
     * @param bo       跟进入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才跟进", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{followId}")
    public R<Void> edit(@NotNull(message = "人才ID不能为空")
                        @PathVariable("id") Long id,
                        @NotNull(message = "跟进记录ID不能为空")
                        @PathVariable("followId") Long followId,
                        @Validated({Default.class, EditGroup.class}) @RequestBody TalentFollowUpBo bo) {
        bo.setFollowId(followId);
        bo.setTalentId(id);
        talentFollowUpService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除跟进记录。
     *
     * @param id        人才主档ID
     * @param followIds 跟进记录ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才跟进", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{followIds}")
    public R<Void> remove(@NotNull(message = "人才ID不能为空")
                          @PathVariable("id") Long id,
                          @PathVariable("followIds") Long[] followIds) {
        talentFollowUpService.remove(followIds);
        return R.ok();
    }

}
