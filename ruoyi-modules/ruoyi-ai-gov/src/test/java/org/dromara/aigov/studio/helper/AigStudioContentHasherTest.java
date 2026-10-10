package org.dromara.aigov.studio.helper;

import org.dromara.aigov.studio.domain.AigStudioDraftContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 草稿内容规范化与哈希测试（专题 C §C3.1、§C11 ST-001）。
 *
 * <p><b>这条测试守的是一个会误报的边界</b>：页面按
 * {@code contentHash != lastPublishedHash} 显示「未发布改动」。
 * 若哈希对序列化顺序/空白敏感，界面就会对一份**内容没变**的草稿喊"你有未发布改动"，
 * 人就会去"重新提交"一次没变的内容；反之，真改动被判成没改，则会漏发布。
 * 两种都错，所以这里逐条钉住"什么算变、什么不算变"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioContentHasherTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    @DisplayName("★ 键顺序不同 → 同一个哈希（内容没变，不能被判成改动）")
    void keyOrderDoesNotChangeHash() {
        String a = "{\"role\":\"策划\",\"objective\":\"出脚本\"}";
        String b = "{\"objective\":\"出脚本\",\"role\":\"策划\"}";
        assertEquals(AigStudioContentHasher.hash(a), AigStudioContentHasher.hash(b));
    }

    @Test
    @DisplayName("★ 空白/换行/缩进不同 → 同一个哈希")
    void whitespaceDoesNotChangeHash() {
        String a = "{\"a\":1,\"b\":[1,2]}";
        String b = "{\n  \"a\" : 1 ,\n  \"b\" : [ 1 , 2 ]\n}\n";
        assertEquals(AigStudioContentHasher.hash(a), AigStudioContentHasher.hash(b));
    }

    @Test
    @DisplayName("★ 嵌套对象的键也排序（只排顶层不算规范化）")
    void nestedKeysAreSorted() {
        String a = "{\"outer\":{\"z\":1,\"a\":2}}";
        String b = "{\"outer\":{\"a\":2,\"z\":1}}";
        assertEquals(AigStudioContentHasher.hash(a), AigStudioContentHasher.hash(b));
        assertEquals("{\"outer\":{\"a\":2,\"z\":1}}", AigStudioContentHasher.canonicalize(a));
    }

    @Test
    @DisplayName("真改动必须变哈希（改值、加键、删键都要变）")
    void realChangesChangeHash() {
        String base = "{\"role\":\"策划\",\"objective\":\"出脚本\"}";
        assertNotEquals(AigStudioContentHasher.hash(base),
            AigStudioContentHasher.hash("{\"role\":\"策划\",\"objective\":\"出分镜\"}"),
            "改了值必须被判成改动");
        assertNotEquals(AigStudioContentHasher.hash(base),
            AigStudioContentHasher.hash("{\"role\":\"策划\",\"objective\":\"出脚本\",\"extra\":1}"),
            "加了键必须被判成改动");
        assertNotEquals(AigStudioContentHasher.hash(base),
            AigStudioContentHasher.hash("{\"role\":\"策划\"}"),
            "删了键必须被判成改动");
    }

    @Test
    @DisplayName("★ 数组顺序是内容的一部分（[1,2] 与 [2,1] 必须不同）")
    void arrayOrderMatters() {
        assertNotEquals(AigStudioContentHasher.hash("{\"l\":[1,2]}"),
            AigStudioContentHasher.hash("{\"l\":[2,1]}"),
            "数组顺序改了就是内容变了（比如步骤顺序/镜头顺序）");
    }

    @Test
    @DisplayName("数值归一：1 与 1.0 视为同一内容；1 与 2 不同")
    void numbersAreNormalized() {
        assertEquals(AigStudioContentHasher.hash("{\"n\":1}"), AigStudioContentHasher.hash("{\"n\":1.0}"),
            "1 与 1.0 是同一个数，不该被判成改动");
        assertEquals("{\"n\":1}", AigStudioContentHasher.canonicalize("{\"n\":1.00}"));
        assertNotEquals(AigStudioContentHasher.hash("{\"n\":1}"), AigStudioContentHasher.hash("{\"n\":2}"));
    }

    @Test
    @DisplayName("字符串转义：引号/反斜杠/换行/控制字符都被正确转义且稳定")
    void stringsAreEscapedStably() {
        String json = "{\"s\":\"a\\\"b\\\\c\\nd\\u0001e\"}";
        String canonical = AigStudioContentHasher.canonicalize(json);
        assertEquals("{\"s\":\"a\\\"b\\\\c\\nd\\u0001e\"}", canonical);
        assertEquals(AigStudioContentHasher.hash(json), AigStudioContentHasher.hash(canonical),
            "规范化是幂等的：对规范化结果再算一次哈希必须一致");
    }

    @Test
    @DisplayName("非 ASCII 原样保留（UTF-8），不被转义成 \\uXXXX")
    void nonAsciiPreserved() {
        assertEquals("{\"role\":\"详情页文案\"}",
            AigStudioContentHasher.canonicalize("{\"role\":\"详情页文案\"}"));
    }

    @Test
    @DisplayName("规范化的幂等性：canonicalize(canonicalize(x)) == canonicalize(x)")
    void canonicalizeIsIdempotent() {
        String[] samples = {
            "{\"b\":1,\"a\":[{\"y\":2,\"x\":1}]}",
            "{\"s\":\"含 \\\" 与 \\n 的文本\"}",
            "[]",
            "{}",
            "null"
        };
        for (String s : samples) {
            String once = AigStudioContentHasher.canonicalize(s);
            assertEquals(once, AigStudioContentHasher.canonicalize(once), "样本：" + s);
        }
    }

    @Test
    @DisplayName("非法输入：空白串与坏 JSON 都要报错（不能静默算出个哈希）")
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AigStudioContentHasher.hash("   "),
            "空白内容算不出有意义的哈希，必须报错而不是当成某个固定值");
        assertThrows(IllegalArgumentException.class, () -> AigStudioContentHasher.hash("{not json"));
    }

    @Test
    @DisplayName("null 按 JSON null 处理（有确定哈希），不抛异常")
    void nullIsHandled() {
        assertEquals("null", AigStudioContentHasher.canonicalize(null));
        assertEquals(AigStudioContentHasher.hash("null"), AigStudioContentHasher.hash(null));
    }

    @Test
    @DisplayName("★ 接真实内容模型：同一份 AigStudioDraftContent 序列化两次哈希一致；改一个分节哈希就变")
    void worksWithRealContentModel() {
        AigStudioDraftContent content = sampleContent();
        String json1 = MAPPER.writeValueAsString(content);
        String json2 = MAPPER.writeValueAsString(sampleContent());
        assertEquals(AigStudioContentHasher.hash(json1), AigStudioContentHasher.hash(json2),
            "同样的内容模型序列化两次必须同哈希（否则页面会无端报『有未发布改动』）");

        AigStudioDraftContent changed = sampleContent();
        changed.getPromptSections().put("output_contract", "必须包含数据来源与不确定性说明");
        assertNotEquals(AigStudioContentHasher.hash(json1),
            AigStudioContentHasher.hash(MAPPER.writeValueAsString(changed)),
            "改了一个 Prompt 分节，必须被识别为改动");
    }

    @Test
    @DisplayName("Prompt 分节键是固定的 8 个（Diff 与缺节校验都靠它对齐口径）")
    void promptSectionKeysAreFixed() {
        List<String> keys = AigStudioDraftContent.PROMPT_SECTION_KEYS;
        assertEquals(8, keys.size());
        assertEquals(keys.size(), keys.stream().distinct().count(), "分节键不能重复");
        assertTrue(keys.containsAll(List.of("role", "objective", "inputs", "workflow_rules",
            "tools_and_skills", "constraints", "output_contract", "uncertainty_policy")));
    }

    /**
     * 造一份有代表性的草稿内容。
     *
     * @return 内容模型
     */
    private static AigStudioDraftContent sampleContent() {
        AigStudioDraftContent content = new AigStudioDraftContent();
        content.setAgentName("详情页文案助手");
        content.setRoleDescription("电商详情页文案策划");
        content.setObjective("按已确认的产品事实输出可审定文案");
        content.setProhibitions("不得编造参数、不得引用未授权素材");
        content.setCoreCapabilities(List.of("卖点提炼", "结构化输出"));
        content.setPromptSections(new LinkedHashMap<>(Map.of(
            "role", "你是详情页文案策划",
            "objective", "依据产品事实输出文案",
            "inputs", "产品事实、品牌简报",
            "workflow_rules", "先核对事实再写作",
            "tools_and_skills", "无外部工具",
            "constraints", "不得编造参数",
            "output_contract", "JSON：{标题, 卖点[]}",
            "uncertainty_policy", "事实缺件时明确说明" )));
        content.setInputSchema("{\"type\":\"object\"}");
        content.setOutputSchema("{\"type\":\"object\"}");
        content.setProviderCapability("text_generation");
        content.setAllowExternal("N");
        return content;
    }

}
