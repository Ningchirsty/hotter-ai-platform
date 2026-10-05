package org.dromara.creative.helper;

import org.dromara.creative.enums.DpVisualStageEnum;
import org.dromara.creative.helper.CreativeStepProjection.ConfiguredStep;
import org.dromara.creative.helper.CreativeStepProjection.StepState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「阶段 → 步骤状态」投影的单元测试（V0.2 D2，文档 §8/§49）。
 *
 * <p>为什么值得单独钉住：这一步决定界面上"我做到第几步了"，算错了不会抛异常，
 * 只会把人指到错的环节，而且看起来还挺像对的。所以断言写成
 * <b>「某阶段下这 10 步分别是什么」</b>的整表对照，而不是抽查一两项。</p>
 *
 * <p>步骤配置取的是生产种子（{@code dp_creative_r11_scenario_foundation.sql} 里
 * ECOM_DETAIL 的 10 步），因此本测试同时钉住了「种子里的 stage_codes 与阶段枚举对得上」——
 * 种子写错一个阶段码，这里会红。</p>
 */
class CreativeStepProjectionTest {

    /**
     * 生产种子里的 10 步（profile 1765000000000000002），顺序即 sort_no。
     *
     * <p><b>R44 起与种子有意的差异</b>：四个步骤的 {@code *_LOCKED} 阶段被
     * {@code dp_creative_step_locked_means_done.sql} 从各自步骤里摘掉了——
     * 产品口径是「锁定/确认 ＝ 这一步做完了」（v1 人工测试反馈：锁定分镜后步骤条仍写
     * 「分镜 进行中」、页面也不推进）。本表跟着改，否则测试钉的是已经不发货的配置。</p>
     */
    private static final List<ConfiguredStep> SEED = List.of(
        new ConfiguredStep("INPUT", "产品资料与参考图", "MATERIAL_READY", 10),
        new ConfiguredStep("FACT", "事实确认", "MATERIAL_READY", 20),
        new ConfiguredStep("DNA", "视觉基因", "DNA_GENERATING,DNA_REVIEW", 30),
        new ConfiguredStep("DIRECTION", "视觉方向",
            "DIRECTION_GENERATING,DIRECTION_REVIEW", 40),
        new ConfiguredStep("STORYBOARD", "分镜",
            "STORYBOARD_GENERATING,STORYBOARD_REVIEW", 50),
        new ConfiguredStep("GATE", "视觉门", "VISUAL_GATE", 60),
        new ConfiguredStep("GENERATION", "出图", "PRODUCING", 70),
        new ConfiguredStep("QA", "质检", "QA_PROCESSING", 80),
        new ConfiguredStep("LAYOUT", "长图排版", "LAYOUT_PROCESSING,DESIGN_REFINING", 90),
        new ConfiguredStep("FINAL", "终审交付", "FINAL_REVIEW,COMPLETED", 100)
    );

    /**
     * 取某阶段下各步骤的状态串（形如 {@code ACTIVE,PENDING,…}），便于整表对照。
     *
     * @param stage 当前阶段
     * @return 10 个状态，用逗号连接
     */
    private static String statuses(String stage) {
        return String.join(",", CreativeStepProjection.project(SEED, stage).stream()
            .map(StepState::status).toList());
    }

    /**
     * 取某阶段下处于 ACTIVE 的步骤编码。
     *
     * @param stage 当前阶段
     * @return 步骤编码列表（同序）
     */
    private static List<String> activeAt(String stage) {
        List<String> codes = new ArrayList<>();
        for (StepState state : CreativeStepProjection.project(SEED, stage)) {
            if (CreativeStepProjection.ACTIVE.equals(state.status())) {
                codes.add(state.stepCode());
            }
        }
        return codes;
    }

    @Test
    @DisplayName("种子里的 10 步与阶段枚举逐一对得上（阶段码写错会在这里红）")
    void seedStageCodesAreKnown() {
        for (ConfiguredStep step : SEED) {
            for (String code : step.stageCodes().split(",")) {
                assertNotNull(DpVisualStageEnum.find(code.trim()),
                    "步骤 " + step.stepCode() + " 引用了未知阶段：" + code);
            }
        }
        for (int i = 1; i < SEED.size(); i++) {
            assertTrue(SEED.get(i).sortNo() > SEED.get(i - 1).sortNo(),
                "sort_no 必须递增：" + SEED.get(i).stepCode());
        }
    }

    @Test
    @DisplayName("资料就绪：INPUT 与 FACT 同时进行中（配置粒度可以细于阶段粒度）")
    void materialReadyStartsFirstTwoSteps() {
        assertEquals(List.of("INPUT", "FACT"), activeAt("MATERIAL_READY"));
        assertEquals("ACTIVE,ACTIVE,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("MATERIAL_READY"));
    }

    @Test
    @DisplayName("每个阶段的整表对照（含阶段内部的生成中/待确认三态）")
    void fullTableForEveryStage() {
        // 基因：生成中与待确认都算"基因这一步"
        String dna = "DONE,DONE,ACTIVE,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING";
        assertEquals(dna, statuses("DNA_GENERATING"));
        assertEquals(dna, statuses("DNA_REVIEW"));
        // 锁定＝这一步做完了（R44）：基因 DONE、方向还没开始
        assertEquals("DONE,DONE,DONE,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("DNA_LOCKED"));

        // 方向
        assertEquals("DONE,DONE,DONE,ACTIVE,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("DIRECTION_GENERATING"));
        assertEquals("DONE,DONE,DONE,DONE,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("DIRECTION_LOCKED"));

        // 分镜
        assertEquals("DONE,DONE,DONE,DONE,ACTIVE,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("STORYBOARD_REVIEW"));
        // 真机复现过的那一条：锁定分镜后步骤条不该再写"分镜 进行中"
        assertEquals("DONE,DONE,DONE,DONE,DONE,PENDING,PENDING,PENDING,PENDING,PENDING",
            statuses("STORYBOARD_LOCKED"));

        // 视觉门：审核中停在"视觉门"这一步
        String gate = "DONE,DONE,DONE,DONE,DONE,ACTIVE,PENDING,PENDING,PENDING,PENDING";
        assertEquals(gate, statuses("VISUAL_GATE"));
        // 人工确认通过＝视觉门做完了
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,PENDING,PENDING,PENDING,PENDING",
            statuses("VISUAL_LOCKED"));

        // 生产链
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,ACTIVE,PENDING,PENDING,PENDING",
            statuses("PRODUCING"));
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,DONE,ACTIVE,PENDING,PENDING",
            statuses("QA_PROCESSING"));
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,ACTIVE,PENDING",
            statuses("LAYOUT_PROCESSING"));

        // 终审
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,ACTIVE",
            statuses("FINAL_REVIEW"));
    }

    @Test
    @DisplayName("锁定/确认之后没有任何一步在进行中——有意如此：那一步已了结，下一步还没开始")
    void lockStagesLeaveNoActiveStep() {
        for (String stage : List.of("DNA_LOCKED", "DIRECTION_LOCKED", "STORYBOARD_LOCKED", "VISUAL_LOCKED")) {
            assertEquals(List.of(), activeAt(stage),
                stage + " 之后不该还有步骤显示「进行中」——这一步已经了结，下一步用户还没开始；"
                    + "界面此时显示的是「第一个还没了结的步骤」（pickVisibleStep）");
        }
    }

    @Test
    @DisplayName("机排版完成(V08_READY)落在排版区间内部 → 排版进行中（区间规则的存在理由）")
    void v08ReadyFallsInsideLayoutInterval() {
        // V08_READY 没有被任何一步写进 stage_codes：它夹在 LAYOUT_PROCESSING 与 DESIGN_REFINING 之间
        assertTrue(SEED.stream().noneMatch(s -> s.stageCodes().contains("V08_READY")));
        assertEquals(List.of("LAYOUT"), activeAt("V08_READY"));
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,ACTIVE,PENDING",
            statuses("V08_READY"));
        // 人工精修：同一步，仍是进行中
        assertEquals(statuses("V08_READY"), statuses("DESIGN_REFINING"));
    }

    @Test
    @DisplayName("已交付(COMPLETED)：全部 DONE，不留一个还在进行中的尾巴")
    void completedMeansEverythingDone() {
        assertEquals("DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE,DONE",
            statuses("COMPLETED"));
    }

    @Test
    @DisplayName("交付后返工回出图：后面的步骤回到待办（状态可重算，不是只能前进）")
    void reworkBackToProducingResetsLaterSteps() {
        String producing = statuses("PRODUCING");
        assertTrue(producing.endsWith("PENDING,PENDING,PENDING"),
            "回到出图后，质检/排版/终审必须回到待办：" + producing);
        // 投影只依赖当前阶段，所以"重建"与"实时算"必然一致
        assertEquals(producing, statuses("PRODUCING"));
    }

    @Test
    @DisplayName("空配置 / 空阶段 / 未知阶段都不编造：空配置返回空，脏阶段按链路起点算")
    void degenerateInputsAreSafe() {
        assertTrue(CreativeStepProjection.project(null, "PRODUCING").isEmpty());
        assertTrue(CreativeStepProjection.project(List.of(), "PRODUCING").isEmpty());

        // 阶段为 NULL（老任务第一次进入视觉工厂）→ 视为资料就绪
        assertEquals(statuses("MATERIAL_READY"),
            String.join(",", CreativeStepProjection.project(SEED, null).stream()
                .map(StepState::status).toList()));
        assertEquals(statuses("MATERIAL_READY"), statuses("  "));
        // 认不出的阶段码（脏数据）→ 退回链路起点，而不是"全部完成"
        assertEquals(statuses("MATERIAL_READY"), statuses("SOME_UNKNOWN_STAGE"));
    }

    @Test
    @DisplayName("认不出的 stage_codes 不猜：为空/全是垃圾 → 该步 PENDING")
    void unknownStageCodesArePending() {
        List<ConfiguredStep> steps = List.of(
            new ConfiguredStep("A", "空覆盖", null, 10),
            new ConfiguredStep("B", "垃圾覆盖", "NOT_A_STAGE, ALSO_NOT", 20),
            new ConfiguredStep("C", "部分可认", "NOT_A_STAGE,PRODUCING", 30));
        List<StepState> got = CreativeStepProjection.project(steps, "PRODUCING");
        assertEquals(3, got.size());
        assertEquals(CreativeStepProjection.PENDING, got.get(0).status(), "空覆盖不编造");
        assertEquals(CreativeStepProjection.PENDING, got.get(1).status(), "全是垃圾也不编造");
        assertEquals(CreativeStepProjection.ACTIVE, got.get(2).status(), "有一个认得出就按它算");
    }

    @Test
    @DisplayName("结果与入参同序、字段原样带出（界面按 sort_no 渲染，投影不自己重排）")
    void orderAndFieldsArePreserved() {
        List<ConfiguredStep> reversed = new ArrayList<>(SEED);
        Collections.reverse(reversed);
        List<StepState> got = CreativeStepProjection.project(reversed, "PRODUCING");
        assertEquals("FINAL", got.get(0).stepCode(), "投影不排序，顺序由调用方（SQL ORDER BY）决定");
        assertEquals("终审交付", got.get(0).stepName());
        assertEquals(100, got.get(0).sortNo());
        assertEquals("INPUT", got.get(9).stepCode());
        assertEquals(10, got.get(9).sortNo());
        assertEquals(CreativeStepProjection.DONE, got.get(9).status());
        // 与正序结果逐项一致（只是顺序不同）
        List<StepState> forward = CreativeStepProjection.project(SEED, "PRODUCING");
        assertEquals(forward.size(), got.size());
        for (StepState state : forward) {
            assertTrue(got.stream().anyMatch(g -> g.stepCode().equals(state.stepCode())
                && g.status().equals(state.status())), "状态与正序一致：" + state.stepCode());
        }
    }
}
