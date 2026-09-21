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
import org.dromara.hrtalent.domain.bo.talent.TalentProfileTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagBo;
import org.dromara.hrtalent.domain.bo.talent.TalentTagQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentTagVo;
import org.dromara.hrtalent.service.talent.ITalentTagService;
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
 * 人才标签 控制层（SPEC-P4 §2.3 C 线）。
 *
 * <p>路径与前端契约一致：标签字典为 {@code /talent/tags}，
 * 人才标签关系为 {@code /talent/profiles/{id}/tags}；
 * 权限串一律取 {@link HrTalentConstants} 常量（字典维护 {@code PERM_PROFILE_EDIT}，查询 {@code PERM_PROFILE_LIST}）。</p>
 *
 * <p><b>敏感标签</b>：服务层禁止创建敏感或歧视性标签，
 * 并保证背调失败、健康、家庭、年龄等敏感内容不会自动生成可被普通用户检索的标签
 * （设计文档 §7.6.4、§8.15）；本层不绕过该校验。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent")
public class TalentTagController {

    /**
     * 人才标签服务。
     */
    private final ITalentTagService talentTagService;

    /**
     * 分页查询标签字典（非管理员的敏感标签默认隐藏）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 标签分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_LIST)
    @GetMapping("/tags")
    public R<PageResult<TalentTagVo>> list(TalentTagQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentTagService.queryPage(bo, pageQuery));
    }

    /**
     * 获取标签详情。
     *
     * @param id 标签ID
     * @return 标签详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_LIST)
    @GetMapping("/tags/{id}")
    public R<TalentTagVo> getInfo(@NotNull(message = "标签ID不能为空")
                                  @PathVariable("id") Long id) {
        return R.ok(talentTagService.getDetail(id));
    }

    /**
     * 新增标签（名称与敏感标记经服务层校验）。
     *
     * @param bo 标签入参
     * @return 新增的标签ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才标签", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/tags")
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody TalentTagBo bo) {
        return R.ok(talentTagService.create(bo));
    }

    /**
     * 更新标签（名称与敏感标记经服务层校验）。
     *
     * @param id 标签ID
     * @param bo 标签入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才标签", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/tags/{id}")
    public R<Void> edit(@NotNull(message = "标签ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody TalentTagBo bo) {
        bo.setTagId(id);
        talentTagService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除标签。
     *
     * @param ids 标签ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才标签", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/tags/{ids}")
    public R<Void> remove(@NotNull(message = "标签ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        talentTagService.remove(ids);
        return R.ok();
    }

    /**
     * 查询某人才当前有效的标签（人才不可见时不返回内容）。
     *
     * @param id 人才主档ID
     * @return 标签列表
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_LIST)
    @GetMapping("/profiles/{id}/tags")
    public R<List<TalentTagVo>> profileTags(@NotNull(message = "人才ID不能为空")
                                            @PathVariable("id") Long id) {
        return R.ok(talentTagService.listProfileTags(id));
    }

    /**
     * 更新人才的标签关系（全量覆盖语义，重复挂载幂等）。
     *
     * @param id 人才主档ID
     * @param bo 标签关系入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EDIT)
    @Log(title = "人才标签关系", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/profiles/{id}/tags")
    public R<Void> updateProfileTags(@NotNull(message = "人才ID不能为空")
                                     @PathVariable("id") Long id,
                                     @Validated @RequestBody TalentProfileTagBo bo) {
        bo.setTalentId(id);
        talentTagService.updateProfileTags(id, bo);
        return R.ok();
    }

}
