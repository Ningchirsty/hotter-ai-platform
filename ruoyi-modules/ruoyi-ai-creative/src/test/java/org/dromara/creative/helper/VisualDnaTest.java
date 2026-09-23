package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视觉基因相关规则的单元测试（不碰 DB、不碰 GPU、不调模型）。
 *
 * <p>盯住三件事：</p>
 * <ol>
 *     <li>DNA 校验：不自洽的基因不能锁定（否则后面所有分镜与出图都跟着错）；</li>
 *     <li>提示词派生：每一项都要能指回 DNA 的字段，DNA 为空时也不能产出半句话；</li>
 *     <li>模型输出验收：格式不可信的字段必须被丢弃，且「全是胡说」时整体不采纳
 *     （这是「不编造、不硬凑」的执行点，实际发生过同类问题所以单独钉）。</li>
 * </ol>
 */
class VisualDnaTest {

    // ------------------------------------------------------------------
    // 1. Schema 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("空 DNA 不可锁定：缺主色、缺背景、缺占比都要报出来")
    void emptyDnaNotLockable() {
        List<String> issues = VisualDnaSchema.validate(VisualDnaSchema.empty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("主色")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("背景色")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("产品占比")), issues.toString());
    }

    @Test
    @DisplayName("非法色值 / 非法枚举 / 占比区间颠倒都要被拦下")
    void invalidValuesRejected() {
        ObjectNode dna = validDna();
        dna.withObject("/colors").put("primary", "红色");
        dna.put("saturation", "VERY_HIGH");
        VisualDnaSchema.setProductRatio(dna, 80, 30);

        List<String> issues = VisualDnaSchema.validate(dna);
        assertTrue(issues.stream().anyMatch(i -> i.contains("主色")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("饱和度")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("颠倒")), issues.toString());
    }

    @Test
    @DisplayName("自洽的 DNA 校验通过")
    void validDnaPasses() {
        assertEquals(List.of(), VisualDnaSchema.validate(validDna()));
    }

    // ------------------------------------------------------------------
    // 2. 种子构造（事实 → 基因）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("颜色事实能映射成色值，品牌调性进风格关键词，且逐项有证据")
    void seedMapsFacts() {
        DnaSeedBuilder builder = new DnaSeedBuilder();
        DnaSeedBuilder.SeedResult seed = builder.build(new DnaSeedBuilder.SeedInput(
            1L, "积木花", "QW-01",
            List.of(
                new DnaSeedBuilder.FactRow("color", "颜色", "粉色", "产品参数表V2 第3行"),
                new DnaSeedBuilder.FactRow("brand_tone", "品牌调性说明", "清新、治愈、自然", "品牌手册"),
                new DnaSeedBuilder.FactRow("main_version", "主体版本", "V2 静态款", "产品资料")),
            List.of("原图2.jpg")));

        assertTrue(seed.dna().path("styleKeywords").toString().contains("清新"), seed.dna().toString());
        assertTrue(seed.dna().path("colors").path("primary").asText().matches("^#[0-9A-F]{6}$"),
            seed.dna().toString());
        assertTrue(seed.notes().stream().anyMatch(n -> n.contains("主色")), seed.notes().toString());
        assertTrue(VisualDnaSchema.evidenceOf(seed.dna()).stream()
                .anyMatch(e -> "FACT".equals(e.get("kind")) && String.valueOf(e.get("source")).contains("color")),
            seed.dna().toString());
        assertEquals(List.of(), VisualDnaSchema.validate(seed.dna()));
    }

    @Test
    @DisplayName("颜色事实认不出时必须留空并提示，而不是猜一个色值")
    void seedDoesNotGuessColor() {
        DnaSeedBuilder builder = new DnaSeedBuilder();
        DnaSeedBuilder.SeedResult seed = builder.build(new DnaSeedBuilder.SeedInput(
            1L, "积木花", null,
            List.of(new DnaSeedBuilder.FactRow("color", "颜色", "渐变色", null)),
            List.of()));

        assertNull(seed.dna().path("colors").path("primary").asText(null));
        assertTrue(seed.missing().stream().anyMatch(m -> m.contains("主色")), seed.missing().toString());
    }

    // ------------------------------------------------------------------
    // 3. 提示词派生
    // ------------------------------------------------------------------

    @Test
    @DisplayName("提示词用到 DNA 的色彩/光线/占比，并如实列出用到的维度")
    void promptUsesDna() {
        DnaPromptBuilder builder = new DnaPromptBuilder();
        DnaPromptBuilder.Prompt prompt = builder.build(validDna(), "积木花", "HERO 主图");

        assertTrue(prompt.prompt().contains("积木花"), prompt.prompt());
        assertTrue(prompt.prompt().contains("#E8543F"), prompt.prompt());
        assertTrue(prompt.prompt().contains("柔和散射光"), prompt.prompt());
        assertTrue(prompt.prompt().contains("45%~65%"), prompt.prompt());
        assertTrue(prompt.applied().contains("colors"), prompt.applied().toString());
        assertTrue(prompt.applied().contains("lighting"), prompt.applied().toString());
        assertFalse(prompt.negativePrompt().isBlank(), prompt.negativePrompt());
    }

    @Test
    @DisplayName("没有基因时也要给出可用的默认提示词（不留半句话）")
    void promptWorksWithoutDna() {
        DnaPromptBuilder builder = new DnaPromptBuilder();
        DnaPromptBuilder.Prompt prompt = builder.build(null, null, null);
        assertNotNull(prompt.prompt());
        assertTrue(prompt.prompt().contains("电商详情页"), prompt.prompt());
        assertTrue(prompt.prompt().contains("当前产品"), prompt.prompt());
        assertTrue(prompt.negativePrompt().contains("水印"), prompt.negativePrompt());
    }

    // ------------------------------------------------------------------
    // 4. 模型输出验收（不编造的执行点）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("模型输出里的非法色值/非法枚举/越界占比被丢弃，合法项保留")
    void modelOutputPartiallyAccepted() {
        VisualBrainAdapter adapter = new VisualBrainAdapter(new EmptyProvider());
        String output = """
            {"styleKeywords":["清新","治愈"],
             "colors":{"primary":"#12AB34","secondary":"蓝色","background":"#FFFFFF"},
             "saturation":"EXTREME",
             "lighting":{"type":"SOFT","direction":"SIDE"},
             "productRatio":{"min":40,"max":70},
             "sceneType":"纯色底"}
            """;
        VisualBrainAdapter.Analysis result = adapter.accept(output, "fake-model", "trace-1");

        assertTrue(result.applied(), String.valueOf(result.reason()));
        assertEquals("#12AB34", result.patch().path("colors").path("primary").asText());
        assertFalse(result.patch().path("colors").has("secondary"), "非法色值不该被采纳");
        assertFalse(result.patch().has("saturation"), "非法枚举不该被采纳");
        assertEquals("SIDE", result.patch().path("lighting").path("direction").asText());
        assertEquals(40, result.patch().path("productRatio").path("min").asInt());
        assertTrue(result.fields().contains("colors.primary"), result.fields().toString());
        assertNotNull(result.reason(), "被丢弃的字段必须留下原因");
        assertTrue(result.reason().contains("colors.secondary"), result.reason());
    }

    @Test
    @DisplayName("模型输出全是胡说时整体不采纳（不许凑数）")
    void modelOutputAllGarbageNotApplied() {
        VisualBrainAdapter adapter = new VisualBrainAdapter(new EmptyProvider());
        String output = """
            {"colors":{"primary":"深蓝"},"saturation":"VERY_HIGH","productRatio":{"min":200,"max":300}}
            """;
        VisualBrainAdapter.Analysis result = adapter.accept(output, "fake-model", "trace-2");

        assertFalse(result.applied(), "没有任何可信核心字段时必须不采纳");
        assertTrue(result.reason().contains("没有可采纳的核心字段"), result.reason());
    }

    @Test
    @DisplayName("模型输出不是 JSON 时整体丢弃")
    void modelOutputNotJson() {
        VisualBrainAdapter adapter = new VisualBrainAdapter(new EmptyProvider());
        VisualBrainAdapter.Analysis result = adapter.accept("这看起来像一段自然语言描述", "fake-model", "trace-3");
        assertFalse(result.applied());
        assertTrue(result.reason().contains("JSON"), result.reason());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private ObjectNode validDna() {
        ObjectNode dna = VisualDnaSchema.empty();
        dna.withArray("styleKeywords").add("现代简约").add("清爽留白");
        dna.withArray("avoidKeywords").add("文字水印");
        ObjectNode colors = dna.withObject("/colors");
        colors.put("primary", "#E8543F");
        colors.put("background", "#F5F5F3");
        dna.put("saturation", "MEDIUM");
        dna.put("contrastLevel", "MEDIUM");
        dna.put("whitespaceLevel", "HIGH");
        dna.put("sceneType", "纯色底");
        VisualDnaSchema.setLighting(dna, "SOFT", "FRONT");
        VisualDnaSchema.setProductRatio(dna, 45, 65);
        return dna;
    }

    /**
     * 空的 ObjectProvider：适配器在探测阶段会回落到「治理层未启用」，正是我们要的单测环境。
     */
    private static final class EmptyProvider
        implements org.springframework.beans.factory.ObjectProvider<org.dromara.aigov.service.IAigInvokeService> {

        @Override
        public org.dromara.aigov.service.IAigInvokeService getObject(Object... args) {
            return null;
        }

        @Override
        public org.dromara.aigov.service.IAigInvokeService getIfAvailable() {
            return null;
        }

        @Override
        public org.dromara.aigov.service.IAigInvokeService getIfUnique() {
            return null;
        }

        @Override
        public org.dromara.aigov.service.IAigInvokeService getObject() {
            return null;
        }
    }

}
