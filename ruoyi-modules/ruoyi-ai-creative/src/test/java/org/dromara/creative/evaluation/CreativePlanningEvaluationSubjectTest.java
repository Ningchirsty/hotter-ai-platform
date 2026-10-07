package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策划 Agent 评测执行器测试（设计 §13.2）。
 *
 * <p>这里验证的是「执行器 + 真实策划引擎」这一整段，而不是 mock：</p>
 * <ol>
 *     <li>确定性：同一快照两次执行<b>逐字相同</b>（执行器自报的 reproducible_probe 在这里被独立验一遍，
 *         因为一个说谎的执行器也能自报 true）；</li>
 *     <li>零成本：不调模型，成本如实上报为「可知且为 0」——用例声明 cost_max=0 时，
 *         一旦有人给确定性基座接上模型，评测就会失败；</li>
 *     <li>快照前缀严格：只认 {@code inline:}，认不出的前缀报错而不是猜一个默认输入。</li>
 * </ol>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativePlanningEvaluationSubjectTest {

    private static final String PRODUCT = "鸢尾花香水";

    private static final String DNA_FULL = "{\"styleKeywords\":[\"极简\",\"自然\"],"
        + "\"colors\":{\"background\":\"#F5F5F3\",\"primary\":\"#2E6B4F\"},"
        + "\"lighting\":{\"type\":\"SOFT\",\"direction\":\"FRONT\"},"
        + "\"productRatio\":{\"min\":15,\"max\":30},"
        + "\"saturation\":\"LOW\",\"contrastLevel\":\"MEDIUM\",\"whitespaceLevel\":\"HIGH\"}";

    /**
     * 基因合法（可锁定）但没有三个档位——与 aig_evaluation_seed.sql 里
     * case-plan-blank-levels 的快照一致：判据同时要求 dna_valid=true 与文案含「未设置」
     */
    private static final String DNA_WITHOUT_LEVELS = "{\"styleKeywords\":[\"极简\",\"自然\"],"
        + "\"colors\":{\"background\":\"#F5F5F3\",\"primary\":\"#2E6B4F\"},"
        + "\"lighting\":{\"type\":\"SOFT\",\"direction\":\"FRONT\"},"
        + "\"productRatio\":{\"min\":15,\"max\":30}}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativePlanningEvaluationSubject subject;

    @BeforeEach
    void setUp() {
        subject = new CreativePlanningEvaluationSubject();
    }

    /**
     * 造一个内联快照引用。
     *
     * @param dna  DNA JSON
     * @param seed 差异种子
     * @return 快照引用
     */
    private static String snapshot(String dna, long seed) {
        return "inline:{\"product_name\":\"" + PRODUCT + "\",\"variant_seed\":" + seed
            + ",\"facts\":{\"product_name\":\"" + PRODUCT + "\",\"color\":\"蓝紫渐变\","
            + "\"occasion\":\"通勤\"},\"dna\":" + dna + "}";
    }

    /**
     * 造一条评测入参。
     *
     * @param ref 快照引用
     * @return 入参
     */
    private static AigEvaluationRequest request(String ref) {
        return new AigEvaluationRequest(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            CreativePlanningEvaluationSubject.SUBJECT_CODE, 9101L, "1.0.0", "case-plan-deterministic",
            "PLAN", null, ref);
    }

    /**
     * 执行并解析产出。
     *
     * @param ref 快照引用
     * @return 产出 JSON 节点
     */
    private static JsonNode run(String ref) throws Exception {
        AigEvaluationOutcome outcome =
            new CreativePlanningEvaluationSubject().execute(request(ref));
        return MAPPER.readTree(outcome.outputJson());
    }

    @Test
    @DisplayName("只负责策划 Agent：别人的对象不认领（派发靠它，认错对象等于结论来自别的实现）")
    void supportsOnlyPlanningAgent() {
        assertTrue(subject.supports(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            CreativePlanningEvaluationSubject.SUBJECT_CODE));
        assertTrue(subject.supports("agent_version", "creative_planning"), "编码大小写不敏感");
        assertFalse(subject.supports(AigReleaseTargetTypeEnum.SKILL_VERSION.getCode(),
            CreativePlanningEvaluationSubject.SUBJECT_CODE));
        assertFalse(subject.supports(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            "creative_visual_dna"));
        assertTrue(subject.describe().contains("creative_planning"), subject.describe());
    }

    @Test
    @DisplayName("产出结构：3 条方向 + 分镜 + 基因自洽 + 文案不含档位枚举 + 零成本且非外呼")
    void producesStructuredDrafts() throws Exception {
        AigEvaluationOutcome outcome = subject.execute(request(snapshot(DNA_FULL, 0)));
        JsonNode out = MAPPER.readTree(outcome.outputJson());

        assertEquals(CreativePlanningEvaluationSubject.SUBJECT_CODE, out.get("subject").asText());
        assertEquals(3, out.get("direction_count").asInt(), out.toString());
        assertTrue(out.get("screen_count").asInt() >= 1, out.toString());
        assertEquals(3, out.get("directions").size());
        assertTrue(out.get("dna_valid").asBoolean(), "dna_issues=" + out.get("dna_issues"));
        assertTrue(out.get("no_enum_leak").asBoolean(),
            "文案里出现了档位枚举：" + out.get("level_enums_present"));
        assertTrue(out.get("reproducible_probe").asBoolean());
        assertTrue(out.get("drafts_mention_product").asBoolean(), "文案里应出现产品名");

        assertTrue(outcome.costKnown(), "确定性引擎的成本是「可知且为 0」，不是「算不出来」");
        assertEquals(BigDecimal.ZERO, outcome.costAmount());
        assertFalse(outcome.externalCall(), "确定性引擎不调模型，也就没有外呼");
        assertTrue(outcome.latencyMs() >= 0L);
    }

    @Test
    @DisplayName("确定性：同一快照两次执行逐字相同（这是平台侧对 reproducible 声明的独立验证）")
    void sameInputSameOutputByteForByte() {
        String first = subject.execute(request(snapshot(DNA_FULL, 7))).outputJson();
        String second = subject.execute(request(snapshot(DNA_FULL, 7))).outputJson();

        assertEquals(first, second, "同一个种子必须逐字相同，否则「可复现」是假的");
    }

    @Test
    @DisplayName("差异化：换了种子方向就变（否则「重新生成」看到的还是那三句话）")
    void differentSeedYieldsDifferentDrafts() throws Exception {
        JsonNode seedOne = run(snapshot(DNA_FULL, 1));
        JsonNode seedTwo = run(snapshot(DNA_FULL, 2));

        assertNotEquals(seedOne.get("directions").toString(), seedTwo.get("directions").toString(),
            "不同种子必须产出不同拍法");
        assertEquals(3, seedOne.get("direction_count").asInt());
        assertEquals(3, seedTwo.get("direction_count").asInt(),
            "种子只改「怎么拍」，不改方向的条数");
    }

    @Test
    @DisplayName("快照前缀严格：只认 inline:，认不出的前缀报错而不是猜一个默认输入")
    void snapshotPrefixIsStrict() {
        ServiceException oss = assertThrows(ServiceException.class,
            () -> subject.execute(request("oss://bucket/snapshot.json")));
        assertTrue(oss.getMessage().contains("只支持 inline:"), oss.getMessage());

        ServiceException unknown = assertThrows(ServiceException.class,
            () -> subject.execute(request("{\"product_name\":\"x\"}")));
        assertTrue(unknown.getMessage().contains("没有前缀"), unknown.getMessage());

        ServiceException blank = assertThrows(ServiceException.class,
            () -> subject.execute(request("   ")));
        assertTrue(blank.getMessage().contains("没有输入快照"), blank.getMessage());
    }

    @Test
    @DisplayName("快照校验：非法 JSON / 缺产品名 / facts 非文本 / 种子非数字 / dna 非对象 都报错")
    void snapshotValidation() {
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{oops"))).getMessage().contains("不是合法 JSON"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{\"variant_seed\":1}")))
            .getMessage().contains("缺少 product_name"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"facts\":{\"n\":1}}"))).getMessage()
            .contains("必须是文本"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"variant_seed\":\"abc\"}")))
            .getMessage().contains("variant_seed"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"dna\":[1,2]}")))
            .getMessage().contains("dna 必须是对象"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:[1,2]"))).getMessage().contains("必须是 JSON 对象"));
    }

    @Test
    @DisplayName("缺档位的基因：文案说「未设置」，不默认成「中」；基因本身仍是可锁定的")
    void blankLevelsAreSaidAsUnset() throws Exception {
        JsonNode out = run(snapshot(DNA_WITHOUT_LEVELS, 0));

        assertTrue(out.get("dna_valid").asBoolean(), "dna_issues=" + out.get("dna_issues"));
        assertTrue(out.toString().contains("未设置"),
            "缺档位必须如实说「未设置」，不能默认成某个档：" + out);
        assertTrue(out.get("no_enum_leak").asBoolean());
        assertEquals(3, out.get("direction_count").asInt());
    }

    // ---------------------------------------------------------------- 品牌要求进分镜

    /**
     * 品牌要求片段（与 aig_evaluation_seed.sql 里 case-plan-brand-brief 的快照逐字一致）
     */
    private static final String BRAND_BRIEF = "\"brand_brief\":{\"must_show_first_line\":\"容量 50ml\","
        + "\"selling_points\":[{\"title\":\"手工缠花\",\"content\":\"手工缠花工艺\"},"
        + "{\"title\":\"蓝紫渐变\",\"content\":\"蓝紫渐变釉色\"}]}";

    /**
     * 带品牌要求的快照引用。
     *
     * @param dna  DNA JSON
     * @param seed 差异种子
     * @return 快照引用
     */
    private static String snapshotWithBrandBrief(String dna, long seed) {
        return "inline:{\"product_name\":\"" + PRODUCT + "\",\"variant_seed\":" + seed
            + ",\"facts\":{\"product_name\":\"" + PRODUCT + "\",\"color\":\"蓝紫渐变\"},"
            + "\"dna\":" + dna + "," + BRAND_BRIEF + "}";
    }

    @Test
    @DisplayName("★ 品牌要求真的落进分镜：必显信息进末屏、两条卖点各进一个卖点屏")
    void brandBriefLandsInScreens() throws Exception {
        JsonNode out = run(snapshotWithBrandBrief(DNA_FULL, 0));

        assertTrue(out.get("brand_brief_used").asBoolean(), out.toString());
        assertEquals("容量 50ml", out.get("must_show_first_line").asText());
        assertTrue(out.get("must_show_in_closing_screen").asBoolean(),
            "必显信息必须进品牌收尾屏（末屏）：" + out);
        assertEquals(2, out.get("selling_point_count").asInt());
        assertEquals(2, out.get("selling_points_landed").asInt(),
            "两条卖点都要落进卖点屏：" + out.get("screens"));
        assertEquals(2, out.get("selling_point_screens").asInt(), "默认骨架有两个卖点屏");

        JsonNode closing = out.get("screens").get(out.get("screens").size() - 1);
        assertTrue(closing.toString().contains("容量 50ml"),
            "末屏（品牌收尾）里应能看到必显信息：" + closing);
    }

    @Test
    @DisplayName("没填品牌要求时：三个新字段是「未使用」，分镜与从前一致（卖点屏仍在，只是没内容可填）")
    void withoutBrandBriefNewFieldsAreInert() throws Exception {
        JsonNode out = run(snapshot(DNA_FULL, 0));

        assertFalse(out.get("brand_brief_used").asBoolean());
        assertTrue(out.get("must_show_first_line").isNull(), out.toString());
        assertFalse(out.get("must_show_in_closing_screen").asBoolean());
        assertEquals(0, out.get("selling_point_count").asInt());
        assertEquals(0, out.get("selling_points_landed").asInt());
        assertEquals(2, out.get("selling_point_screens").asInt());
        // 「没填」不能变成「分镜变空」——这正是 5 参重载里 null/空清单保持原行为的用意
        assertTrue(out.get("screen_count").asInt() >= 7, out.toString());
    }

    @Test
    @DisplayName("品牌要求结构不合法：逐项报错，不猜也不静默忽略")
    void brandBriefValidation() {
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"brand_brief\":[1]}")))
            .getMessage().contains("brand_brief 必须是对象"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"brand_brief\":{\"must_show_first_line\":1}}")))
            .getMessage().contains("must_show_first_line 必须是文本"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"brand_brief\":{\"selling_points\":{}}}")))
            .getMessage().contains("selling_points 必须是数组"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"brand_brief\":{\"selling_points\":[\"文字\"]}}")))
            .getMessage().contains("每一项必须是对象"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "inline:{\"product_name\":\"x\",\"brand_brief\":{\"selling_points\":[{\"title\":1}]}}")))
            .getMessage().contains("title 必须是文本"));
    }

    @Test
    @DisplayName("带品牌要求也保持确定性：同快照两次逐字相同")
    void brandBriefStaysDeterministic() {
        String first = subject.execute(request(snapshotWithBrandBrief(DNA_FULL, 3))).outputJson();
        String second = subject.execute(request(snapshotWithBrandBrief(DNA_FULL, 3))).outputJson();
        assertEquals(first, second);
    }

}
