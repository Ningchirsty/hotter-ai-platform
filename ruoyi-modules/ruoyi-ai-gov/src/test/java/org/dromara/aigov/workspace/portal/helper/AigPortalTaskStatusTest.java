package org.dromara.aigov.workspace.portal.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 员工看到的业务状态测试（设计 §6.8）。
 *
 * <p><b>这一条为什么值钱</b>：内部状态与业务状态之间最容易错的一处是
 * {@code SUCCEEDED}——它在平台上只是"执行成功"，**不等于业务通过**。
 * 若把它直接显示成"完成"，员工会把未经复核的草稿当成成品用出去。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigPortalTaskStatusTest {

    private static String labelOf(String status, String reviewStatus) {
        AigPortalTaskStatus.BusinessStatus resolved = AigPortalTaskStatus.resolve(status, reviewStatus);
        return resolved == null ? null : resolved.label();
    }

    @Test
    @DisplayName("★ 执行成功不等于通过：未复核显示「草稿已生成 待复核」，复核通过才是「完成」")
    void succeededIsNotDoneUntilReviewed() {
        assertEquals("草稿已生成 待复核", labelOf("SUCCEEDED", null));
        assertEquals("草稿已生成 待复核", labelOf("SUCCEEDED", "  "));
        assertEquals("草稿已生成 待复核", labelOf("SUCCEEDED", "REVIEW_PENDING"));
        assertEquals("完成", labelOf("SUCCEEDED", "APPROVED"));
        assertEquals("已复核 未通过", labelOf("SUCCEEDED", "REJECTED"));
        // 大小写/空白都按同一口径归一（复核状态是服务端写的，但配置/回填可能出现小写）
        assertEquals("完成", labelOf("SUCCEEDED", " approved "));
    }

    @Test
    @DisplayName("★ 业务状态覆盖设计 §6.8 列出的形态（排队中/生成中/等待确认/失败/取消）")
    void businessStatusesCoverTheDesignList() {
        assertEquals("待检查", labelOf("DRAFT", null));
        assertEquals("待检查", labelOf("POLICY_CHECKING", null));
        assertEquals("排队中", labelOf("QUEUED", null));
        assertEquals("生成中", labelOf("RUNNING", null));
        assertEquals("生成中", labelOf("DISPATCHED", null));
        assertEquals("等待重试", labelOf("RETRY_WAIT", null));
        assertEquals("等待确认", labelOf("NEED_HUMAN", null));
        assertEquals("失败", labelOf("FAILED", null));
        assertEquals("取消中", labelOf("CANCEL_REQUESTED", null));
        assertEquals("已取消", labelOf("CANCELLED", null));
        assertEquals("草稿已生成 待复核", labelOf("REVIEW_PENDING", null));
        assertEquals("完成", labelOf("APPROVED", null));
        assertEquals("已复核 未通过", labelOf("REJECTED", null));
    }

    @Test
    @DisplayName("★ 认不出的状态码返回 null（回落服务端文案，不猜一个业务状态）")
    void unknownStatusHasNoBusinessLabel() {
        assertNull(AigPortalTaskStatus.resolve("SOMETHING_ELSE", null));
        assertNull(AigPortalTaskStatus.resolve(null, null));
        assertNull(labelOf("SOMETHING_ELSE", "APPROVED"));
    }

    @Test
    @DisplayName("业务状态码本身也是稳定的（前端可按它上色/图标，不必比中文）")
    void businessCodesAreStable() {
        assertEquals("DRAFT_READY", AigPortalTaskStatus.resolve("SUCCEEDED", null).code());
        assertEquals("DONE", AigPortalTaskStatus.resolve("SUCCEEDED", "APPROVED").code());
        assertEquals("GENERATING", AigPortalTaskStatus.resolve("RUNNING", null).code());
        assertEquals("WAITING_CONFIRM", AigPortalTaskStatus.resolve("NEED_HUMAN", null).code());
    }

}
