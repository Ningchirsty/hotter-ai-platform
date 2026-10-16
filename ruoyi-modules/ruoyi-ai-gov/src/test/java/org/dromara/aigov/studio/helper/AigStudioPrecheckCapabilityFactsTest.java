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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「所需能力」事实进入预检后的分级判定（Q6 后续、训练台能力下拉的配套预检）。
 *
 * <p><b>这条测试守的两件事</b>：</p>
 * <ol>
 *     <li>分级不能搞反：**能力不存在 / 已停用**必须阻断（编码错了就不可能调通）；
 *         **缺候选 / 缺策略 / 候选不健康**只能提醒——训练台按 D07 明确允许先存半成品，
 *         把它们升级成阻断会直接推翻那条语义。</li>
 *     <li>没传事实（{@code facts == null}）时**一个字都不能报**：不判 ≠ 判成"不存在"，
 *         否则任何一处漏传事实的调用都会突然宣称"能力不存在"。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigStudioPrecheckCapabilityFactsTest {

    private static final String CODE = "text_generation";

    /**
     * 只用于"把 JSON 解析成内容对象"的序列化器（与生产代码同一实现）。
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 一份只有能力字段可变的合法内容（其余字段都填好，确保新增的问题项只可能来自能力事实）。
     *
     * @param capabilityCode 能力编码（可空）
     * @return 内容
     */
    private static AigStudioDraftContent content(String capabilityCode) {
        AigStudioDraftContent content = new AigStudioDraftContent();
        Map<String, String> sections = new LinkedHashMap<>();
        for (String key : AigStudioDraftContent.PROMPT_SECTION_KEYS) {
            sections.put(key, "内容-" + key);
        }
        content.setPromptSections(sections);
        content.setAgentCategory("PLANNING");
        content.setProviderCapability(capabilityCode);
        content.setAllowExternal("N");
        content.setOutputSchema("{\"type\":\"object\"}");
        return content;
    }

    /**
     * 造一份能力事实。
     *
     * @param exists     是否存在
     * @param status     状态（0 正常 / 1 停用）
     * @param candidates 候选数
     * @param health     候选健康汇总
     * @param covered    已配策略等级数
     * @param total      应配策略等级数
     * @return 事实
     */
    private static AigStudioCapabilityFacts facts(boolean exists, String status, int candidates,
                                                  String health, int covered, int total) {
        return new AigStudioCapabilityFacts(CODE, exists, status, "图片生成", candidates, health, covered, total);
    }

    /**
     * 提取某个编码的问题项数量。
     *
     * @param result 预检结论
     * @param code   问题码
     * @return 数量
     */
    private static long count(AigStudioPrecheck.Result result, String code) {
        long blockers = result.blockers().stream().filter(item -> code.equals(item.getCode())).count();
        long warnings = result.warnings().stream().filter(item -> code.equals(item.getCode())).count();
        return blockers + warnings;
    }

    @Test
    @DisplayName("★ 不传事实时一个字都不报：不判 ≠ 判成「不存在」")
    void withoutFactsNothingIsReported() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content(CODE), null);
        assertTrue(result.passed());
        assertTrue(result.warnings().isEmpty());
        assertEquals(0, count(result, "CAPABILITY_NOT_FOUND"));
        assertEquals(0, count(result, "CAPABILITY_DISABLED"));
    }

    @Test
    @DisplayName("★ 能力不存在 → 阻断（编码写错时不能靠提醒糊过去）")
    void notFoundIsBlocker() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content(CODE), AigStudioCapabilityFacts.missing(CODE));
        assertFalse(result.passed());
        assertEquals(1, count(result, "CAPABILITY_NOT_FOUND"));
        assertTrue(result.blockerMessages().get(0).contains(CODE));
        // 不存在时不应再叠"缺候选/缺策略"的提醒——它连能力都没有，谈候选没有意义
        assertEquals(0, count(result, "CAPABILITY_NO_CANDIDATE"));
        assertEquals(0, count(result, "CAPABILITY_NO_ROUTE_POLICY"));
    }

    @Test
    @DisplayName("★ 能力已停用 → 阻断，并说清「停用后调用一律被拒」")
    void disabledIsBlocker() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content(CODE),
            facts(true, "1", 3, "HEALTHY", 4, 4));
        assertFalse(result.passed());
        assertEquals(1, count(result, "CAPABILITY_DISABLED"));
        assertTrue(result.blockerMessages().get(0).contains("已停用"));
    }

    @Test
    @DisplayName("★ 缺候选 / 缺策略 → 只是提醒（D07：草稿允许先存半成品，不能被拦）")
    void missingCandidateAndPolicyAreWarningsOnly() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content(CODE),
            facts(true, "0", 0, "NONE", 0, 4));
        assertTrue(result.passed(), "缺候选/缺策略不能阻断保存");
        assertEquals(1, count(result, "CAPABILITY_NO_CANDIDATE"));
        assertEquals(1, count(result, "CAPABILITY_NO_ROUTE_POLICY"));
        // 提醒也要把严重性说清：现在正式调用一定调不通
        assertTrue(result.warnings().stream()
            .filter(item -> "CAPABILITY_NO_CANDIDATE".equals(item.getCode()))
            .findFirst().orElseThrow().getMessage().contains("一定调不通"));
        // 缺候选时不再叠"候选不健康"（没有候选谈不上健康）
        assertEquals(0, count(result, "CAPABILITY_CANDIDATE_UNHEALTHY"));
    }

    @Test
    @DisplayName("候选全不健康 / 从未探测分别提醒；策略不全带上 x/y")
    void candidateAndPolicyDetails() {
        AigStudioPrecheck.Result unhealthy = AigStudioPrecheck.of(content(CODE),
            facts(true, "0", 2, "UNHEALTHY", 4, 4));
        assertTrue(unhealthy.passed());
        assertEquals(1, count(unhealthy, "CAPABILITY_CANDIDATE_UNHEALTHY"));

        AigStudioPrecheck.Result unknown = AigStudioPrecheck.of(content(CODE),
            facts(true, "0", 2, "UNKNOWN", 4, 4));
        assertTrue(unknown.passed());
        assertEquals(1, count(unknown, "CAPABILITY_CANDIDATE_UNKNOWN"));

        AigStudioPrecheck.Result partial = AigStudioPrecheck.of(content(CODE),
            facts(true, "0", 2, "HEALTHY", 2, 4));
        assertTrue(partial.passed());
        assertEquals(1, count(partial, "CAPABILITY_POLICY_INCOMPLETE"));
        assertTrue(partial.warnings().stream()
            .filter(item -> "CAPABILITY_POLICY_INCOMPLETE".equals(item.getCode()))
            .findFirst().orElseThrow().getMessage().contains("2/4"));
    }

    @Test
    @DisplayName("★ 就绪的能力不产生任何新增问题项（不能为了「有话可说」硬凑提醒）")
    void readyCapabilityAddsNothing() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content(CODE),
            facts(true, "0", 2, "HEALTHY", 4, 4));
        assertTrue(result.passed());
        assertTrue(result.warnings().isEmpty(), "实际提醒：" + result.warnings());
    }

    @Test
    @DisplayName("没声明能力时只报 CAPABILITY_MISSING，不拿事实再报一次")
    void blankCapabilityKeepsSingleBlocker() {
        AigStudioPrecheck.Result result = AigStudioPrecheck.of(content("  "),
            AigStudioCapabilityFacts.missing(CODE));
        assertFalse(result.passed());
        assertEquals(1, count(result, "CAPABILITY_MISSING"));
        assertEquals(0, count(result, "CAPABILITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("事实快照的派生判断与实际取值一致（disabled/noCandidate/unhealthy/unknown/policy）")
    void factsDerivedFlags() {
        AigStudioCapabilityFacts ready = facts(true, "0", 1, "HEALTHY", 4, 4);
        assertFalse(ready.disabled());
        assertFalse(ready.noCandidate());
        assertFalse(ready.candidateUnhealthy());
        assertFalse(ready.candidateUnknown());
        assertFalse(ready.noPolicy());
        assertFalse(ready.policyIncomplete());

        assertTrue(facts(true, "1", 1, "HEALTHY", 4, 4).disabled());
        assertTrue(facts(true, "0", 0, "NONE", 4, 4).noCandidate());
        assertTrue(facts(true, "0", 1, "NONE", 4, 4).candidateUnknown());
        assertTrue(facts(true, "0", 1, "HEALTHY", 0, 4).noPolicy());
        assertTrue(facts(true, "0", 1, "HEALTHY", 3, 4).policyIncomplete());
        // 不存在的能力不能被认为是"已停用"（"停用"是存在但不可用，两回事）
        assertFalse(AigStudioCapabilityFacts.missing(CODE).disabled());
        // 策略总数为 0（拿不到口径）时不硬报"不全"
        assertFalse(facts(true, "0", 1, "HEALTHY", 0, 0).policyIncomplete());
    }

    @Test
    @DisplayName("解析整段内容 JSON 的入口也走同一套事实判定")
    void evaluateWithFactsUsesSameRules() {
        String json = "{\"promptSections\":{\"role\":\"角色\"},\"agentCategory\":\"PLANNING\","
            + "\"providerCapability\":\"" + CODE + "\",\"allowExternal\":\"N\"}";
        AigStudioPrecheck.Result result = AigStudioPrecheck.evaluate(JSON, json,
            facts(true, "0", 0, "NONE", 0, 4));
        // 结构类阻断照旧生效（同一套判据），能力问题以提醒并列出现
        assertEquals(1, count(result, "CAPABILITY_NO_CANDIDATE"));
        assertEquals(1, count(result, "CAPABILITY_NO_ROUTE_POLICY"));
        assertEquals(1, count(result, "SECTION_MISSING"));
        assertFalse(result.passed());
        List<String> codes = result.blockers().stream().map(item -> item.getCode()).toList();
        assertTrue(codes.contains("SECTION_MISSING"));
    }
}
