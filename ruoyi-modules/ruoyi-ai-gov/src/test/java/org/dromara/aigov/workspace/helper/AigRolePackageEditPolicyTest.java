package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigRoleReleaseStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位包编辑与流转规则测试（增量 1b）。
 *
 * <p>守三件"不报错但会出事"的事：改已发布版本、放没校验过的版本出门、
 * CAS 判定过松导致并发编辑被静默覆盖。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRolePackageEditPolicyTest {

    @Test
    @DisplayName("只有 DRAFT 可改：已发布版本改了就无法回答『员工当时看到的是什么』")
    void onlyDraftIsEditable() {
        assertTrue(AigRolePackageEditPolicy.isEditable(AigRoleReleaseStatusEnum.DRAFT));
        assertFalse(AigRolePackageEditPolicy.isEditable(AigRoleReleaseStatusEnum.TESTING));
        assertFalse(AigRolePackageEditPolicy.isEditable(AigRoleReleaseStatusEnum.PUBLISHED));
        assertFalse(AigRolePackageEditPolicy.isEditable(AigRoleReleaseStatusEnum.DISABLED));
        // 状态未知一律按"不可改"——fail-closed，而不是放行
        assertFalse(AigRolePackageEditPolicy.isEditable(null));
    }

    @Test
    @DisplayName("离开 DRAFT 去 TESTING/PUBLISHED 必须先校验通过；叫停不需要先证明它是对的")
    void visibleTargetsRequireValidation() {
        assertTrue(AigRolePackageEditPolicy.requiresValidationPass(AigRoleReleaseStatusEnum.TESTING));
        assertTrue(AigRolePackageEditPolicy.requiresValidationPass(AigRoleReleaseStatusEnum.PUBLISHED));
        assertFalse(AigRolePackageEditPolicy.requiresValidationPass(AigRoleReleaseStatusEnum.DISABLED));
        assertFalse(AigRolePackageEditPolicy.requiresValidationPass(AigRoleReleaseStatusEnum.DRAFT));
        assertFalse(AigRolePackageEditPolicy.requiresValidationPass(null));
    }

    @Test
    @DisplayName("CAS：两侧都必须非空白，否则『不带期望值』会永远成功")
    void casRequiresBothSides() {
        assertTrue(AigRolePackageEditPolicy.casMatches("abc123", "abc123"));
        assertTrue(AigRolePackageEditPolicy.casMatches(" abc123 ", "abc123"));
        assertFalse(AigRolePackageEditPolicy.casMatches("abc123", "other"));
        assertFalse(AigRolePackageEditPolicy.casMatches(null, "abc123"));
        assertFalse(AigRolePackageEditPolicy.casMatches("", "abc123"), "期望值为空不能算匹配");
        assertFalse(AigRolePackageEditPolicy.casMatches("   ", "abc123"), "空白期望值不能算匹配");
        assertFalse(AigRolePackageEditPolicy.casMatches("abc123", null), "库里没有哈希时不能说匹配");
        assertFalse(AigRolePackageEditPolicy.casMatches("abc123", ""), "库里哈希为空时不能说匹配");
    }

}
