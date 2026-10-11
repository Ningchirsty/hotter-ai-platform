package org.dromara.aigov.workspace.launch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 启动链路配置（主文档线增量 3）。
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.launch")
public class AigLaunchProperties {

    /**
     * 启动票据存活时间（附件 §12：5–10 分钟）。
     *
     * <p>太长等于给了一个长时间有效的启动凭证；太短则用户刚看到确认界面就过期。
     * 默认 10 分钟，上限 30 分钟（超过就不是"短期凭证"了，配置写错要能被发现）。</p>
     */
    private Duration ticketTtl = Duration.ofMinutes(10);

    /**
     * 取票据存活时间（带上限保护）。
     *
     * @return 存活时间
     */
    public Duration effectiveTicketTtl() {
        Duration ttl = ticketTtl == null ? Duration.ofMinutes(10) : ticketTtl;
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofMinutes(10);
        }
        return ttl.compareTo(Duration.ofMinutes(30)) > 0 ? Duration.ofMinutes(30) : ttl;
    }

}
