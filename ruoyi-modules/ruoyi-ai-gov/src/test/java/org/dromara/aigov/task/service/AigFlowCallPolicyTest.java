package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigFlowNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「一次执行发几次流程调用」的口径测试（设计 §6.5；用户 2026-10-14 授权每步一次调用）。
 *
 * <p>这条判据直接决定花多少钱：写宽了会把一次误配置放大成几十次计费，写窄了顺序流程跑不完。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigFlowCallPolicyTest {

    private static AigFlowNode node(String type) {
        return new AigFlowNode(type, type.toLowerCase(), type);
    }

    @Test
    @DisplayName("★ 只数 STEP 节点：控制类节点不花调用（它们由驱动就地收尾）")
    void countsOnlyStepNodes() {
        assertEquals(0, AigFlowCallPolicy.stepNodeCount(
            List.of(node("CONDITION"), node("HUMAN_CONFIRM"), node("OUTPUT"))));
        assertEquals(2, AigFlowCallPolicy.stepNodeCount(
            List.of(node("STEP"), node("OUTPUT"), node("STEP"))));
        assertEquals(0, AigFlowCallPolicy.stepNodeCount(List.of()));
        assertEquals(0, AigFlowCallPolicy.stepNodeCount(null));
    }

    @Test
    @DisplayName("★ 没有 STEP 节点 → 0（调用方据此保持「无流程节点时仍调用一次」的既有语义）")
    void noStepNodeMeansZeroCalls() {
        assertEquals(0, AigFlowCallPolicy.maxStepCalls(List.of(node("OUTPUT")), 5));
        assertEquals(0, AigFlowCallPolicy.maxStepCalls(null, null));
    }

    @Test
    @DisplayName("★ 上限取「声明步数 ∩ limits.maxFlowSteps ∩ 平台默认」三者最小")
    void maxStepCallsIsTheTightestLimit() {
        List<AigFlowNode> threeSteps = List.of(node("STEP"), node("STEP"), node("STEP"));
        // 没声明 limits：声明 3 个就发 3 次
        assertEquals(3, AigFlowCallPolicy.maxStepCalls(threeSteps, null));
        // limits 收紧到 2：只发 2 次（第 3 个步骤不会被跑）
        assertEquals(2, AigFlowCallPolicy.maxStepCalls(threeSteps, 2));
        // limits 放宽到 99：仍以"声明的 3 个"为准（没有第 4 步可跑）
        assertEquals(3, AigFlowCallPolicy.maxStepCalls(threeSteps, 99));
        // 非法/非正的 limits 按未声明处理
        assertEquals(3, AigFlowCallPolicy.maxStepCalls(threeSteps, 0));
        assertEquals(3, AigFlowCallPolicy.maxStepCalls(threeSteps, -1));
    }

    @Test
    @DisplayName("★ 平台默认上限兜底：声明 50 步也不会发 50 次调用")
    void platformCapProtectsAgainstMisconfiguredFlows() {
        List<AigFlowNode> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(node("STEP"));
        }
        assertEquals(AigFlowCallPolicy.DEFAULT_MAX_STEP_CALLS,
            AigFlowCallPolicy.maxStepCalls(many, null));
        assertEquals(AigFlowCallPolicy.DEFAULT_MAX_STEP_CALLS,
            AigFlowCallPolicy.maxStepCalls(many, 999));
    }

    @Test
    @DisplayName("★ 下一步的输入 = 上一步的输出（否则「顺序执行」只是把同一句话问三遍）")
    void nextInputChainsPreviousOutput() {
        assertEquals("上一步的产出", AigFlowCallPolicy.nextInput("上一步的产出", "原始输入"));
        // 上一步没输出时退回原始输入（防御：正常路径上"有输出"是成功判据的一部分）
        assertEquals("原始输入", AigFlowCallPolicy.nextInput(null, "原始输入"));
        assertEquals("原始输入", AigFlowCallPolicy.nextInput("  ", "原始输入"));
    }

}
