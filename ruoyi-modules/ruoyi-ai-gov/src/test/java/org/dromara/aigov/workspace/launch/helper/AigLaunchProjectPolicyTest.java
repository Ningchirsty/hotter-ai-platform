package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.workspace.portal.helper.AigPortalActor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目权判定测试（增量 4）。
 *
 * <p>它钉住的是那条**默认值的选择**：今天判不了项目权，所以带项目ID的启动必须被拒绝。
 * 若哪天有人把默认实现改成"判不了就放行"，这里的用例会红——而那是必须被看见的一次决定
 * （放行意味着拿到别人 projectId 的请求会被当成正常启动，且没有任何报错）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchProjectPolicyTest {

    private static final AigPortalActor ACTOR =
        new AigPortalActor(9L, 102L, Set.of(102L, 100L), Set.of());

    @Test
    @DisplayName("不带项目的请求不受影响（项目权只管项目）")
    void noProjectIsAlwaysAllowed() {
        AigLaunchProjectPolicy policy = new AigLaunchProjectPolicyNotYetEnforceable();
        assertTrue(policy.allowed(null, ACTOR));
    }

    @Test
    @DisplayName("带项目ID的请求必须被拒绝：判不了就拒绝，而不是放行")
    void projectRequestsAreRejectedFailClosed() {
        AigLaunchProjectPolicy policy = new AigLaunchProjectPolicyNotYetEnforceable();
        assertFalse(policy.allowed(1L, ACTOR));
        assertFalse(policy.allowed(1L, null), "没有用户也照样拒绝（不能因为取不到人而放行）");
    }

    @Test
    @DisplayName("当前实现自称不可判定：界面据此把文案说成「暂不可用」而不是「你没权限」")
    void policyDeclaresItselfNotEnforceable() {
        AigLaunchProjectPolicy policy = new AigLaunchProjectPolicyNotYetEnforceable();
        assertFalse(policy.enforceable());
    }

}
