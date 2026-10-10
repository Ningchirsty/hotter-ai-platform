package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.workspace.portal.domain.AigPortalActionContext;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalArtifactVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleHomeVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalRoleVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalTaskVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * 员工门户只读服务（主文档线增量 2）。
 *
 * <h3>三条贯穿全链路的性质</h3>
 * <ol>
 *     <li><b>只读</b>：这里没有任何写方法。员工门户不是"另一个管理入口"，
 *         启动能力要走 Launch Resolver（增量 3）——那样才有一张可审计的启动凭证。</li>
 *     <li><b>过滤在服务端</b>：可见的岗位由绑定与发布状态决定，可见的卡片由启用状态与分类归属决定；
 *         不该看到的<b>根本不发到浏览器</b>。前端筛选意味着数据已经出门了。</li>
 *     <li><b>一律以"当前用户"为范围</b>：{@link #myTasks} 强制把查询条件里的提交人覆盖成登录用户，
 *         调用方无法用它查别人的任务。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigPortalService {

    /**
     * 当前用户可见的岗位（一个岗位只出现一次：取最新的可见版本）。
     *
     * @param actor 当前门户用户
     * @return 岗位清单（按岗位编码排序）
     */
    List<AigPortalRoleVo> listMyRoles(AigPortalActor actor);

    /**
     * 当前用户可见的岗位首页（含服务端过滤后的卡片）。
     *
     * <p>给"要把卡片清单作为目录外发"的调用方用（推荐链路，增量 7）：它必须拿到**同一个**
     * 可见性判定的产物，不能自己再拼一份——否则会出现"推荐得出来、门户里点不开"这种不一致。</p>
     *
     * @param actor 当前门户用户
     * @return 岗位首页清单（按岗位编码排序）
     */
    List<AigPortalRoleHomeVo> listMyRoleHomes(AigPortalActor actor);

    /**
     * 某个岗位的首页（服务端过滤后的卡片）。
     *
     * @param roleCode 岗位编码
     * @param actor    当前门户用户
     * @return 岗位首页
     */
    AigPortalRoleHomeVo getRoleHome(String roleCode, AigPortalActor actor);

    /**
     * 解析"确切的哪张卡片"（启动链路用）。
     *
     * <p>与 {@link #getRoleHome} 走**同一条可见性路径**，因此不会出现
     * "门户里看得到、启动说不可用"这种只有用户能发现的不一致。</p>
     *
     * @param roleCode   岗位编码
     * @param actionCode 卡片编码
     * @param actor      当前用户
     * @return 卡片上下文（含岗位版本）
     * @throws org.dromara.common.core.exception.ServiceException 岗位不可见或卡片不可用
     */
    AigPortalActionContext resolveAction(String roleCode, String actionCode, AigPortalActor actor);

    /**
     * 我的任务（只读聚合；范围恒为当前用户）。
     *
     * @param bo        查询条件（其中的提交人会被覆盖为当前用户）
     * @param pageQuery 分页参数
     * @param actor     当前门户用户
     * @return 分页结果
     */
    PageResult<AigPortalTaskVo> myTasks(AigTaskQueryBo bo, PageQuery pageQuery, AigPortalActor actor);

    /**
     * 我的产物（只读；范围恒为当前用户）。
     *
     * <p>数据源是**平台自己的产物台账** {@code aig_task_artifact}（ADR-010），与任务域同模块；
     * 归属取"任务的提交人"——产物行自己的 {@code create_by} 是生产方（执行器），不是员工。
     * 各域的资产表（image/video/content）**不在**这里聚合：它们的可见性规则在各自模块里，
     * 详见文档 §6.1。</p>
     *
     * @param taskId    任务ID（可空：只查某个任务的产物）
     * @param pageQuery 分页参数
     * @param actor     当前门户用户
     * @return 分页结果
     */
    PageResult<AigPortalArtifactVo> myArtifacts(Long taskId, PageQuery pageQuery, AigPortalActor actor);

}
