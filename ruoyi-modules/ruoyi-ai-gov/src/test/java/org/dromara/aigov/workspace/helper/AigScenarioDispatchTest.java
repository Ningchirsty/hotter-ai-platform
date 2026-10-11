package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigScenarioAdapterEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景"交给谁跑"的分发口径测试（增量 9）。
 *
 * <p>这里钉的是三件"错了不报错"的事：①认不出的适配器必须返回 null（调用方据此拒绝），
 * 绝不猜一个下游；②每个枚举值都要有明确的下游映射（将来扩值忘登记会在这里红）；
 * ③{@code NONE}（纯登记）与"真流程"要分得开——否则要么给纯登记场景白建流程，
 * 要么反过来把该跑的场景当成不用跑。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioDispatchTest {

    @Test
    @DisplayName("归一化：大小写/空白容错；认不出返回 null（不猜下游）")
    void normalizeIsTolerantOnShapeButStrictOnUnknown() {
        assertEquals("CREATIVE_EXISTING_FLOW", AigScenarioDispatch.normalize("creative_existing_flow"));
        assertEquals("VIDEO_EXISTING_FLOW", AigScenarioDispatch.normalize("  VIDEO_EXISTING_FLOW  "));
        assertEquals("NONE", AigScenarioDispatch.normalize("none"));
        assertNull(AigScenarioDispatch.normalize(null));
        assertNull(AigScenarioDispatch.normalize(""));
        assertNull(AigScenarioDispatch.normalize("NOT_AN_ADAPTER"));
    }

    @Test
    @DisplayName("映射到下游执行方；认不出返回 null")
    void targetMapping() {
        assertEquals(AigScenarioDispatch.Target.CREATIVE,
            AigScenarioDispatch.targetOf("CREATIVE_EXISTING_FLOW"));
        assertEquals(AigScenarioDispatch.Target.VIDEO,
            AigScenarioDispatch.targetOf("VIDEO_EXISTING_FLOW"));
        assertEquals(AigScenarioDispatch.Target.CONTENT,
            AigScenarioDispatch.targetOf("CONTENT_EXISTING_FLOW"));
        assertEquals(AigScenarioDispatch.Target.NONE, AigScenarioDispatch.targetOf("NONE"));
        assertNull(AigScenarioDispatch.targetOf("NOT_AN_ADAPTER"));
        assertNull(AigScenarioDispatch.targetOf(null));
    }

    @Test
    @DisplayName("是否需要一条真实流程：NONE 不需要，其余需要；认不出返回 false（但不等于放行）")
    void requiresWorkflow() {
        assertTrue(AigScenarioDispatch.requiresWorkflow("CREATIVE_EXISTING_FLOW"));
        assertTrue(AigScenarioDispatch.requiresWorkflow("VIDEO_EXISTING_FLOW"));
        assertTrue(AigScenarioDispatch.requiresWorkflow("CONTENT_EXISTING_FLOW"));
        assertFalse(AigScenarioDispatch.requiresWorkflow("NONE"));
        assertFalse(AigScenarioDispatch.requiresWorkflow("NOT_AN_ADAPTER"));
    }

    @Test
    @DisplayName("★枚举扩值必须同时登记下游映射（否则新适配器会静默变成「不可用」）")
    void everyAdapterHasADownstreamTarget() {
        for (AigScenarioAdapterEnum adapter : AigScenarioAdapterEnum.values()) {
            assertEquals(adapter.getCode(), AigScenarioDispatch.normalize(" " + adapter.getCode() + " "),
                adapter.name() + " 的规范编码与枚举不一致");
            assertNotNull(AigScenarioDispatch.targetOf(adapter.getCode()),
                adapter.name() + " 没有登记下游映射：扩值必须同时改 AigScenarioDispatch");
        }
    }

}
