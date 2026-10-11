package org.dromara.aigov.workspace.portal.helper;

import org.dromara.aigov.workspace.domain.AigRoleBinding;
import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 品牌归属解析口测试（④）。
 *
 * <p>本仓没有"用户↔品牌"数据源（F-05 已冻），所以这里钉的是两件事：
 * ①<b>默认实现必须是 fail-closed 且自报未接入</b>——空集是"按品牌定向的绑定暂时对谁都不生效"，
 * 不是"所有用户都没有品牌"；②<b>口子是通的</b>：业务侧一旦提供品牌归属，同一套可见性判定
 * 立刻能把按品牌定向的绑定判为可见（判定规则不需要改）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigUserBrandResolverTest {

    @Test
    @DisplayName("默认实现：空集且自报未接入（fail-closed，不伪装成业务事实）")
    void defaultResolverIsFailClosed() {
        AigUserBrandResolver resolver = new AigUserBrandResolverNotYetAvailable();

        assertFalse(resolver.available(), "没有数据源时必须自报未接入");
        assertTrue(resolver.brandsOf(9L).isEmpty(), "未接入时不应凭空推断品牌");
        assertTrue(resolver.brandsOf(null).isEmpty());
    }

    @Test
    @DisplayName("口子是通的：业务侧提供品牌后，按品牌定向的绑定立刻对这个人可见")
    void brandBindingBecomesVisibleOnceASourceIsWired() {
        AigRoleBinding brandOnly = new AigRoleBinding();
        // 只按品牌定向（orgId 为空）：组织范围命中不了，只有品牌能命中
        brandOnly.setBrandId(7L);
        brandOnly.setEnabled("Y");
        LocalDateTime now = LocalDateTime.of(2026, 10, 11, 12, 0);

        AigUserBrandResolver wired = new AigUserBrandResolver() {
            @Override
            public Set<Long> brandsOf(Long userId) {
                return Set.of(7L);
            }

            @Override
            public boolean available() {
                return true;
            }
        };

        assertTrue(AigRoleVisibilityResolver.visible(
                AigRoleReleaseStatusEnum.PUBLISHED.getCode(), AigRoleVisibilityResolver.SCOPE_ASSIGNED_ORG,
                List.of(brandOnly), Set.of(), wired.brandsOf(9L), now),
            "接上品牌来源后，按品牌定向的绑定应命中");
        assertFalse(AigRoleVisibilityResolver.visible(
                AigRoleReleaseStatusEnum.PUBLISHED.getCode(), AigRoleVisibilityResolver.SCOPE_ASSIGNED_ORG,
                List.of(brandOnly), Set.of(), new AigUserBrandResolverNotYetAvailable().brandsOf(9L), now),
            "数据源未接入时，按品牌定向的绑定对谁都不生效（fail-closed）");
    }

}
