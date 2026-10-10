package org.dromara.aigov.workspace.portal.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织范围解析测试（增量 2）。
 *
 * <p>守两件"不报错但会出事"的事：把 {@code "0"}/空串当成一个真实部门（范围里混进不存在的 id），
 * 以及脏 token 让整条链静默失效（表现是"发布了却没人看得见"）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigOrgScopeResolverTest {

    @Test
    @DisplayName("范围 = 本部门 + 全部祖级")
    void resolvesSelfAndAncestors() {
        assertEquals(Set.of(103L, 100L, 102L), AigOrgScopeResolver.resolve(103L, "100,102"));
        assertEquals(Set.of(100L), AigOrgScopeResolver.resolve(100L, ""));
    }

    @Test
    @DisplayName("空串与 0 不是部门：不能进范围")
    void placeholdersAreNotDepartments() {
        assertEquals(Set.of(), AigOrgScopeResolver.resolve(null, null));
        assertEquals(Set.of(), AigOrgScopeResolver.resolve(0L, "0"));
        assertEquals(Set.of(), AigOrgScopeResolver.resolve(null, "0,"));
        assertEquals(Set.of(5L), AigOrgScopeResolver.resolve(5L, "0, ,5"));
    }

    @Test
    @DisplayName("脏 token 只被忽略，不会让整条祖级链失效")
    void dirtyTokensAreIgnoredNotFatal() {
        assertEquals(Set.of(103L, 100L, 101L), AigOrgScopeResolver.resolve(103L, "100,abc,101"));
        assertEquals(Set.of(7L), AigOrgScopeResolver.resolve(7L, " , , "));
    }

    @Test
    @DisplayName("结果不可变且保留插入顺序（本仓库不再依赖 Set 的未定义顺序）")
    void resultIsImmutableAndOrdered() {
        Set<Long> scope = AigOrgScopeResolver.resolve(103L, "100,102");
        assertEquals(java.util.List.of(103L, 100L, 102L), java.util.List.copyOf(scope));
        assertTrue(scope.contains(100L));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> scope.add(999L));
    }

}
