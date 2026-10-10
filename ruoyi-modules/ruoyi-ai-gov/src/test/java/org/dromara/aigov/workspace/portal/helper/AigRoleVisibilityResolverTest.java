package org.dromara.aigov.workspace.portal.helper;

import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门户可见性测试（增量 2）。
 *
 * <p>这是"岗位可见性必须自己实现并测试"（F-06）那条决定的落点。它守的是：
 * 只有 PUBLISHED 可见、读不懂的范围声明不给所有人看、按组织定向时没有绑定就是没人看、
 * 生效区间与启停真的起作用。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRoleVisibilityResolverTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    private static AigRoleBinding binding(Long orgId, Long brandId, String enabled,
                                          LocalDateTime from, LocalDateTime to) {
        AigRoleBinding row = new AigRoleBinding();
        row.setOrgId(orgId);
        row.setBrandId(brandId);
        row.setEnabled(enabled);
        row.setEffectiveFrom(from);
        row.setEffectiveTo(to);
        return row;
    }

    private static boolean visible(String status, String scope, List<AigRoleBinding> bindings,
                                   Set<Long> orgs, Set<Long> brands) {
        return AigRoleVisibilityResolver.visible(status, scope, bindings, orgs, brands, NOW);
    }

    @Test
    @DisplayName("只有 PUBLISHED 可见：草稿/测试中/已停用都不对员工可见")
    void onlyPublishedIsVisible() {
        assertTrue(visible("PUBLISHED", "ALL", List.of(), Set.of(), Set.of()));
        assertFalse(visible("DRAFT", "ALL", List.of(), Set.of(), Set.of()));
        assertFalse(visible("TESTING", "ALL", List.of(), Set.of(), Set.of()));
        assertFalse(visible("DISABLED", "ALL", List.of(), Set.of(), Set.of()));
        assertFalse(visible("SOMETHING_ELSE", "ALL", List.of(), Set.of(), Set.of()), "状态读不懂时 fail-closed");
        assertFalse(visible(null, "ALL", List.of(), Set.of(), Set.of()));
    }

    @Test
    @DisplayName("读不懂的可见范围不给所有人看（null / 空 / 未知取值）")
    void unknownScopeIsNotEveryone() {
        assertFalse(visible("PUBLISHED", null, List.of(), Set.of(), Set.of()));
        assertFalse(visible("PUBLISHED", "  ", List.of(), Set.of(), Set.of()));
        assertFalse(visible("PUBLISHED", "WHO_KNOWS", List.of(), Set.of(9L), Set.of(7L)));
    }

    @Test
    @DisplayName("按组织定向：命中用户组织范围才可见，没绑定就是没人看")
    void assignedOrgNeedsMatchingBinding() {
        List<AigRoleBinding> bindings = List.of(binding(100L, null, "Y", null, null));
        assertTrue(visible("PUBLISHED", "ASSIGNED_ORG", bindings, Set.of(100L, 1L), Set.of()));
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG", bindings, Set.of(101L), Set.of()));
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG", List.of(), Set.of(100L), Set.of()),
            "按组织定向但一条绑定都没有：还没决定给谁看，不是「所有人看」");
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG", null, Set.of(100L), Set.of()));
    }

    @Test
    @DisplayName("按品牌定向：命中用户品牌才可见（今天品牌集可以为空=fail-closed）")
    void assignedBrandNeedsMatchingBrand() {
        List<AigRoleBinding> bindings = List.of(binding(null, 7L, "Y", null, null));
        assertTrue(visible("PUBLISHED", "ASSIGNED_ORG", bindings, Set.of(), Set.of(7L)));
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG", bindings, Set.of(100L), Set.of(8L)));
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG", bindings, Set.of(), Set.of()));
    }

    @Test
    @DisplayName("绑定的启停与生效区间真的起作用（否则「下月才生效」会当场对员工生效）")
    void bindingEffectivenessMatters() {
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(100L, null, "N", null, null)), Set.of(100L), Set.of()));
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(100L, null, "Y", NOW.plusDays(1), null)), Set.of(100L), Set.of()),
            "还没开始生效");
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(100L, null, "Y", null, NOW.minusDays(1))), Set.of(100L), Set.of()),
            "已经过期");
        assertTrue(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(100L, null, "Y", NOW.minusDays(1), NOW)), Set.of(100L), Set.of()),
            "区间是闭区间：到期时刻仍然生效");
        // 多条绑定里有任意一条生效即可
        assertTrue(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(101L, null, "Y", null, null), binding(100L, null, "Y", null, null)),
            Set.of(100L), Set.of()));
    }

    @Test
    @DisplayName("绑定只写了一半（org 与 brand 都为空）不会变成「对所有人可见」")
    void emptyBindingMatchesNobody() {
        assertFalse(visible("PUBLISHED", "ASSIGNED_ORG",
            List.of(binding(null, null, "Y", null, null)), Set.of(100L), Set.of(7L)));
    }

}
