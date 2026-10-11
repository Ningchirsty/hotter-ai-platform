package org.dromara.aigov.workspace.launch.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 启动错误码测试（增量 3）。
 *
 * <p>守两件事：①**封闭集合**——扩一个码必须同时改这里（前端要能处理它，文案要评审过）；
 * ②**文案纪律**——这些字串会直接显示给员工，不许出现私钥/端口/SQL/栈/内部路径。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchErrorEnumTest {

    /**
     * 附件 §12.5 的九个码：一个都不能少，也刻意不多
     */
    private static final Set<String> EXPECTED = Set.of(
        "ROLE_NOT_GRANTED", "ACTION_NOT_AVAILABLE", "SCENE_VERSION_BLOCKED", "RUNTIME_UNHEALTHY",
        "REQUIRED_INPUT_MISSING", "PROJECT_ACCESS_DENIED", "RESOURCE_EXHAUSTED",
        "LAUNCH_TICKET_EXPIRED", "IDEMPOTENCY_CONFLICT");

    @Test
    @DisplayName("错误码是封闭集合（扩值必须同时改契约）")
    void codesAreClosedSet() {
        Set<String> actual = new LinkedHashSet<>();
        for (AigLaunchErrorEnum item : AigLaunchErrorEnum.values()) {
            actual.add(item.getCode());
        }
        assertEquals(EXPECTED, actual);
    }

    @Test
    @DisplayName("文案不泄露私钥/端口/SQL/栈/内部路径（它会被直接显示给员工）")
    void messagesAreSafeForEmployees() {
        String[] forbidden = {"password", "passwd", "secret", "token", ":8080", "jdbc", "select ",
            "insert ", "at org.", "stack", "/home/", "Exception", "null"};
        for (AigLaunchErrorEnum item : AigLaunchErrorEnum.values()) {
            assertNotNull(item.getMessage(), item.getCode() + " 缺文案");
            assertFalse(item.getMessage().isBlank(), item.getCode() + " 文案为空");
            String lower = item.getMessage().toLowerCase();
            for (String word : forbidden) {
                assertFalse(lower.contains(word.toLowerCase()),
                    item.getCode() + " 的文案可能泄露内部细节：" + item.getMessage());
            }
        }
    }

    @Test
    @DisplayName("按码查找大小写与空白不敏感；未命中返回 null")
    void findIsForgiving() {
        assertEquals(AigLaunchErrorEnum.ROLE_NOT_GRANTED, AigLaunchErrorEnum.find("role_not_granted"));
        assertEquals(AigLaunchErrorEnum.RESOURCE_EXHAUSTED, AigLaunchErrorEnum.find("  RESOURCE_EXHAUSTED "));
        assertNull(AigLaunchErrorEnum.find("NOT_A_CODE"));
        assertNull(AigLaunchErrorEnum.find(null));
    }

}
