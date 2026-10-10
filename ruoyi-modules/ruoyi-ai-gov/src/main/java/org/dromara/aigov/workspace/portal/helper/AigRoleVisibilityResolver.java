package org.dromara.aigov.workspace.portal.helper;

import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.dromara.aigov.workspace.helper.AigRoleReleaseTransition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 岗位对某个用户是否可见（主文档线增量 2；附件 §10.2、F-05、F-06）。
 *
 * <h3>为什么这件事必须自己实现，而不能指望绑定表</h3>
 * <p>F-06 已冻：{@code aig_agent_binding} 今天只是一张"发布许可证"，<b>没有运行时消费方</b>。
 * 也就是说"谁该看到这个岗位"不会由别处替我们算好。若不实现，最可能的做法是
 * "列表接口把 PUBLISHED 全返回"——那等于**把岗位发布当成全员广播**，
 * 而岗位包里有面向具体组织/品牌的配置。</p>
 *
 * <h3>规则（fail-closed）</h3>
 * <ol>
 *     <li><b>状态先过关</b>：只有 PUBLISHED 对员工可见（复用 {@link AigRoleReleaseTransition#visibleToEmployees}，
 *         状态未知一律不可见）；</li>
 *     <li>{@code audienceScope=ALL}：全员可见，不需要绑定行；</li>
 *     <li>{@code audienceScope=ASSIGNED_ORG}：**至少一条生效中的绑定**命中用户的组织范围或品牌；</li>
 *     <li><b>其它取值（含 null）一律不可见</b>：读不懂的范围声明不能当成"那就给所有人看"。</li>
 * </ol>
 *
 * <h3>绑定的"生效中"也要判定</h3>
 * <p>{@code enabled='Y'} 且落在 {@code [effectiveFrom, effectiveTo]} 区间内（两端可空=不限）。
 * 少了这一步，一个"下月才生效"或"已过期"的绑定会立刻对员工生效——
 * 这是一类只有业务方在特定日期才会发现的问题。</p>
 *
 * @author ai-gov
 */
public final class AigRoleVisibilityResolver {

    /**
     * 可见范围：全员
     */
    public static final String SCOPE_ALL = "ALL";

    /**
     * 可见范围：按绑定的组织/品牌
     */
    public static final String SCOPE_ASSIGNED_ORG = "ASSIGNED_ORG";

    /**
     * 启用
     */
    private static final String ENABLED_YES = "Y";

    private AigRoleVisibilityResolver() {
    }

    /**
     * 该用户能否看到这个岗位版本。
     *
     * @param releaseStatus 版本发布状态
     * @param audienceScope 清单里的可见范围声明
     * @param bindings      该版本的绑定行（可空）
     * @param userOrgIds    用户组织范围（见 {@link AigOrgScopeResolver}）
     * @param userBrandIds  用户所属品牌（可空；今天没有数据源，见 {@code AigPortalActor}）
     * @param now           判定时刻（显式传入，便于测试与"到期即失效"）
     * @return 可见返回 true
     */
    public static boolean visible(String releaseStatus, String audienceScope, List<AigRoleBinding> bindings,
                                  Set<Long> userOrgIds, Set<Long> userBrandIds, LocalDateTime now) {
        AigRoleReleaseStatusEnum status = AigRoleReleaseStatusEnum.find(releaseStatus);
        if (!AigRoleReleaseTransition.visibleToEmployees(status)) {
            return false;
        }
        if (audienceScope == null || audienceScope.isBlank()) {
            // 读不懂的范围声明不能当成"给所有人看"
            return false;
        }
        String scope = audienceScope.trim();
        if (SCOPE_ALL.equalsIgnoreCase(scope)) {
            return true;
        }
        if (!SCOPE_ASSIGNED_ORG.equalsIgnoreCase(scope)) {
            return false;
        }
        if (bindings == null || bindings.isEmpty()) {
            // 按组织定向但一条绑定都没有 = 还没决定给谁看，而不是"那就所有人看"
            return false;
        }
        Set<Long> orgs = userOrgIds == null ? Set.of() : userOrgIds;
        Set<Long> brands = userBrandIds == null ? Set.of() : userBrandIds;
        for (AigRoleBinding binding : bindings) {
            if (binding == null || !isEffective(binding, now)) {
                continue;
            }
            if (binding.getOrgId() != null && orgs.contains(binding.getOrgId())) {
                return true;
            }
            if (binding.getBrandId() != null && brands.contains(binding.getBrandId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 绑定此刻是否生效（启用 + 落在生效区间内）。
     *
     * @param binding 绑定
     * @param now     判定时刻
     * @return 生效返回 true
     */
    public static boolean isEffective(AigRoleBinding binding, LocalDateTime now) {
        if (binding == null) {
            return false;
        }
        if (!ENABLED_YES.equalsIgnoreCase(binding.getEnabled() == null ? "" : binding.getEnabled().trim())) {
            return false;
        }
        LocalDateTime moment = now == null ? LocalDateTime.now() : now;
        if (binding.getEffectiveFrom() != null && moment.isBefore(binding.getEffectiveFrom())) {
            return false;
        }
        // 区间是闭区间：到点了仍然生效（用 at 之前需要明确写出"含端点"，否则"到期日当天算不算"
        // 会变成两个人各自理解的事实）
        return binding.getEffectiveTo() == null || !moment.isAfter(binding.getEffectiveTo());
    }

}
