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
import org.dromara.hrtalent.domain.bo.talent.TalentPoolBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolMemberBo;
import org.dromara.hrtalent.domain.bo.talent.TalentPoolQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolMemberVo;
import org.dromara.hrtalent.domain.vo.talent.TalentPoolVo;
import org.dromara.hrtalent.service.talent.ITalentPoolService;
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
 * 人才池 控制层（SPEC-P4 §2.3 C 线）。
 *
 * <p>路径固定为 {@code /talent/pools}，与前端菜单契约一致；本层只做参数接收与组装 {@link R}，
 * 权限串一律取 {@link HrTalentConstants} 常量。</p>
 *
 * <p><b>关键契约</b>：{@code POST /talent/pools/{poolId}/members} <b>重复加入返回已有关系</b>，
 * 不返回错误；{@code DELETE /talent/pools/{poolId}/members/{memberId}}
 * <b>只结束成员关系，不删除人才主档</b>（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/pools")
public class TalentPoolController {

    /**
     * 人才池服务。
     */
    private final ITalentPoolService talentPoolService;

    /**
     * 分页查询人才池（自动套可见范围）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 人才池分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_LIST)
    @GetMapping
    public R<PageResult<TalentPoolVo>> list(TalentPoolQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentPoolService.queryPage(bo, pageQuery));
    }

    /**
     * 获取人才池详情。
     *
     * @param id 人才池ID
     * @return 人才池详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_LIST)
    @GetMapping("/{id}")
    public R<TalentPoolVo> getInfo(@NotNull(message = "人才池ID不能为空")
                                   @PathVariable("id") Long id) {
        return R.ok(talentPoolService.getDetail(id));
    }

    /**
     * 新增人才池。
     *
     * @param bo 人才池入参
     * @return 新增的人才池ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_ADD)
    @Log(title = "人才池", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody TalentPoolBo bo) {
        return R.ok(talentPoolService.create(bo));
    }

    /**
     * 更新人才池。
     *
     * @param id 人才池ID
     * @param bo 人才池入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_EDIT)
    @Log(title = "人才池", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "人才池ID不能为空")
                        @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody TalentPoolBo bo) {
        bo.setPoolId(id);
        talentPoolService.update(bo);
        return R.ok();
    }

    /**
     * 逻辑删除人才池（只结束成员关系，不删除人才主档）。
     *
     * @param ids 人才池ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_EDIT)
    @Log(title = "人才池", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "人才池ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        talentPoolService.remove(ids);
        return R.ok();
    }

    /**
     * 分页查询池成员（自动叠加人才可见范围）。
     *
     * @param poolId       人才池ID
     * @param memberStatus 成员状态过滤，可为空
     * @param pageQuery    分页参数
     * @return 成员分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_LIST)
    @GetMapping("/{poolId}/members")
    public R<PageResult<TalentPoolMemberVo>> members(@NotNull(message = "人才池ID不能为空")
                                                     @PathVariable("poolId") Long poolId,
                                                     @RequestParam(value = "memberStatus", required = false) String memberStatus,
                                                     PageQuery pageQuery) {
        return R.ok(talentPoolService.queryMembers(poolId, memberStatus, pageQuery));
    }

    /**
     * 加入人才池（重复加入按幂等处理，返回已有关系）。
     *
     * @param poolId 人才池ID
     * @param bo     成员入参
     * @return 成员关系（新建或已存在）
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_MEMBER)
    @Log(title = "人才池成员", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{poolId}/members")
    public R<TalentPoolMemberVo> addMember(@NotNull(message = "人才池ID不能为空")
                                           @PathVariable("poolId") Long poolId,
                                           @Validated @RequestBody TalentPoolMemberBo bo) {
        return R.ok(talentPoolService.addMember(poolId, bo));
    }

    /**
     * 移出人才池成员（只结束成员关系，不删除人才主档）。
     *
     * @param poolId   人才池ID
     * @param memberId 成员关系ID
     * @param reason   移出原因，可为空
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_POOL_MEMBER)
    @Log(title = "人才池成员", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{poolId}/members/{memberId}")
    public R<Void> removeMember(@NotNull(message = "人才池ID不能为空")
                                @PathVariable("poolId") Long poolId,
                                @NotNull(message = "成员关系ID不能为空")
                                @PathVariable("memberId") Long memberId,
                                @RequestParam(value = "reason", required = false) String reason) {
        talentPoolService.removeMember(poolId, memberId, reason);
        return R.ok();
    }

}
