package org.dromara.creative.helper;

import org.dromara.creative.domain.vo.DpBrandBriefVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 品牌 Brief 与屏文案进提示词的测试（R7）。
 *
 * <p>钉住三件容易「看起来做了、其实没做」的事：</p>
 * <ol>
 *   <li>Brief 的必显信息/主推卖点真的进了正向提示词，禁用词真的进了负向提示词；</li>
 *   <li>长度上限（正向 1000、负向 500）真的被守住，且<b>只放完整条目</b>；
 *       放不下的条目必须出现在 {@code omitted} 里——静默丢弃等于让人以为填了没生效。</li>
 *   <li>屏文案作为「这一屏画面要讲什么」进提示词，而不是把基因内容挤掉。</li>
 * </ol>
 *
 * <p>全部是纯函数断言：不连库、不调模型。</p>
 *
 * @author creative
 */
class DnaPromptBuilderBriefTest {

    private final DnaPromptBuilder builder = new DnaPromptBuilder();

    private static final int MAX_PROMPT = 1000;
    private static final int MAX_NEGATIVE = 500;

    @Test
    @DisplayName("Brief 的必显/卖点进正向提示词，禁用词进负向提示词，并如实列出用到的维度")
    void briefFeedsPrompt() {
        // 禁用词按逗号写也可以（负向提示词本身就是逗号分隔的词表，按词计量）
        DpBrandBriefVo brief = brief("品牌名「鸢尾」\n官网 hottter.cn", "最便宜,国家级，纯天然",
            "1. 手工缠花\n2. UV 喷漆");

        DnaPromptBuilder.Prompt prompt =
            builder.build(VisualDnaSchema.empty(), "鸢尾花", "HERO 主图", brief, "一朵蓝紫渐变的鸢尾花特写");

        assertTrue(prompt.prompt().contains("必须出现：品牌名「鸢尾」"), prompt.prompt());
        assertTrue(prompt.prompt().contains("官网 hottter.cn"), prompt.prompt());
        assertTrue(prompt.prompt().contains("主推卖点：1. 手工缠花"), prompt.prompt());
        assertTrue(prompt.prompt().contains("2. UV 喷漆"), prompt.prompt());
        assertTrue(prompt.prompt().contains("本屏画面要讲什么：一朵蓝紫渐变的鸢尾花特写"), prompt.prompt());
        assertTrue(prompt.negativePrompt().contains("最便宜"), prompt.negativePrompt());
        assertTrue(prompt.negativePrompt().contains("国家级"), prompt.negativePrompt());
        assertTrue(prompt.negativePrompt().contains("纯天然"), prompt.negativePrompt());
        assertTrue(prompt.applied().contains("brief.mustShow(2)"), prompt.applied().toString());
        assertTrue(prompt.applied().contains("brief.mainPush(2)"), prompt.applied().toString());
        assertTrue(prompt.applied().contains("brief.forbiddenWords(3)"), prompt.applied().toString());
        assertTrue(prompt.applied().contains("screenText(屏文案)"), prompt.applied().toString());
        assertTrue(prompt.omitted().isEmpty(), prompt.omitted().toString());
    }

    @Test
    @DisplayName("主推卖点只取前 3 条（行首数字即优先级），其余如实记进 omitted")
    void mainPushTakesTopThree() {
        DpBrandBriefVo brief = brief(null, null,
            "1. 第一卖点\n2. 第二卖点\n3. 第三卖点\n4. 第四卖点\n5. 第五卖点");

        DnaPromptBuilder.Prompt prompt =
            builder.build(VisualDnaSchema.empty(), "鸢尾花", "卖点一", brief, null);

        assertTrue(prompt.prompt().contains("1. 第一卖点"), prompt.prompt());
        assertTrue(prompt.prompt().contains("3. 第三卖点"), prompt.prompt());
        assertFalse(prompt.prompt().contains("4. 第四卖点"), prompt.prompt());
        assertEquals(1, prompt.omitted().size(), prompt.omitted().toString());
        assertTrue(prompt.omitted().get(0).contains("4. 第四卖点"), prompt.omitted().toString());
        assertTrue(prompt.omitted().get(0).contains("5. 第五卖点"), prompt.omitted().toString());
    }

    @Test
    @DisplayName("超长必显信息/禁用词：只放完整条目，上限守住，未放入的逐条列出")
    void respectsLengthBudgetsAndWholeItems() {
        List<String> mustShow = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            mustShow.add("必显项" + i + "：" + "内容占位".repeat(10));
        }
        List<String> forbidden = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            forbidden.add("禁用词" + i + "占位数据");
        }
        DpBrandBriefVo brief = brief(String.join("\n", mustShow), String.join(",", forbidden),
            "1. 卖点甲\n2. 卖点乙");

        DnaPromptBuilder.Prompt prompt =
            builder.build(VisualDnaSchema.empty(), "鸢尾花", "主图", brief, null);

        assertTrue(prompt.prompt().length() <= MAX_PROMPT,
            "正向提示词必须守住 " + MAX_PROMPT + " 字，实际 " + prompt.prompt().length());
        assertTrue(prompt.negativePrompt().length() <= MAX_NEGATIVE,
            "负向提示词必须守住 " + MAX_NEGATIVE + " 字，实际 " + prompt.negativePrompt().length());

        // 关键断言：进提示词的必显信息必须是「完整的条目」——
        // 把「必须出现：」到句号之间的内容按顿号切开，每一段都必须能对上原始条目，
        // 出现半截（例如「必显项25：内容占位内容占位」被砍断）就会失败
        String section = prompt.prompt().substring(
            prompt.prompt().indexOf("必须出现：") + "必须出现：".length(),
            prompt.prompt().indexOf("。", prompt.prompt().indexOf("必须出现：")));
        int placed = 0;
        for (String part : section.split("、")) {
            assertTrue(mustShow.contains(part), "提示词里出现了被截断/改写的必显条目：" + part);
            placed++;
        }
        assertTrue(placed > 0, "至少要放得下一条必显信息，否则说明预算算错了");

        // 未放入的条目：数量必须如实写出（列表可能被截长，但条数不能少）
        int notPlaced = mustShow.size() - placed;
        String briefNote = prompt.omitted().stream()
            .filter(note -> note.startsWith("必显信息"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未放入的条目必须被如实记下来：" + prompt.omitted()));
        assertTrue(briefNote.contains("有 " + notPlaced + " 条"),
            "未放入条数必须如实写出：" + briefNote);
        assertTrue(prompt.applied().contains("brief.mustShow(" + placed + ")"),
            "applied 里的条数必须等于真正放进提示词的条数：" + prompt.applied());
    }

    @Test
    @DisplayName("没有 Brief、没有屏文案时，提示词与 R7 之前一致（不因为没填而变空）")
    void worksWithoutBrief() {
        DnaPromptBuilder.Prompt prompt =
            builder.build(VisualDnaSchema.empty(), "鸢尾花", "HERO 主图", null, null);

        assertNotNull(prompt.prompt());
        assertTrue(prompt.prompt().contains("鸢尾花"), prompt.prompt());
        assertTrue(prompt.prompt().contains("电商详情页HERO 主图"), prompt.prompt());
        assertFalse(prompt.prompt().contains("必须出现"), prompt.prompt());
        assertFalse(prompt.prompt().contains("主推卖点"), prompt.prompt());
        assertTrue(prompt.omitted().isEmpty(), prompt.omitted().toString());
        // 没有基因时默认风格仍然在（老行为不变）
        assertTrue(prompt.prompt().contains("现代简约"), prompt.prompt());
    }

    private static DpBrandBriefVo brief(String mustShow, String forbiddenWords, String mainPush) {
        DpBrandBriefVo vo = new DpBrandBriefVo();
        vo.setConfigured(true);
        vo.setStatus("CONFIRMED");
        vo.setMustShow(mustShow);
        vo.setForbiddenWords(forbiddenWords);
        vo.setMainPush(mainPush);
        return vo;
    }

}
