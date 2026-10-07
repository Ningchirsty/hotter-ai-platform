package org.dromara.aigov.task.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 登录态的默认操作者解析。
 *
 * <p><b>刻意把「无登录上下文」当作正常情况</b>：调度器重试、Provider 回调、
 * 超时扫描都不在 HTTP 线程里，取不到登录用户是预期行为，返回 null
 * 让事件如实记录「系统触发」。</p>
 *
 * <p><b>更正一条曾被写错的结论</b>：早先的注释说这里抛异常会导致「任务该重试但没重试」。
 * 实测 {@code LoginHelper.getUserId()} 在无登录上下文时<b>返回 null 而不抛异常</b>
 * （其内部 {@code getExtra} 已 catch 全部异常），所以下面这层 try/catch 实际不会被触发，
 * 它保留的意义是不依赖第三方实现细节。真正会因 null 出问题的地方是
 * {@code create_by} 参与的创建幂等（见 {@link AigTaskActorProvider} 说明），
 * 已由 {@code AigTaskServiceImpl} 的提交者哨兵值解决。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class LoginTaskActorProvider implements AigTaskActorProvider {

    @Override
    public Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            // 只记 debug：系统触发（调度/回调）是常态，刷 warn 会把真正的异常淹掉
            log.debug("当前无登录上下文，按系统触发处理（actor 为空）：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public String currentUserName() {
        try {
            return LoginHelper.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

}
