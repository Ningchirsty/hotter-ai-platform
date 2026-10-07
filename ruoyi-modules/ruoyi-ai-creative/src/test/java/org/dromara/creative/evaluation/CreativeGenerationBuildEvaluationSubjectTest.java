package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleCheck;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleChecker;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成任务构建评测执行器测试（设计 §13.2）。
 *
 * <p>这个执行器只覆盖「由 DNA 派生提示词/草案」那一段（实际提交花钱且结果非确定，
 * 归沙箱试跑与连通性测试）。因此测试要钉的是：</p>
 * <ol>
 *     <li>提示词真的由基因派生（主色、屏文案都进了提示词）；</li>
 *     <li>没有基因时走内置兜底风格（不会产出半句话提示词）；</li>
 *     <li>同种子逐字可复现、换种子措辞变（裁定 ⑤「可复现但每次要有差异化」）；</li>
 *     <li>产出里显式声明「没提交」「没调模型」——覆盖范围不能被读者猜。</li>
 * </ol>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeGenerationBuildEvaluationSubjectTest {

    private static final String DNA = "{\"styleKeywords\":[\"极简\",\"自然\"],"
        + "\"colors\":{\"background\":\"#F5F5F3\",\"primary\":\"#2E6B4F\",\"secondary\":\"#C9D8CE\"},"
        + "\"lighting\":{\"type\":\"SOFT\",\"direction\":\"FRONT\"},"
        + "\"productRatio\":{\"min\":15,\"max\":30},\"saturation\":\"LOW\","
        + "\"contrastLevel\":\"MEDIUM\",\"whitespaceLevel\":\"HIGH\"}";

    private static final String SCREEN_TEXT = "画面独白：鸢尾花香水静置在木桌上，晨光斜切";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeGenerationBuildEvaluationSubject subject;

    @BeforeEach
    void setUp() {
        subject = new CreativeGenerationBuildEvaluationSubject(new DnaPromptBuilder());
    }

    /**
     * 造一条评测入参。
     *
     * @param body 内联 JSON（不含 inline: 前缀）
     * @return 入参
     */
    private static AigEvaluationRequest request(String body) {
        return new AigEvaluationRequest(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            CreativeGenerationBuildEvaluationSubject.SUBJECT_CODE, 9103L, "0.1.0",
            "case-generation-build", "GENERATION", null, "inline:" + body);
    }

    /**
     * 执行并解析产出。
     *
     * @param body 内联 JSON
     * @return 产出
     */
    private static JsonNode run(String body) throws Exception {
        AigEvaluationOutcome outcome = new CreativeGenerationBuildEvaluationSubject(
            new DnaPromptBuilder()).execute(request(body));
        return MAPPER.readTree(outcome.outputJson());
    }

    @Test
    @DisplayName("只负责生成任务构建：别的对象不认领，且自述写清覆盖范围")
    void supportsOnlyGenerationBuild() {
        assertTrue(subject.supports("AGENT_VERSION", "creative_generation_build"));
        assertFalse(subject.supports("AGENT_VERSION", "creative_planning"));
        assertTrue(subject.describe().contains("实际提交不在本用例范围"), subject.describe());
    }

    @Test
    @DisplayName("提示词由基因派生：主色与屏文案都进提示词，且显式声明没提交、没调模型")
    void derivesPromptFromDna() throws Exception {
        AigEvaluationOutcome outcome = subject.execute(request("{\"subject\":\"鸢尾花香水\","
            + "\"screen_hint\":\"HERO 主图\",\"screen_text\":\"" + SCREEN_TEXT
            + "\",\"variant_seed\":0,\"dna\":" + DNA + "}"));

        assertEquals(BigDecimal.ZERO, outcome.costAmount(), "派生提示词不调模型");
        assertTrue(outcome.costKnown());
        assertFalse(outcome.externalCall());

        JsonNode out = MAPPER.readTree(outcome.outputJson());
        String prompt = out.get("prompt").asText();
        assertFalse(prompt.isBlank(), out.toString());
        assertTrue(out.get("has_negative_prompt").asBoolean(), out.toString());
        assertTrue(out.get("applied_count").asInt() >= 1, out.toString());
        assertTrue(prompt.contains("#2E6B4F"), "主色应进提示词：" + prompt);
        assertTrue(prompt.contains("鸢尾花香水"), "主体应进提示词：" + prompt);
        assertTrue(prompt.contains("画面独白"), "屏文案应进提示词：" + prompt);
        assertTrue(out.get("reproducible_probe").asBoolean());

        assertTrue(out.get("deterministic_only").asBoolean());
        assertFalse(out.get("model_part_evaluated").asBoolean());
        assertTrue(out.get("submission_not_performed").asBoolean(),
            "覆盖范围必须明说：本条用例没有提交任何出图任务");
    }

    @Test
    @DisplayName("没有基因时走内置兜底风格，不会产出半句话提示词")
    void missingDnaUsesFallbackStyle() throws Exception {
        JsonNode out = run("{\"subject\":\"鸢尾花香水\",\"screen_hint\":\"HERO 主图\"}");

        assertFalse(out.get("dna_provided").asBoolean());
        assertTrue(out.get("prompt").asText().contains("现代简约"),
            "基因缺失时应走内置兜底风格：" + out.get("prompt").asText());
        assertTrue(out.get("has_negative_prompt").asBoolean(), out.toString());
    }

    @Test
    @DisplayName("确定性：同种子逐字相同；换种子措辞变（裁定⑤「可复现但每次有差异化」）")
    void seedControlsWording() throws Exception {
        String base = "{\"subject\":\"鸢尾花香水\",\"screen_hint\":\"HERO 主图\",\"screen_text\":\""
            + SCREEN_TEXT + "\",\"dna\":" + DNA + ",\"variant_seed\":";
        String seedZero = subject.execute(request(base + "0}")).outputJson();
        String seedZeroAgain = subject.execute(request(base + "0}")).outputJson();
        String seedOne = subject.execute(request(base + "1}")).outputJson();

        assertEquals(seedZero, seedZeroAgain, "同种子必须逐字相同");
        assertNotEquals(MAPPER.readTree(seedZero).get("prompt").asText(),
            MAPPER.readTree(seedOne).get("prompt").asText(),
            "换种子措辞必须变，否则「重新生成」看到的还是同一句话");
    }

    @Test
    @DisplayName("与用例种子同一条判据在真实产出上必须通过（有基因 / 无基因兜底各一条）")
    void seedJudgementsPassOnRealOutput() {
        AigExpectedRuleChecker checker = new AigExpectedRuleChecker(JsonMapper.builder().build());
        String withDna = "{\"subject\":\"鸢尾花香水\",\"screen_hint\":\"HERO 主图\",\"screen_text\":\""
            + SCREEN_TEXT + "\",\"variant_seed\":0,\"dna\":" + DNA + "}";

        // 与 script/sql/aig_evaluation_seed.sql 里两条用例的 expected_json 保持一致
        AigEvaluationCase dnaCase = new AigEvaluationCase();
        dnaCase.setCaseCode("case-generation-build-dna");
        dnaCase.setExpectedJson("{\"required_paths\":[\"prompt\",\"negative_prompt\",\"applied\","
            + "\"prompt_length\",\"has_negative_prompt\"],\"equals\":{\"has_negative_prompt\":true,"
            + "\"dna_provided\":true,\"submission_not_performed\":true,\"deterministic_only\":true,"
            + "\"model_part_evaluated\":false,\"reproducible_probe\":true},"
            + "\"min_items\":{\"applied\":3},\"must_contain\":[\"#2E6B4F\",\"画面独白\"]}");
        dnaCase.setCostMin(BigDecimal.ZERO);
        dnaCase.setCostMax(BigDecimal.ZERO);
        AigExpectedRuleCheck dnaCheck = checker.check(dnaCase, subject.execute(request(withDna)));
        assertTrue(dnaCheck.passed(), "有基因的判据必须过：" + dnaCheck.failures());

        AigEvaluationCase fallbackCase = new AigEvaluationCase();
        fallbackCase.setCaseCode("case-generation-build-fallback-dna");
        fallbackCase.setExpectedJson("{\"required_paths\":[\"prompt\",\"negative_prompt\","
            + "\"applied\",\"has_negative_prompt\"],\"equals\":{\"dna_provided\":false,"
            + "\"has_negative_prompt\":true,\"submission_not_performed\":true,"
            + "\"reproducible_probe\":true},\"min_items\":{\"applied\":1},"
            + "\"must_contain\":[\"现代简约\"]}");
        fallbackCase.setCostMin(BigDecimal.ZERO);
        fallbackCase.setCostMax(BigDecimal.ZERO);
        AigExpectedRuleCheck fallbackCheck = checker.check(fallbackCase,
            subject.execute(request("{\"subject\":\"鸢尾花香水\",\"screen_hint\":\"HERO 主图\"}")));
        assertTrue(fallbackCheck.passed(), "无基因兜底的判据必须过：" + fallbackCheck.failures());
    }

    @Test
    @DisplayName("快照校验：缺 subject / dna 非对象 / 种子非数字 都报错")
    void snapshotValidation() {
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request("{}")))
            .getMessage().contains("缺少 subject"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "{\"subject\":\"x\",\"dna\":[1,2]}")))
            .getMessage().contains("dna 必须是对象"));
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(request(
            "{\"subject\":\"x\",\"variant_seed\":\"abc\"}")))
            .getMessage().contains("variant_seed"));
    }

}
