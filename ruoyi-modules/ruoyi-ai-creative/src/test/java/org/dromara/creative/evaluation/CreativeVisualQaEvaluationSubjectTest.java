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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视觉 QA 评测执行器测试（设计 §13.2）。
 *
 * <p>要钉住的三条：</p>
 * <ol>
 *     <li><b>阈值到了就翻脸</b>：合格图判过、长方形图判不过（判据真的在看这张图）；</li>
 *     <li><b>规则来自用例</b>：快照里没写 rules 直接报错——「用例忘了写规则」与「这张图没过」
 *         是两件事，混成一条 FAIL 会让人去改图；</li>
 *     <li><b>没配规则不是通过</b>：checker 在 NONE 规则下判 NOT_CONFIGURED，本执行器更前一步拦住。</li>
 * </ol>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeVisualQaEvaluationSubjectTest {

    private static final String CLEAN = "creative/eval/qa-clean-800.png";

    private static final String NOT_SQUARE = "creative/eval/qa-not-square-800x600.png";

    /**
     * 与 CreativeImageRuleCheckerTest 的 MAIN_RULES 同口径（方形 / 最小边 800 / 禁 alpha /
     * 白底 0.90 / 主体占比 0.10 / 贴边 0.05）
     */
    private static final String RULES =
        "{\"schema\":\"screen-qa/1\",\"square\":true,\"minSide\":800,\"alphaForbidden\":true,"
            + "\"whiteBackground\":{\"enabled\":true,\"minEdgeWhiteness\":0.90},"
            + "\"subjectRatio\":{\"enabled\":true,\"min\":0.10},"
            + "\"edgeBleed\":{\"enabled\":true,\"maxRatio\":0.05}}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeVisualQaEvaluationSubject subject;

    @BeforeEach
    void setUp() {
        subject = new CreativeVisualQaEvaluationSubject();
    }

    /**
     * 造一条评测入参。
     *
     * @param artifact 产物快照路径
     * @param rules    规则 JSON（null = 不写 rules）
     * @return 入参
     */
    private static AigEvaluationRequest request(String artifact, String rules) {
        String body = rules == null
            ? "{\"artifact\":\"classpath:" + artifact + "\"}"
            : "{\"artifact\":\"classpath:" + artifact + "\",\"rules\":" + rules + "}";
        return new AigEvaluationRequest(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            CreativeVisualQaEvaluationSubject.SUBJECT_CODE, 9104L, "0.1.0", "case-visual-qa",
            "IMAGE_QA", null, "inline:" + body);
    }

    /**
     * 执行并解析产出。
     *
     * @param artifact 产物快照路径
     * @param rules    规则 JSON
     * @return 产出
     */
    private static JsonNode run(String artifact, String rules) throws Exception {
        AigEvaluationOutcome outcome =
            new CreativeVisualQaEvaluationSubject().execute(request(artifact, rules));
        return MAPPER.readTree(outcome.outputJson());
    }

    @Test
    @DisplayName("只负责视觉 QA：别的对象不认领")
    void supportsOnlyVisualQa() {
        assertTrue(subject.supports("AGENT_VERSION", "creative_visual_qa"));
        assertFalse(subject.supports("AGENT_VERSION", "creative_visual_dna"));
        assertFalse(subject.supports("PACKAGE_VERSION", "creative_visual_qa"));
    }

    @Test
    @DisplayName("合格交付图：判过，度量值可核对，且规则来源标明是「用例快照」")
    void cleanArtifactPasses() throws Exception {
        AigEvaluationOutcome outcome =
            new CreativeVisualQaEvaluationSubject().execute(request(CLEAN, RULES));

        assertEquals(BigDecimal.ZERO, outcome.costAmount(), "本地体检不调模型");
        assertFalse(outcome.externalCall());

        JsonNode out = MAPPER.readTree(outcome.outputJson());
        assertEquals("PASS", out.get("verdict").asText(), out.toString());
        assertTrue(out.get("passed").asBoolean(), out.toString());
        assertTrue(out.get("configured").asBoolean());
        assertEquals(800, out.get("metrics").get("width").asInt());
        assertEquals(800, out.get("metrics").get("height").asInt());
        assertEquals(0, out.get("failed_count").asInt(), out.toString());
        assertTrue(out.get("finding_count").asInt() >= 1, "配了规则就该产出检查项");
        assertEquals("case_snapshot", out.get("rules_source").asText(),
            "规则来自用例，评审要能看出这次按什么规则判的");
        assertEquals("screen-qa/1", out.get("rules_schema").asText());
    }

    @Test
    @DisplayName("长方形图：判不过，且未过项里出现 CANVAS_SQUARE（阈值到了就翻脸）")
    void notSquareArtifactFails() throws Exception {
        JsonNode out = run(NOT_SQUARE, RULES);

        assertFalse(out.get("passed").asBoolean(), out.toString());
        assertTrue(out.get("configured").asBoolean());
        assertTrue(out.get("failed_count").asInt() >= 1, out.toString());
        assertTrue(out.toString().contains("CANVAS_SQUARE"), out.toString());
        assertTrue(out.get("failed_codes").isArray());
        assertTrue(out.get("verdict").asText().contains("FAIL"), out.toString());
    }

    @Test
    @DisplayName("确定性：同一张图两次执行产出逐字相同")
    void sameArtifactSameOutput() {
        assertEquals(subject.execute(request(CLEAN, RULES)).outputJson(),
            subject.execute(request(CLEAN, RULES)).outputJson());
    }

    @Test
    @DisplayName("快照里没写 rules → 报错，而不是「这张图没过」")
    void missingRulesIsAnError() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> subject.execute(request(CLEAN, null)));
        assertTrue(error.getMessage().contains("缺少 rules"), error.getMessage());
        assertTrue(error.getMessage().contains("不能当成通过"), error.getMessage());
    }

    @Test
    @DisplayName("与用例种子同一条判据在真实产出上必须通过（合格图与长方形图各一条）")
    void seedJudgementsPassOnRealOutput() {
        AigExpectedRuleChecker checker = new AigExpectedRuleChecker(JsonMapper.builder().build());

        // 与 script/sql/aig_evaluation_seed.sql 里两条 QA 用例的 expected_json 保持一致
        AigEvaluationCase cleanCase = new AigEvaluationCase();
        cleanCase.setCaseCode("case-visual-qa-clean-800");
        cleanCase.setExpectedJson("{\"required_paths\":[\"metrics\",\"findings\",\"verdict\","
            + "\"failed_codes\"],\"equals\":{\"verdict\":\"PASS\",\"passed\":true,"
            + "\"configured\":true,\"metrics.width\":800,\"metrics.height\":800},"
            + "\"min_items\":{\"findings\":1}}");
        cleanCase.setCostMin(BigDecimal.ZERO);
        cleanCase.setCostMax(BigDecimal.ZERO);
        AigExpectedRuleCheck cleanCheck = checker.check(cleanCase,
            new CreativeVisualQaEvaluationSubject().execute(request(CLEAN, RULES)));
        assertTrue(cleanCheck.passed(), "合格图的判据必须过：" + cleanCheck.failures());

        AigEvaluationCase notSquareCase = new AigEvaluationCase();
        notSquareCase.setCaseCode("case-visual-qa-not-square");
        notSquareCase.setExpectedJson("{\"required_paths\":[\"metrics\",\"findings\",\"verdict\","
            + "\"failed_codes\"],\"equals\":{\"passed\":false,\"configured\":true},"
            + "\"min_items\":{\"findings\":1},\"must_contain\":[\"CANVAS_SQUARE\"]}");
        notSquareCase.setCostMin(BigDecimal.ZERO);
        notSquareCase.setCostMax(BigDecimal.ZERO);
        AigExpectedRuleCheck notSquareCheck = checker.check(notSquareCase,
            new CreativeVisualQaEvaluationSubject().execute(request(NOT_SQUARE, RULES)));
        assertTrue(notSquareCheck.passed(), "长方形图的判据（期望「判不过」）必须过："
            + notSquareCheck.failures());
    }

    @Test
    @DisplayName("快照校验：缺 artifact / rules 不是对象 / 图片资源不存在，都报错")
    void snapshotValidation() {
        AigEvaluationRequest noArtifact = new AigEvaluationRequest(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), CreativeVisualQaEvaluationSubject.SUBJECT_CODE,
            9104L, "0.1.0", "case-visual-qa", "IMAGE_QA", null,
            "inline:{\"rules\":" + RULES + "}");
        assertTrue(assertThrows(ServiceException.class, () -> subject.execute(noArtifact))
            .getMessage().contains("缺少 artifact"));

        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request(CLEAN, "\"just-a-string\"")))
            .getMessage().contains("缺少 rules"));

        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("creative/eval/nope.png", RULES)))
            .getMessage().contains("资源不存在"));
    }

}
