package org.dromara.aigov.studio.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 登录态的训练台操作者解析。
 *
 * <p><b>取不到登录用户时返回 null，由服务层报错</b>：训练台是人工工作台，
 * 没有登录人就不该产生"一次编辑"。把它当系统身份放行，会让草稿出现无法追责的修改。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class LoginStudioActorProvider implements AigStudioActorProvider {

    @Override
    public Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            // 无登录上下文对训练台是异常路径（不像任务域那样是常态），但仍只记 debug：
            // 真正的拒绝逻辑在服务层，这里刷 warn 只会把日志淹掉
            log.debug("训练台取不到登录用户：{}", e.getClass().getSimpleName());
            return null;
        }
    }

}
