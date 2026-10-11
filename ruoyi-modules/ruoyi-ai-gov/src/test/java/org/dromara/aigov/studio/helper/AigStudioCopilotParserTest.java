package org.dromara.aigov.studio.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话式 Copilot 输出解析测试（附件 07 增量 6）。
 *
 * <p><b>这条测试守住两件相反的事</b>：①模型把 JSON 包在代码围栏里、前后带一句"好的"，
 * 这些<b>不该</b>被判成"模型不行"；②拿不到 JSON 时<b>必须</b>明确失败，不能"尽力猜一个"——
 * 猜出来的操作会被当成模型的原意写进提案。另外：越权路径在这里<b>必须通过</b>
 * （白名单是规划器的职责，这里再判一遍就会出现两份会漂移的白名单）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioCopilotParserTest {

    private static final String PLAIN = "{\"summary\":\"改目标\","
        + "\"operations\":[{\"path\":\"/objective\",\"value\":\"新目标\"}]}";

    @Test
    @DisplayName("纯 JSON：summary 与 operations 都取到，值原样保留")
    void parsesPlainJson() {
        AigStudioCopilotParser.Parsed parsed = AigStudioCopilotParser.parse(PLAIN);

        assertEquals("改目标", parsed.summary());
        assertEquals(1, parsed.operations().size());
        assertEquals("/objective", parsed.operations().get(0).getPath());
        assertEquals("新目标", parsed.operations().get(0).getValue());
    }

    @Test
    @DisplayName("★ 容忍代码围栏与前后杂文（模型习惯；这些不该被判成格式错误）")
    void toleratesFencesAndProse() {
        String fenced = "好的，我来改：\n```json\n" + PLAIN + "\n```\n需要我继续吗？";
        AigStudioCopilotParser.Parsed parsed = AigStudioCopilotParser.parse(fenced);
        assertEquals("/objective", parsed.operations().get(0).getPath());

        String bareFence = "```\n" + PLAIN + "\n```";
        assertEquals(1, AigStudioCopilotParser.parse(bareFence).operations().size());

        String proseOnly = "这是我的建议：" + PLAIN + "（以上）";
        assertEquals(1, AigStudioCopilotParser.parse(proseOnly).operations().size());
    }

    @Test
    @DisplayName("★ 拿不到 JSON 时必须明确失败（不猜）")
    void rejectsWhenNoJson() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("我觉得可以改一改主要责任")).getMessage()
            .contains("没有找到 JSON 对象"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("")).getMessage().contains("没有返回任何内容"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse(null)).getMessage().contains("没有返回任何内容"));
    }

    @Test
    @DisplayName("形状不对：不是对象 / 缺 operations / operations 为空 / 不是数组 各自给出可读原因")
    void rejectsBadShapes() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("[1,2]")).getMessage().contains("必须是一个 JSON 对象"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"summary\":\"x\"}")).getMessage()
            .contains("没有 operations"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":[]}")).getMessage()
            .contains("没有任何改动"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":\"oops\"}")).getMessage()
            .contains("必须是数组"));
    }

    @Test
    @DisplayName("操作项：缺 path / path 空白 / 缺 value 都要拒绝，并指出是第几条")
    void rejectsBadOperations() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":[{\"value\":\"x\"}]}")).getMessage()
            .contains("第 1 条操作缺少 path"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":[{\"path\":\"  \",\"value\":\"x\"}]}"))
            .getMessage().contains("第 1 条操作缺少 path"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":[{\"path\":\"/objective\"}]}")).getMessage()
            .contains("缺少 value"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse("{\"operations\":[\"oops\"]}")).getMessage()
            .contains("不是 JSON 对象"));
    }

    @Test
    @DisplayName("★ 越权路径在这里必须通过：白名单是规划器的职责（两份白名单必然漂移）")
    void doesNotEnforceWhitelist() {
        String reserved = "{\"operations\":[{\"path\":\"/agentCategory\",\"value\":\"QA\"}]}";
        assertEquals("/agentCategory", AigStudioCopilotParser.parse(reserved).operations().get(0).getPath());

        // 值类型也不在这里判（清单路径给了字符串照样过；规划器会拒绝）
        String wrongType = "{\"operations\":[{\"path\":\"/allowedTools\",\"value\":\"不是数组\"}]}";
        assertEquals("不是数组", AigStudioCopilotParser.parse(wrongType).operations().get(0).getValue());
    }

    @Test
    @DisplayName("值原样保留类型（数组/对象/数字都不被转成字符串）")
    void keepsValueTypes() {
        String json = "{\"operations\":[{\"path\":\"/allowedTools\",\"value\":[\"a\",\"b\"]},"
            + "{\"path\":\"/objective\",\"value\":123}]}";
        List<?> values = AigStudioCopilotParser.parse(json).operations().stream()
            .map(operation -> operation.getValue()).toList();

        assertEquals(List.of("a", "b"), values.get(0));
        assertTrue(values.get(1) instanceof Number, "数字应保持数字：" + values.get(1));
    }

    @Test
    @DisplayName("summary：可空、超长截断到 200（列宽一致）")
    void capsSummary() {
        assertNull(AigStudioCopilotParser.parse("{\"operations\":[{\"path\":\"/objective\",\"value\":\"x\"}]}")
            .summary());
        assertNull(AigStudioCopilotParser.parse("{\"summary\":\"   \",\"operations\":"
            + "[{\"path\":\"/objective\",\"value\":\"x\"}]}").summary());

        String longSummary = "改".repeat(300);
        AigStudioCopilotParser.Parsed parsed = AigStudioCopilotParser.parse(
            "{\"summary\":\"" + longSummary + "\",\"operations\":[{\"path\":\"/objective\",\"value\":\"x\"}]}");
        assertEquals(200, parsed.summary().length());
    }

    @Test
    @DisplayName("解析失败时给出的是业务可读原因，且不吞掉真正的解析错误（JSON 语法错看得出来）")
    void reportsBrokenJson() {
        String broken = "{\"operations\":[{\"path\":\"/objective\",\"value\":\"x\",}]}";
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> AigStudioCopilotParser.parse(broken)).getMessage().contains("无法解析"));
    }

}
