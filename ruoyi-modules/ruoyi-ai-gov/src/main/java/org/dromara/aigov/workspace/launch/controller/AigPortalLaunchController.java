package org.dromara.aigov.workspace.launch.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchCommitVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchPrepareVo;
import org.dromara.aigov.workspace.launch.service.IAigLaunchService;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigPortalActorProvider;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位卡片启动接口（主文档线增量 3；附件 §12）。
 *
 * <h3>两个端点、两种语义</h3>
 * <ul>
 *     <li>{@code /prepare}：**无副作用**。算清"会变成什么"、列出问题、通过则给一张短期票据。
 *         它不发任务、不改业务数据（只往 Redis 放一张会自己过期的票）。</li>
 *     <li>{@code /commit}：用户确认后真正启动，**幂等**（同键重放返回同一次结果）。</li>
 * </ul>
 * <p>分开的理由很实际：员工点卡片之后需要一个"确认"界面（要填什么、会建什么任务）。
 * 如果只有一个端点，那个界面就只能靠前端猜——猜错了就是"确认的是 A、执行的是 B"。</p>
 *
 * <h3>与门户一致：{@code @SaCheckLogin}，不用权限点</h3>
 * <p>能启动什么由"岗位对当前用户是否可见 + 卡片是否可用"决定，即**数据范围**，不是权限点。
 * 加一个 {@code aig:workspace:use} 只会让每个角色都得记得勾上，漏了就表现为"某人点不动"。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/portal/launch")
public class AigPortalLaunchController extends BaseController {

    private final IAigLaunchService launchService;
    private final AigPortalActorProvider actorProvider;

    /**
     * 预检并发放票据（无副作用）。
     *
     * @param bo 启动请求
     * @return 预检结果
     */
    @SaCheckLogin
    @RepeatSubmit
    @PostMapping("/prepare")
    public R<AigLaunchPrepareVo> prepare(@RequestBody @Validated AigLaunchRequestBo bo) {
        return R.ok(launchService.prepare(bo, requireActor()));
    }

    /**
     * 确认启动（幂等）。
     *
     * @param bo 启动请求（必须带 ticket）
     * @return 启动结果（业务拒绝以 problems 返回；系统故障仍抛异常）
     */
    @SaCheckLogin
    @RepeatSubmit
    @PostMapping("/commit")
    public R<AigLaunchCommitVo> commit(@RequestBody @Validated AigLaunchRequestBo bo) {
        return R.ok(launchService.commit(bo, requireActor()));
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
