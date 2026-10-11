package org.dromara.aigov.workspace.launch.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.springframework.stereotype.Component;

/**
 * 项目权判定的当前实现：**判不了就拒绝**（主文档线增量 4）。
 *
 * <h3>它做了什么</h3>
 * <p>当请求带了 {@code projectId} 时一律拒绝（{@code PROJECT_ACCESS_DENIED}），并留下日志——
 * 因为本平台今天**没有任何可信的"用户 ↔ 项目归属"来源**：创作/内容/视频各域的项目表与归属规则
 * 都在各自模块里，本模块若去猜（例如"看 create_dept"）就会把一条业务规则偷偷固化在一个错误的位置，
 * 而猜错的方向恰好是**放行**。</p>
 *
 * <h3>为什么不说"暂不支持"就放行</h3>
 * <p>放行的后果是：拿到别人 projectId 的请求会被当成正常启动，任务挂到别人的项目下，
 * 而每一步都"成功"。这类越权不会报错，只会某天被人发现。所以默认必须是拒绝。</p>
 *
 * <h3>接入方式（将来只改这一个类）</h3>
 * <p>某个域提供归属来源后，把本类替换成"查归属 + 按组织范围判定"的实现，
 * 并把 {@link #enforceable()} 改为 true（界面据此把文案从"暂不可用"换成"你没有这个项目的权限"）。
 * 校验链、错误码与用例都不需要改。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigLaunchProjectPolicyNotYetEnforceable implements AigLaunchProjectPolicy {

    @Override
    public boolean allowed(Long projectId, AigPortalActor actor) {
        if (projectId == null) {
            // 不挂项目就不涉及项目权
            return true;
        }
        log.info("带项目的启动被拒绝：项目权判定尚未接入（fail-closed），projectId={} userId={}",
            projectId, actor == null ? null : actor.userId());
        return false;
    }

    @Override
    public boolean enforceable() {
        return false;
    }

}
