package org.dromara.creative.evaluation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.bo.AigEvaluationRunBo;
import org.dromara.aigov.agent.enums.AigEvaluationStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationSubjectRegistry;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleChecker;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationCaseMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationRunMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.impl.AigEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 评测链路端到端测试：治理层的评测服务 + 真实的策划引擎（设计 §13.2）。
 *
 * <p>这一条把两侧接起来验证：{@code IAigEvaluationService#runEvaluation} 派发到
 * {@link CreativePlanningEvaluationSubject}，执行器调用真实的 {@code CreativeDraftFactory}，
 * 判据（真实的 {@code AigExpectedRuleChecker}）在真实产出上求值，最后
 * {@code goldenCaseEvidence} 给出的结论与运行账本一致。</p>
 *
 * <p>只有 Mapper 是 mock（不碰数据库）——被测对象、判据、派发、证据判定都是真实现。
 * 真库那一段由 {@code EvaluationCheck} 探针负责。</p>
 *
 * @author creative
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class CreativeEvaluationPipelineTest {

    private static final long VERSION_ID = 9101L;

    private static final long AGENT_ID = 9201L;

    private static final long CASE_ID = 9301L;

    private static final String CASE_CODE = "case-plan-deterministic";

    private static final String SNAPSHOT = "inline:{\"product_name\":\"鸢尾花香水\","
        + "\"variant_seed\":0,\"facts\":{\"product_name\":\"鸢尾花香水\",\"color\":\"蓝紫渐变\"},"
        + "\"dna\":{\"styleKeywords\":[\"极简\",\"自然\"],"
        + "\"colors\":{\"background\":\"#F5F5F3\",\"primary\":\"#2E6B4F\"},"
        + "\"lighting\":{\"type\":\"SOFT\",\"direction\":\"FRONT\"},"
        + "\"productRatio\":{\"min\":15,\"max\":30},\"saturation\":\"LOW\","
        + "\"contrastLevel\":\"MEDIUM\",\"whitespaceLevel\":\"HIGH\"}}";

    /**
     * 与种子脚本里 case-plan-deterministic 的判据保持一致
     */
    private static final String EXPECTED_JSON =
        "{\"required_paths\":[\"directions\",\"screens\",\"direction_count\",\"screen_count\","
            + "\"dna_valid\",\"no_enum_leak\",\"reproducible_probe\"],"
            + "\"equals\":{\"direction_count\":3,\"deterministic\":true,\"dna_valid\":true,"
            + "\"no_enum_leak\":true,\"reproducible_probe\":true,\"drafts_mention_product\":true},"
            + "\"min_items\":{\"directions\":3,\"screens\":1}}";

    private AigEvaluationCaseMapper caseMapper;
    private AigEvaluationRunMapper runMapper;
    private AigAgentMapper agentMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigEvaluationServiceImpl service;
    private final AtomicReference<Long> lastRunId = new AtomicReference<>();

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigEvaluationCase.class);
        TableInfoHelper.initTableInfo(assistant, AigEvaluationRun.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigPackageVersion.class);
    }

    @BeforeEach
    void setUp() {
        caseMapper = mock(AigEvaluationCaseMapper.class);
        runMapper = mock(AigEvaluationRunMapper.class);
        agentMapper = mock(AigAgentMapper.class);
        AigSkillMapper skillMapper = mock(AigSkillMapper.class);
        AigPackageMapper packageMapper = mock(AigPackageMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        AigSkillVersionMapper skillVersionMapper = mock(AigSkillVersionMapper.class);
        AigPackageVersionMapper packageVersionMapper = mock(AigPackageVersionMapper.class);
        AigEvaluationSubjectRegistry registry = new AigEvaluationSubjectRegistry();
        // 真实执行器（不是 mock）：跑的是真正的策划引擎
        registry.setSubjects(List.of(new CreativePlanningEvaluationSubject()));
        service = new AigEvaluationServiceImpl(caseMapper, runMapper, agentMapper, skillMapper,
            packageMapper, agentVersionMapper, skillVersionMapper, packageVersionMapper, registry,
            new AigExpectedRuleChecker(JsonMapper.builder().build()), JsonMapper.builder().build());

        when(agentVersionMapper.update(isNull(), any())).thenReturn(1);
        when(runMapper.update(isNull(), any())).thenReturn(1);
        when(runMapper.insert(any(AigEvaluationRun.class))).thenAnswer(invocation -> {
            AigEvaluationRun run = invocation.getArgument(0);
            run.setRunId(9401L);
            lastRunId.set(9401L);
            return 1;
        });
    }

    /**
     * 桩上 Agent 版本（SANDBOX_TESTED + 声明黄金用例集合）。
     *
     * @param evaluationRunId 版本上记录的最近一次评测
     */
    private void stubAgentVersion(Long evaluationRunId) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setAgentId(AGENT_ID);
        version.setVersion("1.0.0");
        version.setReleaseStatus("SANDBOX_TESTED");
        version.setConfigJson("{\"implementation\":\"CreativeDraftFactory\",\"golden_cases\":[\""
            + CASE_CODE + "\"]}");
        version.setEvaluationRunId(evaluationRunId);
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);

        AigAgent agent = new AigAgent();
        agent.setAgentId(AGENT_ID);
        agent.setAgentCode(CreativePlanningEvaluationSubject.SUBJECT_CODE);
        when(agentMapper.selectById(AGENT_ID)).thenReturn(agent);
    }

    /**
     * 桩上用例。
     *
     * @param expectedJson 判据
     */
    private void stubCase(String expectedJson) {
        AigEvaluationCase evaluationCase = new AigEvaluationCase();
        evaluationCase.setCaseId(CASE_ID);
        evaluationCase.setCaseCode(CASE_CODE);
        evaluationCase.setCaseName("策划确定性用例");
        evaluationCase.setCaseType("PLAN");
        evaluationCase.setInputSnapshotRef(SNAPSHOT);
        evaluationCase.setExpectedJson(expectedJson);
        evaluationCase.setCostMin(BigDecimal.ZERO);
        evaluationCase.setCostMax(BigDecimal.ZERO);
        evaluationCase.setStatus("0");
        when(caseMapper.selectOne(any())).thenReturn(evaluationCase);
    }

    /**
     * 造运行入参。
     *
     * @return 入参
     */
    private static AigEvaluationRunBo runBo() {
        AigEvaluationRunBo bo = new AigEvaluationRunBo();
        bo.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        bo.setTargetVersionId(VERSION_ID);
        bo.setCaseCodes(List.of(CASE_CODE));
        bo.setOperatorId(7L);
        return bo;
    }

    /**
     * 与种子脚本里 case-plan-brand-brief 的判据**逐字一致**（同一条判据在真实产出上求值）
     */
    private static final String BRAND_BRIEF_EXPECTED_JSON =
        "{\"required_paths\":[\"directions\",\"screens\",\"brand_brief_used\",\"must_show_first_line\","
            + "\"must_show_in_closing_screen\",\"selling_point_count\",\"selling_points_landed\","
            + "\"selling_point_screens\"],"
            + "\"equals\":{\"direction_count\":3,\"brand_brief_used\":true,"
            + "\"must_show_in_closing_screen\":true,\"selling_point_count\":2,"
            + "\"selling_points_landed\":2,\"selling_point_screens\":2,\"deterministic\":true,"
            + "\"reproducible_probe\":true},"
            + "\"min_items\":{\"directions\":3,\"screens\":1},"
            + "\"must_contain\":[\"容量 50ml\",\"手工缠花\",\"蓝紫渐变釉色\"]}";

    /**
     * 与种子脚本里 case-plan-brand-brief 的输入快照**逐字一致**
     */
    private static final String BRAND_BRIEF_SNAPSHOT =
        "inline:{\"product_name\":\"鸢尾花香水\",\"variant_seed\":0,"
            + "\"facts\":{\"product_name\":\"鸢尾花香水\",\"color\":\"蓝紫渐变\"},"
            + "\"dna\":{\"colors\":{\"background\":\"#F5F5F3\",\"primary\":\"#2E6B4F\"},"
            + "\"lighting\":{\"type\":\"SOFT\",\"direction\":\"FRONT\"},\"saturation\":\"LOW\","
            + "\"contrastLevel\":\"MEDIUM\",\"whitespaceLevel\":\"HIGH\"},"
            + "\"brand_brief\":{\"must_show_first_line\":\"容量 50ml\","
            + "\"selling_points\":[{\"title\":\"手工缠花\",\"content\":\"手工缠花工艺\"},"
            + "{\"title\":\"蓝紫渐变\",\"content\":\"蓝紫渐变釉色\"}]}}";

    @Test
    @DisplayName("★ 端到端：种子里「品牌要求进分镜」那条用例，同一条判据在真实产出上求值 → PASS")
    void brandBriefCasePassesEndToEnd() {
        stubAgentVersion(null);
        AigEvaluationCase evaluationCase = new AigEvaluationCase();
        evaluationCase.setCaseId(CASE_ID);
        evaluationCase.setCaseCode(CASE_CODE);
        evaluationCase.setCaseName("策划·品牌要求进分镜");
        evaluationCase.setCaseType("PLAN");
        evaluationCase.setInputSnapshotRef(BRAND_BRIEF_SNAPSHOT);
        evaluationCase.setExpectedJson(BRAND_BRIEF_EXPECTED_JSON);
        evaluationCase.setCostMin(BigDecimal.ZERO);
        evaluationCase.setCostMax(BigDecimal.ZERO);
        evaluationCase.setStatus("0");
        when(caseMapper.selectOne(any())).thenReturn(evaluationCase);

        List<AigEvaluationRun> runs = service.runEvaluation(runBo());

        assertEquals(1, runs.size());
        AigEvaluationRun run = runs.get(0);
        assertEquals(AigEvaluationStatusEnum.PASS.getCode(), run.getResultStatus(),
            "判据未通过（说明品牌要求没落进分镜，或执行器没把 brand_brief 喂给派生器）："
                + run.getScoreJson());
    }

    @Test
    @DisplayName("端到端：真实策划引擎跑通用例 → PASS，成本 0，人工复核不要求")
    void endToEndPassesWithRealEngine() {
        stubAgentVersion(null);
        stubCase(EXPECTED_JSON);

        List<AigEvaluationRun> runs = service.runEvaluation(runBo());

        assertEquals(1, runs.size());
        AigEvaluationRun run = runs.get(0);
        assertEquals(AigEvaluationStatusEnum.PASS.getCode(), run.getResultStatus(),
            "run=" + run.getResultStatus() + " score=" + run.getScoreJson());
        assertNull(run.getReviewResult(), "用例没有 Rubric，不需要人工复核");
        assertEquals(BigDecimal.ZERO, run.getCostAmount(), "确定性引擎成本为 0");
        assertEquals("N", run.getExternalCall());
        assertNull(run.getRemark(), "通过时不该有失败摘要");
        assertNotNull(run.getScoreJson());
        assertTrue(run.getScoreJson().contains("\"passed\":true"), run.getScoreJson());
        assertTrue(run.getScoreJson().contains("OK"), run.getScoreJson());
    }

    @Test
    @DisplayName("端到端：判据不满足 → FAIL 且失败摘要指出是哪条路径（判据真的在看真实产出）")
    void endToEndFailsWhenJudgementNotMet() {
        stubAgentVersion(null);
        stubCase("{\"required_paths\":[\"definitely_not_produced\"],\"equals\":{\"direction_count\":99}}");

        AigEvaluationRun run = service.runEvaluation(runBo()).get(0);

        assertEquals(AigEvaluationStatusEnum.FAIL.getCode(), run.getResultStatus());
        assertNotNull(run.getRemark());
        assertTrue(run.getRemark().contains("definitely_not_produced"), run.getRemark());
        assertTrue(run.getRemark().contains("direction_count"), run.getRemark());
        assertTrue(run.getScoreJson().contains("\"passed\":false"), run.getScoreJson());
    }

    @Test
    @DisplayName("端到端：跑完一次通过后，「黄金用例已通过」的证据成立（发布门槛可消费）")
    void evidenceSatisfiedAfterPassRun() {
        stubAgentVersion(null);
        stubCase(EXPECTED_JSON);
        service.runEvaluation(runBo());

        // 版本上的最近一次评测指针已写入（服务在批次结束时更新）
        org.mockito.Mockito.verify(agentVersionMapper).update(isNull(), any());
        // 账本里能查到这次通过（证据从账本读，不从版本行读）
        AigEvaluationRun persisted = new AigEvaluationRun();
        persisted.setRunId(lastRunId.get());
        persisted.setRunNo("EV-TEST");
        persisted.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        persisted.setTargetVersionId(VERSION_ID);
        persisted.setCaseId(CASE_ID);
        persisted.setResultStatus(AigEvaluationStatusEnum.PASS.getCode());
        persisted.setOperateTime(java.time.LocalDateTime.now());
        when(runMapper.selectList(any())).thenReturn(List.of(persisted));
        stubAgentVersion(lastRunId.get());

        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);

        assertTrue(evidence.satisfied(), evidence.reason());
        assertEquals(AigGoldenCaseEvidence.VERDICT_PASS, evidence.caseVerdicts().get(CASE_CODE));
        assertFalse(evidence.declaredCaseCodes().isEmpty());
    }

}
