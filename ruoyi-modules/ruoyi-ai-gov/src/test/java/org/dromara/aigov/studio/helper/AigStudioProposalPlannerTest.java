package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变更提案白名单应用器测试（增量 21／附件 07 增量 6，ST-003/ST-004）。
 *
 * <p>守的是"Copilot 能改什么"这条边界：白名单外的路径必须<b>拒绝</b>（尤其类别、
 * 能力编码、外发许可、Schema），值类型不符必须拒绝，同一路径重复必须拒绝——
 * 这些错误都不会自己暴露，只会让一次"采纳建议"悄悄改掉了不该改的东西。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioProposalPlannerTest {

    private static AigStudioDraftContent content() {
        AigStudioDraftContent content = new AigStudioDraftContent();
        content.setObjective("旧目标");
        content.getPromptSections().put("role", "旧角色");
        content.setCoreCapabilities(new ArrayList<>(List.of("a")));
        return content;
    }

    @Test
    @DisplayName("文本字段：可改，且预览给出改前/改后")
    void textFieldIsApplied() {
        AigStudioDraftContent content = content();

        List<AigStudioProposalPlanner.DiffItem> diff = AigStudioProposalPlanner.apply(content,
            List.of(new AigStudioProposalPlanner.Operation("/objective", "新目标")));

        assertEquals("旧目标", diff.get(0).before());
        assertEquals("新目标", diff.get(0).after());
        assertEquals("新目标", content.getObjective());
    }

    @Test
    @DisplayName("Prompt 分节：可改；不认识的分节键拒绝")
    void promptSectionIsApplied() {
        AigStudioDraftContent content = content();

        AigStudioProposalPlanner.apply(content,
            List.of(new AigStudioProposalPlanner.Operation("/promptSections/role", "新角色")));
        assertEquals("新角色", content.getPromptSections().get("role"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AigStudioProposalPlanner.apply(content(),
                List.of(new AigStudioProposalPlanner.Operation("/promptSections/nope", "x"))));
        assertTrue(error.getMessage().contains("不认识的 Prompt 分节"), error.getMessage());
    }

    @Test
    @DisplayName("清单字段：可改；非字符串数组拒绝")
    void listFieldIsApplied() {
        AigStudioDraftContent content = content();

        List<AigStudioProposalPlanner.DiffItem> diff = AigStudioProposalPlanner.apply(content,
            List.of(new AigStudioProposalPlanner.Operation("/coreCapabilities", List.of("b", "c"))));
        assertEquals(List.of("b", "c"), content.getCoreCapabilities());
        assertEquals("a", diff.get(0).before());
        assertEquals("b，c", diff.get(0).after());

        assertThrows(IllegalArgumentException.class, () -> AigStudioProposalPlanner.apply(content(),
            List.of(new AigStudioProposalPlanner.Operation("/coreCapabilities", "不是数组"))));
        assertThrows(IllegalArgumentException.class, () -> AigStudioProposalPlanner.apply(content(),
            List.of(new AigStudioProposalPlanner.Operation("/coreCapabilities", List.of(1, 2)))));
    }

    @Test
    @DisplayName("★越权路径一律拒绝：类别 / 能力编码 / 外发许可 / Schema / 页面定制")
    void unauthorizedPathsAreRejected() {
        for (String path : List.of("/agentCategory", "/providerCapability", "/allowExternal",
            "/inputSchema", "/outputSchema", "/pageCustomizationJson", "/不存在的字段")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AigStudioProposalPlanner.apply(content(),
                    List.of(new AigStudioProposalPlanner.Operation(path, "x"))),
                "路径必须被拒绝：" + path);
            assertTrue(error.getMessage().contains("不允许改这个字段"), path + " -> " + error.getMessage());
        }
    }

    @Test
    @DisplayName("文本路径给了数组 / 清单路径给了文本：都拒绝（不做隐式转换）")
    void mismatchedValueTypesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AigStudioProposalPlanner.apply(content(),
            List.of(new AigStudioProposalPlanner.Operation("/objective", List.of("x")))));
    }

    @Test
    @DisplayName("空清单 / 缺路径 / 同路径重复：都拒绝")
    void malformedOperationsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> AigStudioProposalPlanner.apply(content(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> AigStudioProposalPlanner.apply(content(),
            List.of(new AigStudioProposalPlanner.Operation("  ", "x"))));
        IllegalArgumentException duplicated = assertThrows(IllegalArgumentException.class,
            () -> AigStudioProposalPlanner.apply(content(), List.of(
                new AigStudioProposalPlanner.Operation("/objective", "a"),
                new AigStudioProposalPlanner.Operation("/objective", "b"))));
        assertTrue(duplicated.getMessage().contains("多次"), duplicated.getMessage());
    }

    @Test
    @DisplayName("内容为空：拒绝（不能对着空内容提案）")
    void nullContentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AigStudioProposalPlanner.apply(null,
            List.of(new AigStudioProposalPlanner.Operation("/objective", "x"))));
    }

    @Test
    @DisplayName("一次提案多处改动：按顺序逐条应用并逐条给预览")
    void multipleOperationsKeepOrder() {
        AigStudioDraftContent content = content();

        List<AigStudioProposalPlanner.DiffItem> diff = AigStudioProposalPlanner.apply(content, List.of(
            new AigStudioProposalPlanner.Operation("/objective", "新目标"),
            new AigStudioProposalPlanner.Operation("/promptSections/role", "新角色")));

        assertEquals(2, diff.size());
        assertEquals("/objective", diff.get(0).path());
        assertEquals("/promptSections/role", diff.get(1).path());
        assertEquals("新目标", content.getObjective());
        assertEquals("新角色", content.getPromptSections().get("role"));
    }

}
