package org.dromara.aigov.workspace.launch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动票据测试（增量 3）。
 *
 * <p>编解码只在这一处：字段名一改而编解码分家，就会出现"能存进去、读不出来"，
 * 而表现是**所有人的启动都报票据过期**——日志里还什么都没有。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchTicketTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 10, 12, 10);

    private static AigLaunchTicket ticket() {
        return new AigLaunchTicket("t-1", 9L, 102L, "GRAPHIC_DESIGNER_AI", 7L, "A1",
            "STUDIO", "QUICK_CAPABILITY", "cap/x", "creative", 1L, "digest-1", EXPIRES_AT);
    }

    @Test
    @DisplayName("往返：JSON 编码解码后字段一致")
    void roundTrip() {
        AigLaunchTicket back = AigLaunchTicket.fromJson(ticket().toJson());
        assertEquals(ticket(), back);
        assertNull(AigLaunchTicket.fromJson(null));
        assertNull(AigLaunchTicket.fromJson("  "));
    }

    @Test
    @DisplayName("票据是短期凭证：过期判定按时刻，不按「看起来新不新」")
    void expiryIsByMoment() {
        AigLaunchTicket ticket = ticket();
        assertFalse(ticket.isExpired(EXPIRES_AT.minusMinutes(1)));
        assertFalse(ticket.isExpired(EXPIRES_AT), "到期时刻本身仍算有效（与绑定生效区间同一口径）");
        assertTrue(ticket.isExpired(EXPIRES_AT.plusSeconds(1)));
        assertFalse(ticket.isExpired(null), "没有判定时刻时不由本方法下结论");
    }

    @Test
    @DisplayName("坏 JSON 必须抛，不能当成「没有票据」静默处理")
    void brokenJsonFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> AigLaunchTicket.fromJson("{不是 JSON"));
        assertThrows(IllegalArgumentException.class, () -> AigLaunchTicket.fromJson("[1,2,3]"));
    }

}
