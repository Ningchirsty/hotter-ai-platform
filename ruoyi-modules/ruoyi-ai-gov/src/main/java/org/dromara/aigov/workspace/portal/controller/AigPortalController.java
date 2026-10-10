package org.dromara.aigov.workspace.portal.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalArtifactVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalTaskVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.aigov.workspace.portal.helper.AigPortalActorProvider;
import org.dromara.aigov.workspace.portal.service.IAigPortalService;
import org.dromara.aigov.workspace.recommend.domain.bo.AigRecommendSuggestBo;
import org.dromara.aigov.workspace.recommend.domain.vo.AigRecommendResultVo;
import org.dromara.aigov.workspace.recommend.service.IAigRecommendService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工 AI 工作台（门户）接口（主文档线增量 2；附件 §7、§9）。
 *
 * <p>除 {@code /intent/suggest} 外都是**只读**接口。那个推荐接口也不改平台业务数据，
 * 但它会发起一次**受治理的模型调用**（默认关闭、真花钱），所以方法和语义上都与只读接口区分开。</p>
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
    private final IAigRecommendService recommendService;
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
     * 我的产物（平台产物台账；范围恒为当前用户）。
     *
     * @param taskId    任务ID（可空：只看某个任务的产物）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckLogin
    @GetMapping("/my-artifacts")
    public R<PageResult<AigPortalArtifactVo>> myArtifacts(
        @RequestParam(required = false) Long taskId, PageQuery pageQuery) {
        return R.ok(portalService.myArtifacts(taskId, pageQuery, requireActor()));
    }

    /**
     * 自然语言推荐（只推荐、不启动；一次调用会花真钱，默认关闭）。
     *
     * <p>候选清单由服务端按当前用户可见卡片算出，调用方只能给"一句话需求"。
     * 关着时**明确报错**（返回空列表会被读成"没有相关卡片"）。</p>
     *
     * @param bo 入参（一句话需求）
     * @return 推荐结果（已按可见清单过滤）
     */
    @SaCheckLogin
    @PostMapping("/intent/suggest")
    public R<AigRecommendResultVo> suggest(@RequestBody(required = false) AigRecommendSuggestBo bo) {
        return R.ok(recommendService.suggest(bo, requireActor()));
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
