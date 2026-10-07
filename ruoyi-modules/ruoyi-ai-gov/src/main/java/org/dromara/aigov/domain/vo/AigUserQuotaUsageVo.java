package org.dromara.aigov.domain.vo;

import java.time.LocalDateTime;

/**
 * 某个人的配额**用量**（当前周期已用多少、还剩多少）。
 *
 * <p><b>为什么必须能查用量</b>：只给一个上限、不给当前用量，运维就无法回答
 * 「他到底是快用完了还是配错了」，用户被拦时也只能看到「超了」而不知道超在哪。
 * 把「已用 / 上限 / 周期起点」一起返回，判断依据才完整。</p>
 *
 * @param userId        用户ID
 * @param userName      用户账号（能取到时带上）
 * @param dailyLimit    每自然日上限（null = 不限）
 * @param dailyUsed     今日已用次数
 * @param dailyFrom     今日周期起点（自然日 00:00:00）
 * @param monthlyLimit  每自然月上限（null = 不限）
 * @param monthlyUsed   本月已用次数
 * @param monthlyFrom   本月周期起点（自然月 1 日 00:00:00）
 * @param limited       是否有生效中的限制（配了行、且状态正常、且至少一条上限非空）
 * @param exceeded      当前是否已超限（任一有上限的周期已用 ≥ 上限）
 * @author ai-gov
 */
public record AigUserQuotaUsageVo(
    Long userId,
    String userName,
    Integer dailyLimit,
    long dailyUsed,
    LocalDateTime dailyFrom,
    Integer monthlyLimit,
    long monthlyUsed,
    LocalDateTime monthlyFrom,
    boolean limited,
    boolean exceeded
) {
}
