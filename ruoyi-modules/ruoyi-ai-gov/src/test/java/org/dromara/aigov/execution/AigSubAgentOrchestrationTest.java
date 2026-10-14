package org.dromara.aigov.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 子 Agent 顺序编排测试（设计 §6.5）。
 *
 * <p>钉住：声明顺序即执行顺序；循环引用与超深在<b>展开时</b>拒绝（不等运行时超时）；
 * 超深选择拒绝而不是截断。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSubAgentOrchestrationTest {

    private static Function<Long, List<Long>> graph(Map<Long, List<Long>> edges) {
        return id -> edges.getOrDefault(id, List.of());
    }

    @Test
    @DisplayName("★ 顺序：按声明顺序展开，步骤带深度与父版本")
    void expandsInDeclaredOrder() {
        // 1 -> [2, 3]，2 -> [4]
        AigSubAgentOrchestration.Result result = AigSubAgentOrchestration.plan(1L,
            graph(Map.of(1L, List.of(2L, 3L), 2L, List.of(4L))), 3);

        assertTrue(result.accepted(), result.rejectionReason());
        assertEquals(3, result.steps().size());
        assertEquals(2L, result.steps().get(0).versionId());
        assertEquals(1, result.steps().get(0).depth());
        assertEquals(4L, result.steps().get(1).versionId(), "深度优先：2 的子先于 3");
        assertEquals(2, result.steps().get(1).depth());
        assertEquals(2L, result.steps().get(1).parentVersionId());
        assertEquals(3L, result.steps().get(2).versionId());
    }

    @Test
    @DisplayName("★ 循环引用：A→B→A 在展开时拒绝")
    void cycleIsRejected() {
        AigSubAgentOrchestration.Result result = AigSubAgentOrchestration.plan(1L,
            graph(Map.of(1L, List.of(2L), 2L, List.of(1L))), 5);

        assertFalse(result.accepted());
        assertTrue(result.rejectionReason().contains("循环引用"), result.rejectionReason());
    }

    @Test
    @DisplayName("★ 超过最大深度：拒绝而不是截断（截断会静默少跑一段）")
    void exceedingDepthIsRejected() {
        AigSubAgentOrchestration.Result result = AigSubAgentOrchestration.plan(1L,
            graph(Map.of(1L, List.of(2L), 2L, List.of(3L))), 1);

        assertFalse(result.accepted());
        assertTrue(result.rejectionReason().contains("最大深度"), result.rejectionReason());
    }

    @Test
    @DisplayName("参数非法：根为空 / 最大深度非正 / 无解析器 → 拒绝")
    void invalidInputsAreRejected() {
        assertFalse(AigSubAgentOrchestration.plan(null, graph(Map.of()), 1).accepted());
        assertFalse(AigSubAgentOrchestration.plan(1L, graph(Map.of()), 0).accepted());
        assertFalse(AigSubAgentOrchestration.plan(1L, null, 1).accepted());
    }

    @Test
    @DisplayName("没有子 Agent：通过且步骤为空")
    void noChildrenIsAccepted() {
        AigSubAgentOrchestration.Result result = AigSubAgentOrchestration.plan(1L, graph(Map.of()), 1);
        assertTrue(result.accepted());
        assertTrue(result.steps().isEmpty());
    }

}
