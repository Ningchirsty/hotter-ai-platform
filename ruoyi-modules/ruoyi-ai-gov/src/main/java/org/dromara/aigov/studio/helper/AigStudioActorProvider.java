package org.dromara.aigov.studio.helper;

/**
 * 训练台操作者解析（隔离 {@code LoginHelper} 的静态依赖）。
 *
 * <p><b>为什么与任务域的那个分开、不复用</b>：本仓的 {@code AigTaskActorProvider} 允许
 * actor 为空（调度器/回调确实没有登录人）；而训练台是<b>纯人工工作台</b>——
 * 草稿的写操作必须有明确的人来担责任，"系统自动改了别人的草稿"不是本模块的语义。
 * 因此这里刻意不提供"空 actor"的默认路径：取不到人时由服务层报错，
 * 而不是把它当成"系统触发"放行。</p>
 *
 * @author ai-gov
 */
public interface AigStudioActorProvider {

    /**
     * 当前操作者ID。
     *
     * @return 用户ID；无登录上下文时返回 null（调用方必须显式处理，不得当成系统身份）
     */
    Long currentUserId();

}
