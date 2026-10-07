package org.dromara.aigov.task.helper;

/**
 * 任务操作者解析（隔离 {@code LoginHelper} 的静态依赖）。
 *
 * <p><b>为什么要这一层，而不是直接调 {@code LoginHelper.getUserId()}</b>：
 * 推进任务状态的不只有 HTTP 请求——调度器重试、Provider 回调、超时扫描器
 * 都在<b>没有登录上下文</b>的线程里跑。{@code LoginHelper.getUserId()} 在那种环境会
 * 直接抛异常，于是「取不到操作者」这件小事会把整次状态迁移带崩：
 * 重试永远不会发生、回调永远处理不了，而报错信息里只有一句 token 相关的异常，
 * 与「任务没动」之间的因果关系很难看出来。</p>
 *
 * <p>因此这里把「谁在操作」变成一个可替换、可返回 null 的依赖：
 * 系统触发的事件 actor 为空是<b>正常且正确</b>的语义，不是异常。</p>
 *
 * @author ai-gov
 */
public interface AigTaskActorProvider {

    /**
     * 当前操作者ID。
     *
     * @return 用户ID；无登录上下文（系统触发）时返回 null
     */
    Long currentUserId();

    /**
     * 当前操作者名称。
     *
     * @return 账号名；无登录上下文时返回 null
     */
    String currentUserName();

}
