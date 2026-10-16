package org.dromara.aigov.domain.vo;

/**
 * 调用人在当前周期的配额状态（**只读**快照，供 {@code dryRun} 预演使用）。
 *
 * <p><b>为什么预演要给出这个</b>：真实调用在被审批/配额拦下时，人会看到一句"需要审批"或"配额超了"，
 * 但那是在**已经尝试调用之后**。预演的目的正是"上线前先知道会不会被拦"，只回一句路由结论
 * 等于把最该提前知道的两道闸门藏起来。</p>
 *
 * <p><b>为什么只回调用人自己的额度</b>：额度是"按人"的，回别人的额度等于把治理数据泄漏给
 * 一个没被授权看它的人；而"没有调用人"（调度/系统发起）时额度本就不适用，此时连这个对象都不给。</p>
 *
 * <p><b>这个快照不消耗配额、不写审计、不写决策账本</b>：它只是把"现在的用量"读出来算一下剩余。</p>
 *
 * @param limited      是否有生效中的限制（配了配额行、状态正常、且至少一条上限非空）
 * @param exceeded     当前是否已超限（任一有上限的周期已用 ≥ 上限）——预演要能直接回答"会不会被拦"
 * @param remaining    最紧的那个周期的剩余次数（下限 0）；不限时为空
 * @param dailyLimit   自然日上限（空 = 不限）
 * @param dailyUsed    今日已用次数
 * @param monthlyLimit 自然月上限（空 = 不限）
 * @param monthlyUsed  本月已用次数
 * @author ai-gov
 */
public record AigQuotaStatusVo(boolean limited, boolean exceeded, Integer remaining,
                               Integer dailyLimit, long dailyUsed,
                               Integer monthlyLimit, long monthlyUsed) {

    /**
     * 从配额用量视图构造（`remaining` 取两个周期里最紧的那个，避免"日剩 5、月已超"被说成还有余量）。
     *
     * @param usage 用量视图（可空 → 返回 null，表示"无调用人 / 取不到"）
     * @return 状态快照；usage 为空时返回 null
     */
    public static AigQuotaStatusVo of(AigUserQuotaUsageVo usage) {
        if (usage == null) {
            return null;
        }
        Integer remaining = null;
        if (usage.dailyLimit() != null) {
            remaining = Math.max(0, usage.dailyLimit() - (int) usage.dailyUsed());
        }
        if (usage.monthlyLimit() != null) {
            int monthly = Math.max(0, usage.monthlyLimit() - (int) usage.monthlyUsed());
            remaining = remaining == null ? monthly : Math.min(remaining, monthly);
        }
        return new AigQuotaStatusVo(usage.limited(), usage.exceeded(), remaining,
            usage.dailyLimit(), usage.dailyUsed(), usage.monthlyLimit(), usage.monthlyUsed());
    }
}
