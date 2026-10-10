package org.dromara.aigov.workspace.launch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 启动票据 TTL 配置测试（增量 3）。
 *
 * <p>票据是短期凭证：没有上限保护的配置项迟早会被写成"一天有效"，
 * 那时它就不再是"短期"了，而代码里没有任何地方能看出来。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchPropertiesTest {

    @Test
    @DisplayName("默认 10 分钟；超过 30 分钟被夹到上限；非正数回落到默认")
    void ttlIsClamped() {
        AigLaunchProperties properties = new AigLaunchProperties();
        assertEquals(Duration.ofMinutes(10), properties.effectiveTicketTtl());

        properties.setTicketTtl(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5), properties.effectiveTicketTtl());

        properties.setTicketTtl(Duration.ofHours(24));
        assertEquals(Duration.ofMinutes(30), properties.effectiveTicketTtl(), "超过 30 分钟不再是短期凭证");

        properties.setTicketTtl(Duration.ZERO);
        assertEquals(Duration.ofMinutes(10), properties.effectiveTicketTtl());

        properties.setTicketTtl(Duration.ofMinutes(-5));
        assertEquals(Duration.ofMinutes(10), properties.effectiveTicketTtl());

        properties.setTicketTtl(null);
        assertEquals(Duration.ofMinutes(10), properties.effectiveTicketTtl());
    }

}
