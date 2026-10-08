package org.dromara.creative.helper;

import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.creative.enums.DpGenerationStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 创作域候选状态 → 治理任务状态的映射测试（一候选一任务，用户 2026-10-08 拍定）。
 *
 * <p><b>为什么逐条钉</b>：这是两套状态机之间唯一的接口，映射写错不会报错——只会在治理台里
 * 显示一个与事实不符的状态。尤其两点容易写错：① {@code SUCCEEDED} 在两侧含义相同
 * （任务侧本就是「执行成功、待复核」），不要画蛇添足映射成 {@code REVIEW_PENDING}；
 * ② 人工结论必须**经由** {@code REVIEW_PENDING}，不能直接跳到 {@code APPROVED}——
 * 那会绕过任务侧「APPROVED 必经 REVIEW_PENDING」的不变式。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeTaskStatusMapperTest {

    @Test
    @DisplayName("★ 内核六态逐条映射（QUEUED 落 DISPATCHED、超时归 FAILED、取消走 CANCELLED）")
    void mapsKernelStates() {
        assertEquals(AigTaskStatusEnum.DISPATCHED, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.QUEUED),
            "登记那一刻就是「已派发」：内核已接单，不是排队等平台执行");
        assertEquals(AigTaskStatusEnum.RUNNING, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.RUNNING));
        assertEquals(AigTaskStatusEnum.SUCCEEDED, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.SUCCEEDED),
            "「已出图」= 任务侧的「执行成功（待复核，不等于通过）」，含义本来就一致");
        assertEquals(AigTaskStatusEnum.FAILED, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.FAILED));
        assertEquals(AigTaskStatusEnum.FAILED, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.TIMEOUT),
            "任务侧没有超时态，归到可重试的失败分类");
        assertEquals(AigTaskStatusEnum.CANCELLED, CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.CANCELED));
    }

    @Test
    @DisplayName("★ 人工结论不是内核回报：forKernel 必须拒绝，走 forDecision 才不会被当成状态透传")
    void kernelMappingRejectsHumanDecisions() {
        assertNull(CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.APPROVED),
            "候选的 APPROVED 是「该屏采用这张图」的人工结论，内核不会回报它");
        assertNull(CreativeTaskStatusMapper.forKernel(DpGenerationStatusEnum.REJECTED));
        assertNull(CreativeTaskStatusMapper.forKernel(null));
    }

    @Test
    @DisplayName("★ 选定：两步 REVIEW_PENDING → APPROVED（顺序即执行顺序）")
    void decisionApprovedTakesTwoSteps() {
        assertEquals(java.util.List.of(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.APPROVED),
            CreativeTaskStatusMapper.forDecision(DpGenerationStatusEnum.APPROVED),
            "任务侧有「APPROVED 必经 REVIEW_PENDING」的不变式；而 SUCCEEDED 的含义就是等复核，这一步不是伪造");
    }

    @Test
    @DisplayName("★ 筛除/否决：两步 REVIEW_PENDING → REJECTED")
    void decisionRejectedTakesTwoSteps() {
        assertEquals(java.util.List.of(AigTaskStatusEnum.REVIEW_PENDING, AigTaskStatusEnum.REJECTED),
            CreativeTaskStatusMapper.forDecision(DpGenerationStatusEnum.REJECTED));
    }

    @Test
    @DisplayName("非人工结论不给步骤（空列表 ⇒ 调用方什么都不做）")
    void otherStatusesHaveNoDecisionSteps() {
        assertTrue(CreativeTaskStatusMapper.forDecision(DpGenerationStatusEnum.SUCCEEDED).isEmpty());
        assertTrue(CreativeTaskStatusMapper.forDecision(DpGenerationStatusEnum.FAILED).isEmpty());
        assertTrue(CreativeTaskStatusMapper.forDecision(null).isEmpty());
    }

    @Test
    @DisplayName("失败分类：超时能被识别为 TIMEOUT；内核 FAILED 不猜、如实记 UNKNOWN（会转人工）")
    void errorClassesAreHonest() {
        assertEquals(AigErrorClassEnum.TIMEOUT, CreativeTaskStatusMapper.errorClassFor(DpGenerationStatusEnum.TIMEOUT));
        assertEquals(AigErrorClassEnum.UNKNOWN, CreativeTaskStatusMapper.errorClassFor(DpGenerationStatusEnum.FAILED),
            "内核错误码与平台错误分类未对齐，不猜");
        assertNull(CreativeTaskStatusMapper.errorClassFor(DpGenerationStatusEnum.SUCCEEDED));
    }

    @Test
    @DisplayName("登记态是 DISPATCHED 而不是 QUEUED：QUEUED 的含义是「排队等平台执行」，平台不执行这条任务")
    void registeredStatusIsDispatched() {
        assertEquals(AigTaskStatusEnum.DISPATCHED, CreativeTaskStatusMapper.REGISTERED_STATUS);
    }

}
