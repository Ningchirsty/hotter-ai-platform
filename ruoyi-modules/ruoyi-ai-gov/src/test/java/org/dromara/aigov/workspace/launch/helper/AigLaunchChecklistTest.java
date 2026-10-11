package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.workspace.launch.enums.AigLaunchErrorEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动校验链测试（增量 3）。
 *
 * <p>它是员工点卡片后的即时反馈。守的是"该拦的必须拦"（导航目标不在白名单、
 * 场景版本没就绪、缺必填输入、缺上下文、项目无权、运行时异常）
 * 以及"不该拦的不能拦"（NAVIGATION 不需要任务描述字段）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchChecklistTest {

    /**
     * 造一份"都合规"的输入。
     */
    private static AigLaunchChecklist.Input ok(String launchMode, String targetType, String targetRef) {
        return new AigLaunchChecklist.Input(launchMode, targetType, targetRef, null, List.of(),
            Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{\"a\":1}", true, false, false);
    }

    @Test
    @DisplayName("NAVIGATION：目标必须在白名单里；且不需要任务描述字段")
    void navigationNeedsWhitelistedRouteKey() {
        assertTrue(AigLaunchChecklist.check(ok("NAVIGATION", "NAVIGATION", "AIGOV_TASK")).isEmpty());
        assertEquals(List.of(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE),
            AigLaunchChecklist.check(ok("NAVIGATION", "NAVIGATION", "NOT_A_KEY")));
        // 页面跳转不建任务：任务类型/快照都不需要（这是"该放行的要放行"）
        AigLaunchChecklist.Input nav = new AigLaunchChecklist.Input("NAVIGATION", "NAVIGATION",
            "AIGOV_TASK", null, List.of(), Map.of(), null, null, null, null, true, false, false);
        assertTrue(AigLaunchChecklist.check(nav).isEmpty());
    }

    @Test
    @DisplayName("SCENARIO：引用形态或场景可用性任一不成立都要拦")
    void scenarioMustBeParseableAndUsable() {
        assertTrue(AigLaunchChecklist.check(
            ok("QUICK", "SCENARIO", "scenario://COMMERCE@1.0.0")).isEmpty());
        assertEquals(List.of(AigLaunchErrorEnum.SCENE_VERSION_BLOCKED),
            AigLaunchChecklist.check(ok("QUICK", "SCENARIO", "COMMERCE@1.0.0")), "形态不对");
        AigLaunchChecklist.Input notUsable = new AigLaunchChecklist.Input("QUICK", "SCENARIO",
            "scenario://COMMERCE@1.0.0", null, List.of(), Map.of(), "TEXT_GENERATION", "creative",
            "INTERNAL", "{}", false, false, false);
        assertEquals(List.of(AigLaunchErrorEnum.SCENE_VERSION_BLOCKED),
            AigLaunchChecklist.check(notUsable), "场景还没就绪");
    }

    @Test
    @DisplayName("STUDIO：专业页跳转键必须在白名单里")
    void studioNeedsWhitelistedRouteKey() {
        AigLaunchChecklist.Input good = new AigLaunchChecklist.Input("STUDIO", "QUICK_CAPABILITY", "cap/x",
            "CREATIVE_PRODUCTION", List.of(), Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{}",
            true, false, false);
        assertTrue(AigLaunchChecklist.check(good).isEmpty());
        AigLaunchChecklist.Input bad = new AigLaunchChecklist.Input("STUDIO", "QUICK_CAPABILITY", "cap/x",
            "NOT_A_KEY", List.of(), Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{}",
            true, false, false);
        assertEquals(List.of(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE), AigLaunchChecklist.check(bad));
    }

    @Test
    @DisplayName("缺必填输入：任务描述不全、或卡片要求的上下文没给")
    void missingInputsAreCaught() {
        AigLaunchChecklist.Input missingTask = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of(), Map.of(), null, "creative", "INTERNAL", "{}", true, false, false);
        assertEquals(List.of(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING),
            AigLaunchChecklist.check(missingTask), "没有任务类型");

        AigLaunchChecklist.Input badTaskType = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of(), Map.of(), "NOT_A_TASK_TYPE", "creative", "INTERNAL", "{}",
            true, false, false);
        assertEquals(List.of(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING),
            AigLaunchChecklist.check(badTaskType), "平台不认识的任务类型不能放行");

        AigLaunchChecklist.Input missingContext = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of("productId", "brandId"), Map.of("productId", "9"), "TEXT_GENERATION",
            "creative", "INTERNAL", "{}", true, false, false);
        List<AigLaunchErrorEnum> contextProblems = AigLaunchChecklist.check(missingContext);
        assertEquals(1, contextProblems.size(), "缺一个上下文键只报一次，不重复刷");
        assertEquals(AigLaunchErrorEnum.REQUIRED_INPUT_MISSING, contextProblems.get(0));
        // 给全了就不拦
        AigLaunchChecklist.Input fullContext = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of("productId", "brandId"),
            Map.of("productId", "9", "brandId", "3"), "TEXT_GENERATION", "creative", "INTERNAL", "{}",
            true, false, false);
        assertTrue(AigLaunchChecklist.check(fullContext).isEmpty());
    }

    @Test
    @DisplayName("资源类问题排在输入问题之后；项目无权与运行时异常各归各的码")
    void resourceProblemsHaveTheirOwnCodes() {
        AigLaunchChecklist.Input denied = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of(), Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{}",
            true, true, false);
        assertEquals(List.of(AigLaunchErrorEnum.PROJECT_ACCESS_DENIED), AigLaunchChecklist.check(denied));
        AigLaunchChecklist.Input unhealthy = new AigLaunchChecklist.Input("QUICK", "QUICK_CAPABILITY",
            "cap/x", null, List.of(), Map.of(), "TEXT_GENERATION", "creative", "INTERNAL", "{}",
            true, false, true);
        assertEquals(List.of(AigLaunchErrorEnum.RUNTIME_UNHEALTHY), AigLaunchChecklist.check(unhealthy));
    }

    @Test
    @DisplayName("读不懂的卡片（启动方式/目标类型非法）不进后续判定，直接报卡片不可用")
    void unknownCardFailsFast() {
        AigLaunchChecklist.Input unknownMode = new AigLaunchChecklist.Input("SOMETHING", "NAVIGATION",
            "AIGOV_TASK", null, List.of(), Map.of(), null, null, null, null, true, false, false);
        assertEquals(List.of(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE),
            AigLaunchChecklist.check(unknownMode));
        assertEquals(List.of(AigLaunchErrorEnum.ACTION_NOT_AVAILABLE), AigLaunchChecklist.check(null));
    }

    @Test
    @DisplayName("场景可用性是封闭集合：只有 STABLE 算可用")
    void scenarioUsabilityClosedSet() {
        assertTrue(AigLaunchChecklist.scenarioUsable("STABLE"));
        assertTrue(AigLaunchChecklist.scenarioUsable(" stable "));
        assertFalse(AigLaunchChecklist.scenarioUsable("CANDIDATE"), "候选版不可用于业务任务");
        assertFalse(AigLaunchChecklist.scenarioUsable("VALIDATED"));
        assertFalse(AigLaunchChecklist.scenarioUsable("DISABLED"));
        assertFalse(AigLaunchChecklist.scenarioUsable(null));
    }

}
