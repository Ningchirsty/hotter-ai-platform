package org.dromara.aigov.workspace.launch.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.launch.domain.AigLaunchTicket;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的启动票据存放（主文档线增量 3）。
 *
 * <h3>为什么是 Redis 而不是库表</h3>
 * <p>附件 §12：prepare 的票据是**短期凭证**（TTL 5–10 分钟），过期即无意义。
 * 落库会带来一批只用来过期的行，还要自己写清理任务；而 Redis 的 TTL 本身就是语义。</p>
 *
 * <h3>为什么读不出来时只返回 null，不抛异常</h3>
 * <p>票据不在 = 过期或被用过，调用方会按 {@code LAUNCH_TICKET_EXPIRED} 处理（这是正常业务路径，
 * 不是系统故障）。但**解析失败**是另一回事：票据在而内容读不出来，说明编解码出问题了，
 * 这时 {@link AigLaunchTicket#fromJson} 会抛——不能被当成"过期"静默掉，
 * 否则一次编解码回归会表现成"所有人的启动都过期"，而日志里什么都没有。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLaunchTicketStore implements IAigLaunchTicketStore {

    /**
     * 票据键前缀
     */
    private static final String KEY_PREFIX = "aig:launch:ticket:";

    @Override
    public void save(AigLaunchTicket ticket, Duration ttl) {
        if (ticket == null || ticket.ticketId() == null) {
            throw new IllegalArgumentException("启动票据缺少票据ID");
        }
        RedisUtils.setCacheObject(key(ticket.ticketId()), ticket.toJson(), ttl);
    }

    @Override
    public AigLaunchTicket load(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return null;
        }
        String json = RedisUtils.getCacheObject(key(ticketId));
        if (json == null || json.isBlank()) {
            return null;
        }
        return AigLaunchTicket.fromJson(json);
    }

    @Override
    public void consume(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return;
        }
        RedisUtils.deleteObject(key(ticketId));
    }

    /**
     * 票据键。
     *
     * @param ticketId 票据ID
     * @return Redis 键
     */
    private static String key(String ticketId) {
        return KEY_PREFIX + ticketId;
    }

}
