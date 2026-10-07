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
 * 视觉 DNA 评测执行器测试（设计 §13.2）。
 *
 * <p>用随平台发布的快照图（{@code creative/eval/ref-red-block-200.png}，白底 + 正中纯红方块）——
 * 这是「已知答案」的图：量出来的背景、主色、留白、占比都有唯一正确的值，因此判据可以写死。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeVisualDnaEvaluationSubjectTest {

    private static final String REF = "classpath:creative/eval/ref-red-block-200.png";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreativeVisualDnaEvaluationSubject subject;

    @BeforeEach
    void setUp() {
        subject = new CreativeVisualDnaEvaluationSubject();
    }

    /**
     * 造一条评测入参。
     *
     * @param ref 快照引用
     * @return 入参
     */
    private static AigEvaluationRequest request(String ref) {
        return new AigEvaluationRequest(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            CreativeVisualDnaEvaluationSubject.SUBJECT_CODE, 9102L, "0.1.0", "case-visual-dna-solid-bg",
            "VISUAL_DNA", null, ref);
    }

    /**
     * 执行并解析产出。
     *
     * @param ref 快照引用
     * @return 产出
     */
    private static JsonNode run(String ref) throws Exception {
        AigEvaluationOutcome outcome = new CreativeVisualDnaEvaluationSubject().execute(request(ref));
        return MAPPER.readTree(outcome.outputJson());
    }

    @Test
    @DisplayName("只负责视觉 DNA：别的对象不认领")
    void supportsOnlyVisualDna() {
        assertTrue(subject.supports("AGENT_VERSION", "creative_visual_dna"));
        assertFalse(subject.supports("AGENT_VERSION", "creative_planning"));
        assertFalse(subject.supports("SKILL_VERSION", "creative_visual_dna"));
        assertTrue(subject.describe().contains("模型分析部分不在本用例范围"),
            "自述必须写清本执行器只覆盖确定性那段：" + subject.describe());
    }

    @Test
    @DisplayName("已知答案的快照图：背景/主色/留白/场景/占比都要量对，且明确标注覆盖范围")
    void analyzesCommittedSnapshot() throws Exception {
        AigEvaluationOutcome outcome = subject.execute(request(
            "inline:{\"reference\":\"" + REF + "\"}"));

        assertEquals(BigDecimal.ZERO, outcome.costAmount(), "本地实测不调模型");
        assertTrue(outcome.costKnown());
        assertFalse(outcome.externalCall());

        JsonNode out = MAPPER.readTree(outcome.outputJson());
        assertEquals(200, out.get("width").asInt());
        assertEquals(200, out.get("height").asInt());
        // 判据按点号路径取值，因此 values 必须是**嵌套**的（values.colors.primary）
        assertEquals("#FF0000", out.get("values").get("colors").get("primary").asText(), out.toString());
        assertTrue(out.get("values").get("colors").get("background").asText().startsWith("#F"),
            out.toString());
        assertEquals("HIGH", out.get("values").get("saturation").asText(), out.toString());
        assertEquals("纯色底", out.get("values").get("sceneType").asText(), out.toString());
        double observedRatio = out.get("observed_ratio").asDouble();
        assertTrue(observedRatio > 12 && observedRatio < 20, "实测占比应接近 16%，实际 " + observedRatio);
        assertTrue(out.get("recommendation_count").asInt() >= 3, out.toString());
        assertTrue(out.get("low_reliability_fields").isArray(), "低可信字段要以清单形式暴露");

        // 覆盖范围必须明说，免得读者以为这一条用例覆盖了整个 Agent
        assertTrue(out.get("deterministic_only").asBoolean());
        assertFalse(out.get("model_part_evaluated").asBoolean());
    }

    @Test
    @DisplayName("与用例种子同一条判据在真实产出上必须通过（路径语法按点号展开）")
    void seedJudgementPassesOnRealOutput() {
        AigEvaluationOutcome outcome = subject.execute(request("inline:{\"reference\":\"" + REF + "\"}"));
        AigEvaluationCase evaluationCase = new AigEvaluationCase();
        evaluationCase.setCaseCode("case-visual-dna-solid-bg");
        // 与 script/sql/aig_evaluation_seed.sql 里 case-visual-dna-solid-bg 的 expected_json 保持一致
        evaluationCase.setExpectedJson("{\"required_paths\":[\"values\",\"recommendations\",\"width\","
            + "\"height\",\"observed_ratio\",\"recommendation_count\"],\"equals\":{\"width\":200,"
            + "\"height\":200,\"values.colors.primary\":\"#FF0000\",\"values.saturation\":\"HIGH\","
            + "\"values.sceneType\":\"纯色底\",\"deterministic_only\":true,"
            + "\"model_part_evaluated\":false},\"min_items\":{\"recommendations\":3}}");
        evaluationCase.setCostMin(BigDecimal.ZERO);
        evaluationCase.setCostMax(BigDecimal.ZERO);

        AigExpectedRuleCheck check = new AigExpectedRuleChecker(JsonMapper.builder().build())
            .check(evaluationCase, outcome);

        assertTrue(check.passed(), "种子的判据必须能在真实产出上通过：" + check.failures());
    }

    @Test
    @DisplayName("确定性：同一张快照图两次执行产出逐字相同")
    void sameSnapshotSameOutput() {
        String ref = "inline:{\"reference\":\"" + REF + "\"}";
        assertEquals(subject.execute(request(ref)).outputJson(),
            subject.execute(request(ref)).outputJson());
    }

    @Test
    @DisplayName("快照校验：缺 reference / 前缀不认 / 资源不存在 / 越出允许目录，都报错")
    void snapshotValidation() {
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{}"))).getMessage().contains("缺少 reference"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{\"reference\":\"oss://bucket/a.png\"}")))
            .getMessage().contains("不支持的图片快照前缀"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{\"reference\":\"classpath:creative/eval/nope.png\"}")))
            .getMessage().contains("资源不存在"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{\"reference\":\"classpath:application.yml\"}")))
            .getMessage().contains("只允许 creative/eval/"), "用例不能读任意 classpath 资源");
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:{\"reference\":\"classpath:creative/eval/../secret\"}")))
            .getMessage().contains("只允许 creative/eval/"));
        assertTrue(assertThrows(ServiceException.class,
            () -> subject.execute(request("inline:not-json")))
            .getMessage().contains("不是合法 JSON"));
    }

}
