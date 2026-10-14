package org.dromara.aigov.tool.exec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具执行判据测试（设计 §7.5）。
 *
 * <p>守三件"错了也不报错"的事：未接入工具被当成可执行、写操作绕过确认、参数变了批准仍生效。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigToolExecutionPolicyTest {

    @Test
    @DisplayName("★ 没有注册执行器 → 拒绝（不显示为可用）")
    void unavailableExecutorIsRejected() {
        AigToolExecutionPolicy.Decision decision =
            AigToolExecutionPolicy.decide("READ_ONLY_QUERY", false);
        assertFalse(decision.allowed());
        assertFalse(decision.confirmationRequired());
        assertTrue(decision.reason().contains("待接入"), decision.reason());
    }

    @Test
    @DisplayName("★ 写操作必须人工确认：变更业务数据 / 外部发送")
    void mutatingOperationsRequireConfirmation() {
        for (String op : new String[] {"MUTATE_BUSINESS_DATA", "EXTERNAL_SEND"}) {
            AigToolExecutionPolicy.Decision decision = AigToolExecutionPolicy.decide(op, true);
            assertFalse(decision.allowed(), op);
            assertTrue(decision.confirmationRequired(), op);
        }
    }

    @Test
    @DisplayName("只读查询与生成草稿可直接执行")
    void readOnlyAndDraftAreAllowed() {
        for (String op : new String[] {"READ_ONLY_QUERY", "GENERATE_DRAFT"}) {
            AigToolExecutionPolicy.Decision decision = AigToolExecutionPolicy.decide(op, true);
            assertTrue(decision.allowed(), op);
            assertFalse(decision.confirmationRequired(), op);
        }
    }

    @Test
    @DisplayName("未知操作类型拒绝（不让未登记语义的执行悄悄跑起来）")
    void unknownOperationIsRejected() {
        assertFalse(AigToolExecutionPolicy.decide("NO_SUCH_OP", true).allowed());
        assertFalse(AigToolExecutionPolicy.decide(null, true).allowed());
    }

    @Test
    @DisplayName("输入摘要随参数变化（这是「参数变则批准失效」的基础）")
    void inputDigestChangesWithInput() {
        String a = AigToolExecutionPolicy.inputDigest("t", "1", "{\"x\":1}");
        String b = AigToolExecutionPolicy.inputDigest("t", "1", "{\"x\":2}");
        assertNotEquals(a, b);
        assertEquals(a, AigToolExecutionPolicy.inputDigest("t", "1", "{\"x\":1}"));
    }

    @Test
    @DisplayName("★ 批准校验：摘要一致且未过期才有效；截止时刻本身仍有效")
    void approvalValidity() {
        LocalDateTime now = LocalDateTime.parse("2026-10-14T10:00:00");
        String digest = "d1";

        assertTrue(AigToolExecutionPolicy.approvalValid(digest, digest, null, now));
        assertTrue(AigToolExecutionPolicy.approvalValid(digest, digest, now, now), "闭区间：截止时刻仍有效");
        assertFalse(AigToolExecutionPolicy.approvalValid(digest, digest, now.minusSeconds(1), now));
        assertFalse(AigToolExecutionPolicy.approvalValid(digest, "other", null, now), "参数变了批准失效");
        assertFalse(AigToolExecutionPolicy.approvalValid(digest, null, null, now));
        assertFalse(AigToolExecutionPolicy.approvalValid(null, digest, null, now));
    }

}
