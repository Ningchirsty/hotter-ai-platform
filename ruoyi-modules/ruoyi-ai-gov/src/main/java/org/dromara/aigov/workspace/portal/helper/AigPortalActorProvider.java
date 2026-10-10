package org.dromara.aigov.workspace.portal.helper;

/**
 * 解析门户的当前用户（隔离 {@code LoginHelper} 的静态依赖）。
 *
 * <p><b>为什么与任务域那个 provider 分开</b>：{@code AigTaskActorProvider} 允许 actor 为空
 * （调度器/回调确实没有登录人）；门户是<b>纯人工入口</b>，"没有登录人"不是一种状态，
 * 而是一个必须被拒绝的请求——取不到人时由服务层报错，而不是当成"某个系统身份"放行。</p>
 *
 * @author ai-gov
 */
public interface AigPortalActorProvider {

    /**
     * 当前登录用户。
     *
     * @return 门户用户；无登录上下文时返回 null（调用方必须显式处理）
     */
    AigPortalActor currentActor();

}
