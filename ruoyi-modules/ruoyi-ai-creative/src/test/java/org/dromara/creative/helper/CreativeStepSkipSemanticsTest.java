package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「跳过」的语义（V0.2 R36）。
 *
 * <p>这一步此前只有一个"词"：{@code DpProjectStepState} 的注释里写着
 * {@code PENDING/ACTIVE/DONE/SKIPPED}，但全仓没有任何代码产出 SKIPPED，
 * 文档里也没有它的定义。本轮把它定清楚，这里把判据钉死：</p>
 *
 * <ol>
 *   <li><b>必填不能跳</b>（配置 {@code required} 不是 {@code '0'} 一律按必填，含空值——fail-closed）；</li>
 *   <li><b>有闸门不能跳</b>（跳过等于绕过门禁）；</li>
 *   <li>已完成/已跳过的不能再跳；</li>
 *   <li>进度口径：跳过从分母里去掉，但绝不算进分子。</li>
 * </ol>
 */
class CreativeStepSkipSemanticsTest {

    @Test
    @DisplayName("只有「显式可选 + 无闸门 + 未完成」才允许跳过")
    void skippableRules() {
        assertTrue(CreativeStepProjection.skippable("0", null, CreativeStepProjection.PENDING),
            "可选、无闸门、待办 → 可以跳");
        assertTrue(CreativeStepProjection.skippable("0", "", CreativeStepProjection.ACTIVE),
            "空串闸门等同于没有闸门 → 进行中也可以跳");

        assertFalse(CreativeStepProjection.skippable("1", null, CreativeStepProjection.PENDING),
            "必填步骤不允许跳过");
        assertFalse(CreativeStepProjection.skippable(null, null, CreativeStepProjection.PENDING),
            "required 为空按必填处理（漏写的字段不能让流程变得可跳过）");
        assertFalse(CreativeStepProjection.skippable("", null, CreativeStepProjection.PENDING),
            "同上：空字符串也按必填");
        assertFalse(CreativeStepProjection.skippable("0", "VISUAL_GATE_PASS", CreativeStepProjection.PENDING),
            "有闸门的步骤不允许跳过——跳过等于绕过门禁");
        assertFalse(CreativeStepProjection.skippable("0", null, CreativeStepProjection.DONE),
            "已完成的不允许标成跳过");
        assertFalse(CreativeStepProjection.skippable("0", null, CreativeStepProjection.SKIPPED),
            "已经跳过的不能再跳（要改就取消跳过）");
    }

    @Test
    @DisplayName("投影永远不产出 SKIPPED（它是人的决定，不是阶段的推论）")
    void projectionNeverYieldsSkipped() {
        var steps = java.util.List.of(
            new CreativeStepProjection.ConfiguredStep("INPUT", "资料", "MATERIAL_READY", 10),
            new CreativeStepProjection.ConfiguredStep("QA", "质检", "QA_PROCESSING", 80));
        for (String stage : new String[] {"MATERIAL_READY", "QA_PROCESSING", "COMPLETED", null, "不认识的阶段"}) {
            for (CreativeStepProjection.StepState state : CreativeStepProjection.project(steps, stage)) {
                assertFalse(CreativeStepProjection.SKIPPED.equals(state.status()),
                    "阶段=" + stage + " 时不该推出 SKIPPED：" + state);
            }
        }
    }

    @Test
    @DisplayName("进度口径：跳过从分母去掉，但不算完成；跳完不为负")
    void progressExcludesSkippedFromDenominator() {
        assertArrayEquals(new int[] {3, 7}, CreativeStepProjection.progress(9, 3, 2),
            "9 步里做了 3 步、跳过 2 步 → 3/7");
        assertArrayEquals(new int[] {0, 9}, CreativeStepProjection.progress(9, 0, 0));
        assertArrayEquals(new int[] {9, 9}, CreativeStepProjection.progress(9, 9, 0));
        assertArrayEquals(new int[] {0, 0}, CreativeStepProjection.progress(2, 0, 5),
            "跳过数超过总步数（配置被改小）时分母夹到 0，不出现负数");
        assertArrayEquals(new int[] {0, 0}, CreativeStepProjection.progress(2, 2, 5),
            "分子不越过分母：剩 0 个可做步骤时显示 0/0，而不是 2/0（百分比会超过 100%）");
    }
}
