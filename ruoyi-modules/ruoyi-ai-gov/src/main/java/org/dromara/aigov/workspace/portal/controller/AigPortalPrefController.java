package org.dromara.aigov.workspace.portal.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalDefaultRoleBo;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalFavoriteBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalPrefVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigPortalActorProvider;
import org.dromara.aigov.workspace.portal.service.IAigPortalPrefService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台偏好接口（主文档线增量 5；附件 §6.1 的 favorites）。
 *
 * <p>与门户其它接口一致：{@code @SaCheckLogin}、**不设权限点**——它是"我自己的偏好"，
 * 范围由登录身份决定。写操作只影响自己那一行（服务层按登录用户取行，不接受调用方指定用户）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/portal")
public class AigPortalPrefController extends BaseController {

    private final IAigPortalPrefService prefService;
    private final AigPortalActorProvider actorProvider;

    /**
     * 读取我的偏好。
     *
     * @return 偏好
     */
    @SaCheckLogin
    @GetMapping("/pref")
    public R<AigPortalPrefVo> pref() {
        return R.ok(prefService.getPref(requireActor()));
    }

    /**
     * 收藏/取消收藏岗位。
     *
     * @param bo 入参
     * @return 更新后的偏好
     */
    @SaCheckLogin
    @RepeatSubmit
    @PostMapping("/favorites")
    public R<AigPortalPrefVo> favorites(@RequestBody @Validated AigPortalFavoriteBo bo) {
        return R.ok(prefService.toggleFavorite(bo, requireActor()));
    }

    /**
     * 设置/清空默认岗位。
     *
     * @param bo 入参（roleCode 留空表示清空）
     * @return 更新后的偏好
     */
    @SaCheckLogin
    @RepeatSubmit
    @PostMapping("/default-role")
    public R<AigPortalPrefVo> defaultRole(@RequestBody @Validated AigPortalDefaultRoleBo bo) {
        return R.ok(prefService.setDefaultRole(bo, requireActor()));
    }

    /**
     * 取当前门户用户；取不到就拒绝。
     *
     * @return 门户用户
     */
    private AigPortalActor requireActor() {
        AigPortalActor actor = actorProvider.currentActor();
        if (actor == null || actor.userId() == null) {
            throw new ServiceException("AI 工作台需要登录用户");
        }
        return actor;
    }

}
