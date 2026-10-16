package org.dromara.aigov.studio.helper;

import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;

/**
 * 「所需能力」的客观事实快照（训练台预检用）。
 *
 * <p><b>为什么要单独做一个快照，而不是让预检直接查库</b>：{@link AigStudioPrecheck} 是纯函数
 * （预检与提交共用同一套判据，见该类注释）。纯函数不能拿 Mapper，所以把"这次要判的事实"
 * 先由调用方查成一份快照传进去——预检只做判定，不做 I/O。</p>
 *
 * <p><b>口径单一</b>：事实来自能力概览聚合（{@code IAigCapabilityOverviewService}），
 * 与治理台能力卡片、"能力就绪度"用的是同一份口径；若在这里另写一套候选/策略判定，
 * 迟早会出现"卡片说缺策略、预检说没问题"这种自相矛盾。</p>
 *
 * @param capabilityCode    能力编码（原样回显，便于文案与定位）
 * @param exists            能力清单里是否存在这个编码
 * @param status            状态（0 正常 / 1 停用；不存在时为 null）
 * @param capabilityName    能力名（可能为空）
 * @param candidateCount    启用的模型候选数
 * @param candidateHealthAgg 候选健康汇总 NONE / HEALTHY / UNHEALTHY / UNKNOWN
 * @param policyCoveredLevels 已配置策略的数据等级数
 * @param policyTotalLevels  应配置的策略总数
 * @author ai-gov
 */
public record AigStudioCapabilityFacts(String capabilityCode, boolean exists, String status,
                                       String capabilityName, int candidateCount, String candidateHealthAgg,
                                       int policyCoveredLevels, int policyTotalLevels) {

    /**
     * 状态：正常。
     */
    private static final String STATUS_NORMAL = "0";

    /**
     * 候选健康汇总：没有任何候选。
     */
    private static final String HEALTH_NONE = "NONE";

    /**
     * 候选健康汇总：存在明确不可用的候选。
     */
    private static final String HEALTH_UNHEALTHY = "UNHEALTHY";

    /**
     * 能力不存在（编码写错或已被删除）。
     *
     * @param capabilityCode 能力编码
     * @return 快照
     */
    public static AigStudioCapabilityFacts missing(String capabilityCode) {
        return new AigStudioCapabilityFacts(capabilityCode, false, null, null, 0, HEALTH_NONE, 0, 0);
    }

    /**
     * 从概览行取快照。
     *
     * @param capabilityCode 能力编码
     * @param vo             概览行（为 null 视为不存在）
     * @return 快照
     */
    public static AigStudioCapabilityFacts of(String capabilityCode, AigCapabilityOverviewVo vo) {
        if (vo == null) {
            return missing(capabilityCode);
        }
        return new AigStudioCapabilityFacts(capabilityCode, true, vo.getStatus(), vo.getCapabilityName(),
            vo.getCandidateCount() == null ? 0 : vo.getCandidateCount(), vo.getCandidateHealthAgg(),
            vo.getPolicyCoveredLevels() == null ? 0 : vo.getPolicyCoveredLevels(),
            vo.getPolicyTotalLevels() == null ? 0 : vo.getPolicyTotalLevels());
    }

    /**
     * 是否已停用（存在的能力才谈停用）。
     *
     * @return 停用返回 true
     */
    public boolean disabled() {
        return exists && !STATUS_NORMAL.equals(status);
    }

    /**
     * 是否一个候选都没有。
     *
     * @return 没有候选返回 true
     */
    public boolean noCandidate() {
        return candidateCount == 0;
    }

    /**
     * 候选是否明确不可用。
     *
     * @return 明确不可用返回 true
     */
    public boolean candidateUnhealthy() {
        return HEALTH_UNHEALTHY.equals(candidateHealthAgg);
    }

    /**
     * 候选是否"从未探测过"（不是健康，见概览实现的健康口径）。
     *
     * @return 未知返回 true
     */
    public boolean candidateUnknown() {
        return healthUnknown() || HEALTH_NONE.equals(candidateHealthAgg);
    }

    private boolean healthUnknown() {
        return "UNKNOWN".equals(candidateHealthAgg);
    }

    /**
     * 是否一条策略都没配。
     *
     * @return 未配置返回 true
     */
    public boolean noPolicy() {
        return policyCoveredLevels == 0;
    }

    /**
     * 策略是否不全（含"一条都没配"）。
     *
     * @return 不全返回 true
     */
    public boolean policyIncomplete() {
        return policyTotalLevels > 0 && policyCoveredLevels < policyTotalLevels;
    }
}
