package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位包发布流转守卫测试（增量 1b）。
 *
 * <p>流转写错不会报错，只会在某天出现"某个岗位对员工可见了，但没人知道它怎么上去的"。
 * 所以把每条边与每条"不许走的边"都钉住。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRoleReleaseTransitionTest {

    private static final AigRoleReleaseStatusEnum DRAFT = AigRoleReleaseStatusEnum.DRAFT;
    private static final AigRoleReleaseStatusEnum TESTING = AigRoleReleaseStatusEnum.TESTING;
    private static final AigRoleReleaseStatusEnum PUBLISHED = AigRoleReleaseStatusEnum.PUBLISHED;
    private static final AigRoleReleaseStatusEnum DISABLED = AigRoleReleaseStatusEnum.DISABLED;

    @Test
    @DisplayName("★ 允许的四类边：草稿→测试、测试→发布、任何已存在→停用、停用→可再启用")
    void allowedEdges() {
        assertTrue(AigRoleReleaseTransition.canTransition(DRAFT, TESTING), "先给测试账号预览，再对员工开放");
        assertTrue(AigRoleReleaseTransition.canTransition(TESTING, PUBLISHED), "只有验证过才能对员工开放");
        assertTrue(AigRoleReleaseTransition.canTransition(DRAFT, DISABLED));
        assertTrue(AigRoleReleaseTransition.canTransition(TESTING, DISABLED));
        assertTrue(AigRoleReleaseTransition.canTransition(PUBLISHED, DISABLED), "任何已存在的东西都要能被叫停");
        assertTrue(AigRoleReleaseTransition.canTransition(DISABLED, PUBLISHED), "停用是可撤销的");
        assertTrue(AigRoleReleaseTransition.canTransition(DISABLED, TESTING), "想再验一遍也允许");
    }

    @Test
    @DisplayName("★ 不许把已发布版本退回草稿（否则「这个版本当时是什么」就答不上来）")
    void publishedCannotGoBackToDraft() {
        assertFalse(AigRoleReleaseTransition.canTransition(PUBLISHED, DRAFT));
        assertFalse(AigRoleReleaseTransition.canTransition(TESTING, DRAFT),
            "退回改稿应当出新版本，而不是把当前版本退回草稿");
    }

    @Test
    @DisplayName("★ 同状态不是流转（写事件流只会多一条「其实没变」的记录）")
    void sameStatusIsNotATransition() {
        for (AigRoleReleaseStatusEnum status : AigRoleReleaseStatusEnum.values()) {
            assertFalse(AigRoleReleaseTransition.canTransition(status, status), status.getCode());
        }
    }

    @Test
    @DisplayName("空状态一律拒绝（不能靠 null 蒙混过去）")
    void nullsAreRejected() {
        assertFalse(AigRoleReleaseTransition.canTransition(null, PUBLISHED));
        assertFalse(AigRoleReleaseTransition.canTransition(DRAFT, null));
        assertFalse(AigRoleReleaseTransition.canTransition(null, null));
        assertTrue(AigRoleReleaseTransition.allowedFrom(null).isEmpty());
        assertEquals("（当前状态未知）", AigRoleReleaseTransition.describeAllowed(null));
    }

    @Test
    @DisplayName("报错文案要当场告诉人能去哪（不是只说「不行」）")
    void describeAllowedTellsWhatToDo() {
        assertEquals("TESTING/DISABLED", AigRoleReleaseTransition.describeAllowed(DRAFT));
        assertEquals("PUBLISHED/DISABLED", AigRoleReleaseTransition.describeAllowed(TESTING));
        assertEquals("DISABLED", AigRoleReleaseTransition.describeAllowed(PUBLISHED));
        assertEquals("TESTING/PUBLISHED", AigRoleReleaseTransition.describeAllowed(DISABLED));
    }

    @Test
    @DisplayName("★ 只有 PUBLISHED 对员工可见——这一处判断集中，避免「TESTING 的岗位被员工看到」")
    void onlyPublishedIsVisibleToEmployees() {
        assertTrue(AigRoleReleaseTransition.visibleToEmployees(PUBLISHED));
        assertFalse(AigRoleReleaseTransition.visibleToEmployees(DRAFT));
        assertFalse(AigRoleReleaseTransition.visibleToEmployees(TESTING), "测试态只能给测试账号预览");
        assertFalse(AigRoleReleaseTransition.visibleToEmployees(DISABLED));
        assertFalse(AigRoleReleaseTransition.visibleToEmployees(null), "状态未知时 fail-closed");
    }

}
