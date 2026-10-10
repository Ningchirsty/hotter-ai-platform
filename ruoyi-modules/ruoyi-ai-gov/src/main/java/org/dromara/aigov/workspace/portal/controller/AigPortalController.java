package org.dromara.aigov.workspace.portal.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalTaskVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigPortalActorProvider;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工 AI 工作台（门户）只读接口（主文档线增量 2；附件 §7、§9）。
 *
 * <h3>为什么用 {@code @SaCheckLogin} 而不是权限点</h3>
 * <p>这个入口对<b>每一个员工</b>开放，它只返回"当前用户自己"能看到的东西
 * （岗位由绑定决定、任务由登录用户决定）。给它加一个 {@code aig:portal:view} 权限点，
 * 等于发明一个"谁能使用员工工作台"的授权面——而答案永远是"所有员工"，
 * 于是运维每建一个角色都得记得勾上，漏了就表现为"某个人打开是空白"。
 * 真正的边界是<b>数据范围</b>（绑定 + 登录用户），不是权限点。</p>
 *
 * <p>反过来说：管理端接口（`/aigov/roles`）必须保持权限点，因为那些接口能改全平台配置。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/portal")
public class AigPortalController extends BaseController {

    private final IAigPortalService portalService;
    private final AigPortalActorProvider actorProvider;

    /**
     * 我的岗位（当前用户可见的已发布岗位）。
     *
     * @return 岗位清单
     */
    @SaCheckLogin
    @GetMapping("/roles")
    public R<List<AigPortalRoleVo>> roles() {
        return R.ok(portalService.listMyRoles(requireActor()));
    }

    /**
     * 岗位首页（服务端过滤后的卡片）。
     *
     * @param roleCode 岗位编码
     * @return 岗位首页
     */
    @SaCheckLogin
    @GetMapping("/roles/{roleCode}/home")
    public R<AigPortalRoleHomeVo> roleHome(@PathVariable String roleCode) {
        return R.ok(portalService.getRoleHome(roleCode, requireActor()));
    }

    /**
     * 我的任务（范围恒为当前用户）。
     *
     * @param bo        查询条件（提交人会被服务端覆盖）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckLogin
    @GetMapping("/my-tasks")
    public R<PageResult<AigPortalTaskVo>> myTasks(AigTaskQueryBo bo, PageQuery pageQuery) {
        return R.ok(portalService.myTasks(bo, pageQuery, requireActor()));
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
