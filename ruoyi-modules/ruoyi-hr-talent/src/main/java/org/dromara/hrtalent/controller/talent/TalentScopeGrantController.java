package org.dromara.hrtalent.controller.talent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantBo;
import org.dromara.hrtalent.domain.bo.talent.TalentScopeGrantQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentScopeGrantVo;
import org.dromara.hrtalent.service.talent.ITalentScopeGrantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人才共享授权 控制层（SPEC-P4 §2.4、设计文档 §8.19 / §21.14）。
 *
 * <p>路径固定为「新增挂在人才下、撤销与列表挂在授权根下」：
 * {@code POST /talent/profiles/{id}/grants}、{@code GET /talent/profiles/{id}/grants}、
 * {@code GET /talent/grants}、{@code DELETE /talent/grants/{id}}，
 * 权限串一律取 {@link HrTalentConstants} 常量。</p>
 *
 * <p><b>安全边界</b>（设计文档 §8.19）：共享授权只扩大查看范围，
 * <b>不</b>自动授予电话明文、附件下载、背调和导出权限；上述动作各有独立按钮权限，
 * 并由 {@code TalentScopeDomainService#checkPermissionLevel} 单独判定。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent")
public class TalentScopeGrantController {

    /**
     * 人才共享授权服务。
     */
    private final ITalentScopeGrantService talentScopeGrantService;

    /**
     * 分页查询某位人才的授权列表（默认只返回当前有效授权）。
     *
     * @param id        人才主档ID
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 授权分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_LIST)
    @GetMapping("/profiles/{id}/grants")
    public R<PageResult<TalentScopeGrantVo>> listByTalent(@NotNull(message = "人才ID不能为空")
                                                          @PathVariable("id") Long id,
                                                          TalentScopeGrantQueryBo bo,
                                                          PageQuery pageQuery) {
        return R.ok(talentScopeGrantService.queryPage(id, bo, pageQuery));
    }

    /**
     * 分页查询当前用户可见范围内的授权列表（未指定 talentId 时按可见范围过滤）。
     *
     * @param bo        检索条件
     * @param pageQuery 分页参数
     * @return 授权分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_LIST)
    @GetMapping("/grants")
    public R<PageResult<TalentScopeGrantVo>> list(TalentScopeGrantQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentScopeGrantService.queryVisiblePage(bo, pageQuery));
    }

    /**
     * 获取授权详情。
     *
     * @param id 授权ID
     * @return 授权详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_LIST)
    @GetMapping("/grants/{id}")
    public R<TalentScopeGrantVo> getInfo(@NotNull(message = "授权ID不能为空")
                                         @PathVariable("id") Long id) {
        return R.ok(talentScopeGrantService.getDetail(id));
    }

    /**
     * 新增共享授权。
     *
     * @param id 人才主档ID
     * @param bo 授权入参
     * @return 新增的授权ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_ADD)
    @Log(title = "人才共享授权", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/profiles/{id}/grants")
    public R<Long> add(@NotNull(message = "人才ID不能为空")
                       @PathVariable("id") Long id,
                       @Validated({Default.class, AddGroup.class}) @RequestBody TalentScopeGrantBo bo) {
        return R.ok(talentScopeGrantService.create(id, bo));
    }

    /**
     * 撤销共享授权（授权立即失效，保留审计轨迹）。
     *
     * @param id     授权ID
     * @param reason 撤销原因，可为空
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_REVOKE)
    @Log(title = "人才共享授权撤销", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/grants/{id}/revoke")
    public R<Void> revoke(@NotNull(message = "授权ID不能为空")
                          @PathVariable("id") Long id,
                          @RequestParam(value = "reason", required = false) String reason) {
        talentScopeGrantService.revoke(id, reason);
        return R.ok();
    }

    /**
     * 逻辑删除共享授权（数据清理用；日常业务请使用 {@code POST /talent/grants/{id}/revoke}）。
     *
     * @param ids 授权ID集合
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_GRANT_REVOKE)
    @Log(title = "人才共享授权", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/grants/{ids}")
    public R<Void> remove(@NotNull(message = "授权ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        talentScopeGrantService.remove(ids);
        return R.ok();
    }

}
