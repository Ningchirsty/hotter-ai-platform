package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块配置接进生成链路的三处读法（V0.2 R23，文档 §24 的右栏字段落地）。
 *
 * <p>这一轮要证明的是"配了真的用上了"，不是"字段存下来了"：</p>
 * <ol>
 *   <li>{@link CreativeScreenModuleConfig}：屏 spec 里烙的模块配置读得对（含脏数据兜底）；</li>
 *   <li>{@link DnaPromptBuilder}：模块视觉表达真的进了提示词，并且如实记进 applied；</li>
 *   <li>没配的时候**行为与以前逐字一致**（不能因为加了功能就把默认提示词改了）。</li>
 * </ol>
 */
class CreativeModuleGenerationWiringTest {

    private final DnaPromptBuilder builder = new DnaPromptBuilder();

    @Test
    @DisplayName("屏 spec 里的模块参考图/视觉表达读得对（参考图取第一个能解析成数字的）")
    void readsModuleConfigFromSpec() {
        String spec = "{\"shot\":\"正面\",\"referenceFileIds\":[\"REF-A\",\"123456789\",\"987654321\"],"
            + "\"visualRules\":\"暖光、木桌、留白多\"}";
        CreativeScreenModuleConfig config = CreativeScreenModuleConfig.parse(spec);

        assertEquals(123456789L, config.referenceFileId(), "REF-A 不是数字，应跳到下一个");
        assertEquals("暖光、木桌、留白多", config.visualRules());
    }

    @Test
    @DisplayName("模块配置缺失/脏数据 → 当没有（不猜、也不炸）")
    void missingOrBrokenModuleConfigIsEmpty() {
        assertNull(CreativeScreenModuleConfig.parse(null).referenceFileId());
        assertNull(CreativeScreenModuleConfig.parse("").visualRules());
        assertNull(CreativeScreenModuleConfig.parse("not-json").referenceFileId());
        assertNull(CreativeScreenModuleConfig.parse("{\"referenceFileIds\":\"single\"}").referenceFileId(),
            "不是数组就当没有（而不是解析出半个值）");
        assertNull(CreativeScreenModuleConfig.parse("{\"referenceFileIds\":[\"A\",\"B\"]}").referenceFileId(),
            "全是非数字编码 → 没有可用的附件ID");
        assertNull(CreativeScreenModuleConfig.parse("{\"visualRules\":\"   \"}").visualRules());
    }

    @Test
    @DisplayName("模块视觉表达进了提示词，并记进 applied（可追溯）")
    void moduleVisualRulesReachPrompt() {
        ObjectNode dna = VisualDnaSchema.empty();
        DnaPromptBuilder.Prompt withRules = builder.build(dna, "花瓶", "主图",
            (CpBrandBriefVo) null, "本屏讲配色", "暖光、木桌、留白多");
        DnaPromptBuilder.Prompt without = builder.build(dna, "花瓶", "主图",
            (CpBrandBriefVo) null, "本屏讲配色", null);

        assertTrue(withRules.prompt().contains("该屏的模块视觉表达：暖光、木桌、留白多"),
            withRules.prompt());
        assertTrue(withRules.applied().contains("module.visualRules"), withRules.applied().toString());
        assertFalse(without.prompt().contains("该屏的模块视觉表达"), "没配就不该出现这一句");
        assertFalse(without.applied().contains("module.visualRules"));
        // 没配时与"根本没有这个功能"逐字一致：把 withRules 里那段去掉应等于 without
        String stripped = withRules.prompt().replace("该屏的模块视觉表达：暖光、木桌、留白多。", "");
        assertEquals(without.prompt(), stripped, "加功能不能顺手改默认提示词");
    }

    @Test
    @DisplayName("模块视觉表达超长 → 按同一上限截断并记进 omitted（不静默丢）")
    void longModuleVisualRulesAreTruncatedHonestly() {
        String longRules = "暖光".repeat(400);
        DnaPromptBuilder.Prompt prompt = builder.build(VisualDnaSchema.empty(), "花瓶", "主图",
            (CpBrandBriefVo) null, null, longRules);

        assertTrue(prompt.applied().contains("module.visualRules"));
        assertTrue(prompt.omitted().stream().anyMatch(o -> o.contains("模块视觉表达超过")),
            prompt.omitted().toString());
    }
}
