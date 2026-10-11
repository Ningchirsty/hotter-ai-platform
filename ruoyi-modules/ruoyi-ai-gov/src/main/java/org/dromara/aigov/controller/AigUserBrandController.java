package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.workspace.portal.domain.bo.AigUserBrandBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigUserBrandVo;
import org.dromara.aigov.workspace.portal.service.IAigUserBrandService;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户↔品牌归属管理（④；运维方式）。
 *
 * <p><b>权限分两档</b>：查看（{@code aig:user-brand:list}）与编辑（{@code aig:user-brand:edit}）。
 * 编辑直接改变"谁能看到按品牌定向的岗位"，所以与查看分开授权。</p>
 *
 * <p><b>它不改角色/数据权限</b>：本接口只登记"某人属于哪些品牌"，这是可见性判定的输入之一；
 * 授给谁权限由既有角色体系管，这里不发明第二套授权。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/user-brand")
public class AigUserBrandController {

    private final IAigUserBrandService userBrandService;

    /**
     * 查某个用户的品牌归属（含已停用的行）。
     *
     * @param userId 用户ID
     * @return 归属清单
     */
    @SaCheckPermission(AigConstants.PERM_USER_BRAND_LIST)
    @GetMapping("/list")
    public R<List<AigUserBrandVo>> list(@RequestParam Long userId) {
        return R.ok(userBrandService.listByUser(userId));
    }

    /**
     * 登记/恢复某人的品牌归属（幂等：同一对 user+brand 只有一条）。
     *
     * @param bo 入参
     * @return 归属记录ID
     */
    @RepeatSubmit
    @SaCheckPermission(AigConstants.PERM_USER_BRAND_EDIT)
    @PostMapping("/grant")
    public R<Long> grant(@RequestBody @Validated AigUserBrandBo bo) {
        return R.ok(userBrandService.grant(bo));
    }

    /**
     * 停用一条归属（不物理删除；重新登记会自动恢复）。
     *
     * @param userBrandId 归属记录ID
     * @return 空
     */
    @SaCheckPermission(AigConstants.PERM_USER_BRAND_EDIT)
    @DeleteMapping("/{userBrandId}")
    public R<Void> revoke(@PathVariable Long userBrandId) {
        userBrandService.revoke(userBrandId);
        return R.ok();
    }

}
