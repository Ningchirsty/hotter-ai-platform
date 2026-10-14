package org.dromara.aigov.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顺序流程校验测试（设计 §6.5）。
 *
 * <p>三条判据各自对应一种"错了也不报错"的失败：拼错的节点类型被静默跳过、
 * 没有输出的流程跑完没有交付物、超步数被截断看起来像成功。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSequentialFlowTest {

    private static AigFlowNode node(String type) {
        return new AigFlowNode(type, null, null);
    }

    @Test
    @DisplayName("没有声明流程：通过且节点为空")
    void emptyIsAccepted() {
        assertTrue(AigSequentialFlow.validate(null, 10).accepted());
        assertTrue(AigSequentialFlow.validate(List.of(), 10).accepted());
        assertTrue(AigSequentialFlow.validate(List.of(), 10).nodes().isEmpty());
    }

    @Test
    @DisplayName("合法顺序流程：步骤 + 人工确认 + 输出")
    void validFlowIsAccepted() {
        AigSequentialFlow.Result result = AigSequentialFlow.validate(
            List.of(node("STEP"), node("HUMAN_CONFIRM"), node("OUTPUT")), 10);

        assertTrue(result.accepted(), result.reason());
        assertEquals(3, result.nodes().size());
    }

    @Test
    @DisplayName("★ 未知节点类型拒绝（拼错的类型只会让那一步被静默跳过）")
    void unknownNodeTypeIsRejected() {
        AigSequentialFlow.Result result = AigSequentialFlow.validate(
            List.of(node("STEP"), node("NO_SUCH_NODE"), node("OUTPUT")), 10);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("不可识别"), result.reason());
    }

    @Test
    @DisplayName("★ 必须有输出节点：没有输出的流程跑完没有交付物")
    void missingOutputIsRejected() {
        AigSequentialFlow.Result result = AigSequentialFlow.validate(
            List.of(node("STEP"), node("CONDITION")), 10);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("输出节点"), result.reason());
    }

    @Test
    @DisplayName("★ 输出节点必须是最后一步（交付了又继续改，结果与交付物不一致）")
    void outputMustBeLast() {
        AigSequentialFlow.Result result = AigSequentialFlow.validate(
            List.of(node("OUTPUT"), node("STEP")), 10);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("最后一步"), result.reason());
    }

    @Test
    @DisplayName("★ 超过步数上限拒绝（截断会静默少跑一段）")
    void exceedingMaxStepsIsRejected() {
        AigSequentialFlow.Result result = AigSequentialFlow.validate(
            List.of(node("STEP"), node("STEP"), node("OUTPUT")), 2);

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("上限"), result.reason());
    }

    @Test
    @DisplayName("声明了流程但步数上限非正：拒绝")
    void nonPositiveMaxStepsIsRejected() {
        assertFalse(AigSequentialFlow.validate(List.of(node("OUTPUT")), 0).accepted());
    }

}
