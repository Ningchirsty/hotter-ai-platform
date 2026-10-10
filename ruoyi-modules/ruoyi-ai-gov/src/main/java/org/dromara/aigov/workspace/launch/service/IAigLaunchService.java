package org.dromara.aigov.workspace.launch.service;

import org.dromara.aigov.workspace.launch.domain.bo.AigLaunchRequestBo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchCommitVo;
import org.dromara.aigov.workspace.launch.domain.vo.AigLaunchPrepareVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;

/**
 * 启动解析（Launch Resolver，主文档线增量 3；附件 §12）。
 *
 * <h3>两步，各有明确职责</h3>
 * <ol>
 *     <li>{@link #prepare}：把"这次启动会变成什么"算清楚并**如实报告问题**，通过则给一张短期票据。
 *         <b>无副作用</b>——不建任务、不改任何业务数据（只往 Redis 放一张会自己过期的票）。</li>
 *     <li>{@link #commit}：用户确认后真正启动。**幂等**：同一个幂等键重复提交只会得到同一个结果，
 *         不会建出第二个任务。</li>
 * </ol>
 *
 * <h3>为什么校验要做两遍</h3>
 * <p>prepare 与 commit 之间隔着用户操作的一段时间：卡片可能被停用、场景版本可能被下架、
 * 配额可能耗尽。只在 prepare 校验，等于把"确认"变成一个绕过全部检查的入口。
 * 因此 commit 会**重新**判定一次（判定逻辑是同一份 {@code AigLaunchChecklist}）。</p>
 *
 * @author ai-gov
 */
public interface IAigLaunchService {

    /**
     * 预检并发放票据（无副作用）。
     *
     * @param bo    启动请求
     * @param actor 当前用户
     * @return 预检结果（未通过时不含票据）
     */
    AigLaunchPrepareVo prepare(AigLaunchRequestBo bo, AigPortalActor actor);

    /**
     * 确认启动（幂等）。
     *
     * @param bo    启动请求（必须带 ticket）
     * @param actor 当前用户（必须与 prepare 的人一致）
     * @return 启动结果（业务拒绝以 problems 返回）
     */
    AigLaunchCommitVo commit(AigLaunchRequestBo bo, AigPortalActor actor);

}
