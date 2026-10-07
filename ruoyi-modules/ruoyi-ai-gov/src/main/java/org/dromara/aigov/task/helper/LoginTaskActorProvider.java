package org.dromara.aigov.task.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 登录态的默认操作者解析。
 *
 * <p><b>刻意把「无登录上下文」当作正常情况而不是异常</b>：调度器重试、
 * Provider 回调、超时扫描都不在 HTTP 线程里，取不到登录用户是预期行为。
 * 若在这里抛异常，「任务该重试但没重试」会被一条 token 异常掩盖，
 * 排查时几乎看不出两者的关系。返回 null 则让事件如实记录「系统触发」。</p>
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
