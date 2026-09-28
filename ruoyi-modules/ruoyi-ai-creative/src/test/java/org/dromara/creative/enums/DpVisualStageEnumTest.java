package org.dromara.creative.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段推进合法性（防漂移的唯一判据）单测。
 *
 * <p>R7 的真实故障把两条口径钉在这里：补齐参考图/重出图这类「人的补充动作」在已推进的项目上
 * 不能被阶段机拒掉——否则表现就是「已交付/已排版的项目改不动」。</p>
 *
 * @author creative
 */
class DpVisualStageEnumTest {

    @Test
    @DisplayName("停留原地与向前推进（含跳步）都允许")
    void forwardMovesAllowed() {
        assertTrue(DpVisualStageEnum.MATERIAL_READY.canMoveTo(DpVisualStageEnum.MATERIAL_READY));
        assertTrue(DpVisualStageEnum.MATERIAL_READY.canMoveTo(DpVisualStageEnum.PRODUCING));
        assertTrue(DpVisualStageEnum.DNA_LOCKED.canMoveTo(DpVisualStageEnum.VISUAL_LOCKED));
        assertTrue(DpVisualStageEnum.PRODUCING.canMoveTo(DpVisualStageEnum.LAYOUT_PROCESSING));
    }

    @Test
    @DisplayName("R7：排版完成后允许回到出图重出（返工重出图不再被拒）")
    void reworkBackToProducingAllowed() {
        assertTrue(DpVisualStageEnum.V08_READY.canMoveTo(DpVisualStageEnum.PRODUCING));
        assertTrue(DpVisualStageEnum.DESIGN_REFINING.canMoveTo(DpVisualStageEnum.PRODUCING));
        assertTrue(DpVisualStageEnum.FINAL_REVIEW.canMoveTo(DpVisualStageEnum.PRODUCING));
        // 返工回出图后，后续仍是向前推进，环路闭合
        assertTrue(DpVisualStageEnum.PRODUCING.canMoveTo(DpVisualStageEnum.V08_READY));
        assertTrue(DpVisualStageEnum.V08_READY.canMoveTo(DpVisualStageEnum.COMPLETED));
    }

    @Test
    @DisplayName("R7：已完成的项目也能返工重出图（但不会退回更早的生成态）")
    void completedCanRework() {
        assertTrue(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.PRODUCING));
        assertTrue(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.LAYOUT_PROCESSING));
        assertTrue(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.DESIGN_REFINING));
        // 仍然不允许从"已完成"整条链路重跑
        assertFalse(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.MATERIAL_READY));
        assertFalse(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.DNA_GENERATING));
        assertFalse(DpVisualStageEnum.COMPLETED.canMoveTo(DpVisualStageEnum.DIRECTION_GENERATING));
    }

    @Test
    @DisplayName("仍然禁止回退到「生成中」的中间态（假中间态的原始顾虑保持不变）")
    void backwardIntoGeneratingStillForbidden() {
        assertFalse(DpVisualStageEnum.VISUAL_LOCKED.canMoveTo(DpVisualStageEnum.DNA_GENERATING));
        assertFalse(DpVisualStageEnum.VISUAL_LOCKED.canMoveTo(DpVisualStageEnum.DIRECTION_GENERATING));
        assertFalse(DpVisualStageEnum.VISUAL_LOCKED.canMoveTo(DpVisualStageEnum.STORYBOARD_GENERATING));
        assertFalse(DpVisualStageEnum.V08_READY.canMoveTo(DpVisualStageEnum.QA_PROCESSING));
    }
}
