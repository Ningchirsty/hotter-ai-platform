package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.bo.AigEvaluationCaseBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationManualRunBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationReviewBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationRunBo;
import org.dromara.aigov.agent.enums.AigEvaluationReviewEnum;
import org.dromara.aigov.agent.enums.AigEvaluationStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.AigEvaluationSubjectRegistry;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleChecker;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;
import org.dromara.aigov.agent.evaluation.IAigEvaluationSubject;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationCaseMapper;
import org.dromara.aigov.agent.mapper.AigEvaluationRunMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评测服务测试（设计 §13.2）。
 *
 * <p>纯 Mockito：不加载 Spring、不碰数据库。用假执行器（{@link FakeSubject}）代替真实业务实现，
 * 判据用真实的 {@link AigExpectedRuleChecker}——判据是这一步的产出，不能再用假的糊过去。</p>
 *
 * <p>这里钉住的是「不生效也不会报错」的四类违约：</p>
 * <ol>
 *     <li>挑着跑用例（用最容易过的一两条换「黄金用例通过」）；</li>
 *     <li>评测跑在不该跑的阶段（结论会被后续改动作废，或版本已经出去了）；</li>
 *     <li>没有执行器时假装跑过；</li>
 *     <li>人工复核把机器的不通过改成通过，或「待复核」被当成「通过」。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigEvaluationServiceImplTest {

    private static final long VERSION_ID = 8101L;

    private static final long AGENT_ID = 8201L;

    private static final long PACKAGE_VERSION_ID = 8301L;

    private static final String CASE_A = "case-plan-a";

    private static final String CASE_B = "case-plan-b";

    private static final String AGENT_CODE = "creative_planning";

    private AigEvaluationCaseMapper caseMapper;
    private AigEvaluationRunMapper runMapper;
    private AigAgentMapper agentMapper;
    private AigPackageMapper packageMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigPackageVersionMapper packageVersionMapper;
    private AigEvaluationSubjectRegistry subjectRegistry;
    private AigEvaluationServiceImpl service;
    private FakeSubject subject;
    private final AtomicInteger runIdSeq = new AtomicInteger(9000);

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
        packageMapper = mock(AigPackageMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        AigSkillVersionMapper skillVersionMapper = mock(AigSkillVersionMapper.class);
        packageVersionMapper = mock(AigPackageVersionMapper.class);
        subjectRegistry = new AigEvaluationSubjectRegistry();
        subject = new FakeSubject(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), AGENT_CODE);
        subjectRegistry.setSubjects(List.of(subject));
        service = new AigEvaluationServiceImpl(caseMapper, runMapper, agentMapper, skillMapper,
            packageMapper, agentVersionMapper, skillVersionMapper, packageVersionMapper,
            subjectRegistry, new AigExpectedRuleChecker(JsonMapper.builder().build()),
            JsonMapper.builder().build());

        when(runMapper.insert(any(AigEvaluationRun.class))).thenAnswer(invocation -> {
            invocation.<AigEvaluationRun>getArgument(0).setRunId((long) runIdSeq.incrementAndGet());
            return 1;
        });
        when(runMapper.update(isNull(), any())).thenReturn(1);
        when(agentVersionMapper.update(isNull(), any())).thenReturn(1);
        when(packageVersionMapper.update(isNull(), any())).thenReturn(1);
    }

    /**
     * 假执行器：只按预设产出返回（真实实现由业务模块在 7b 提供）。
     */
    private static final class FakeSubject implements IAigEvaluationSubject {

        private final String targetType;
        private final String code;
        private AigEvaluationOutcome outcome;
        private RuntimeException failure;
        private int calls;
        private AigEvaluationRequest lastRequest;

        FakeSubject(String targetType, String code) {
            this.targetType = targetType;
            this.code = code;
        }

        @Override
        public boolean supports(String targetType, String subjectCode) {
            return this.targetType.equals(targetType) && this.code.equals(subjectCode);
        }

        @Override
        public String describe() {
            return targetType + ":" + code;
        }

        @Override
        public AigEvaluationOutcome execute(AigEvaluationRequest request) {
            this.calls++;
            this.lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return outcome;
        }
    }

    /**
     * 造一个 Agent 版本桩（默认 SANDBOX_TESTED、声明两条黄金用例）。
     *
     * @param configJson       config_json 内容
     * @param evaluationRunId  版本上记录的最近一次评测
     */
    private void stubAgentVersion(String configJson, Long evaluationRunId) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setAgentId(AGENT_ID);
        version.setVersion("1.2.0");
        version.setReleaseStatus("SANDBOX_TESTED");
        version.setConfigJson(configJson);
        version.setEvaluationRunId(evaluationRunId);
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);
        stubAgentDefinition();
    }

    /**
     * 造一个用例桩（按编码匹配，供多用例场景使用）。
     *
     * @param code         编码
     * @param expectedJson 判据
     * @param rubricJson   Rubric
     */
    private void stubCase(String code, String expectedJson, String rubricJson) {
        when(caseMapper.selectOne(argThatWrapper(code))).thenReturn(caseEntity(code, expectedJson,
            rubricJson, null, null));
    }

    /**
     * 造一个用例实体。
     */
    private static AigEvaluationCase caseEntity(String code, String expectedJson, String rubricJson,
                                                BigDecimal costMin, BigDecimal costMax) {
        AigEvaluationCase entity = new AigEvaluationCase();
        entity.setCaseId((long) code.hashCode());
        entity.setCaseCode(code);
        entity.setCaseName(code);
        entity.setCaseType("PLAN");
        entity.setExpectedJson(expectedJson);
        entity.setRubricJson(rubricJson);
        entity.setCostMin(costMin);
        entity.setCostMax(costMax);
        entity.setDataLevel("INTERNAL");
        entity.setStatus("0");
        return entity;
    }

    /**
     * 造一个运行行。
     */
    private static AigEvaluationRun run(String caseCode, String status, String review) {
        AigEvaluationRun run = new AigEvaluationRun();
        run.setRunId(7000L + Math.abs(caseCode.hashCode() % 100));
        run.setRunNo("EV-" + caseCode);
        run.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        run.setTargetVersionId(VERSION_ID);
        run.setCaseId((long) caseCode.hashCode());
        run.setResultStatus(status);
        run.setReviewResult(review);
        run.setOperateTime(LocalDateTime.now());
        return run;
    }

    /**
     * 按 wrapper 里出现的编码匹配（多用例场景下 Mockito 不解析 SQL，只能这样区分）。
     */
    private static Wrapper<AigEvaluationCase> argThatWrapper(String code) {
        return org.mockito.ArgumentMatchers.argThat(
            (Wrapper<AigEvaluationCase> wrapper) -> wrapperHasValue(wrapper, code));
    }

    /**
     * wrapper 的参数值里是否含某个值。
     *
     * <p>注意：MyBatis-Plus 的参数值是在<b>渲染 SQL 段时</b>才填进 {@code paramNameValuePairs} 的
     * （{@code eq()} 里存的是个 lambda），所以必须先调一次 {@code getSqlSegment()}。
     * 直接读 map 会永远是空——看起来像「桩没生效」，其实是读早了。</p>
     */
    private static boolean wrapperHasValue(Wrapper<?> wrapper, Object value) {
        if (!(wrapper instanceof LambdaQueryWrapper<?> lambda)) {
            return false;
        }
        lambda.getSqlSegment();
        return lambda.getParamNameValuePairs().containsValue(value);
    }

    /**
     * 桩上 Agent 定义（编码用于派发执行器）。
     */
    private void stubAgentDefinition() {
        AigAgent agent = new AigAgent();
        agent.setAgentId(AGENT_ID);
        agent.setAgentCode(AGENT_CODE);
        when(agentMapper.selectById(AGENT_ID)).thenReturn(agent);
    }

    // ---------------------------------------------------------------- 用例定义

    @Test
    @DisplayName("定义用例：未知判据当场拒绝（不能等到跑评测时才当成「跑不过」）")
    void defineCaseRejectsUnknownRule() {
        AigEvaluationCaseBo bo = new AigEvaluationCaseBo();
        bo.setCaseCode("case-x");
        bo.setCaseName("x");
        bo.setCaseType("PLAN");
        bo.setExpectedJson("{\"nope\":1}");

        ServiceException error = assertThrows(ServiceException.class, () -> service.defineCase(bo));
        assertTrue(error.getMessage().contains("不可执行"), error.getMessage());
        assertTrue(error.getMessage().contains("未知判据"), error.getMessage());
        verify(caseMapper, never()).insert(any(AigEvaluationCase.class));
    }

    @Test
    @DisplayName("定义用例：判据与 Rubric 都空 → 拒绝（这样的用例无法判定通过与否）")
    void defineCaseRejectsNoJudgement() {
        AigEvaluationCaseBo bo = new AigEvaluationCaseBo();
        bo.setCaseCode("case-y");
        bo.setCaseName("y");
        bo.setCaseType("PLAN");

        ServiceException error = assertThrows(ServiceException.class, () -> service.defineCase(bo));
        assertTrue(error.getMessage().contains("无法判定"), error.getMessage());
    }

    @Test
    @DisplayName("定义用例：成本范围写反、类型未知、编码重复都拒绝")
    void defineCaseValidatesInputs() {
        AigEvaluationCaseBo base = new AigEvaluationCaseBo();
        base.setCaseCode("case-z");
        base.setCaseName("z");
        base.setCaseType("PLAN");
        base.setExpectedJson("{\"required_paths\":[\"a\"]}");

        AigEvaluationCaseBo badRange = new AigEvaluationCaseBo();
        badRange.setCaseCode("case-z");
        badRange.setCaseName("z");
        badRange.setCaseType("PLAN");
        badRange.setExpectedJson("{\"required_paths\":[\"a\"]}");
        badRange.setCostMin(new BigDecimal("2"));
        badRange.setCostMax(new BigDecimal("1"));
        assertTrue(assertThrows(ServiceException.class, () -> service.defineCase(badRange))
            .getMessage().contains("下限大于上限"));

        AigEvaluationCaseBo badType = new AigEvaluationCaseBo();
        badType.setCaseCode("case-z");
        badType.setCaseName("z");
        badType.setCaseType("NOPE");
        badType.setExpectedJson("{\"required_paths\":[\"a\"]}");
        assertTrue(assertThrows(ServiceException.class, () -> service.defineCase(badType))
            .getMessage().contains("未知的用例类型"));

        when(caseMapper.selectCount(any())).thenReturn(1L);
        assertTrue(assertThrows(ServiceException.class, () -> service.defineCase(base))
            .getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("定义用例：数据等级缺省为 INTERNAL、状态置正常、返回新ID")
    void defineCaseInserts() {
        when(caseMapper.selectCount(any())).thenReturn(0L);
        when(caseMapper.insert(any(AigEvaluationCase.class))).thenAnswer(invocation -> {
            invocation.<AigEvaluationCase>getArgument(0).setCaseId(8888L);
            return 1;
        });
        AigEvaluationCaseBo bo = new AigEvaluationCaseBo();
        bo.setCaseCode(" case-new ");
        bo.setCaseName("新用例");
        bo.setCaseType("plan");
        bo.setExpectedJson("{\"required_paths\":[\"a\"]}");

        assertEquals(8888L, service.defineCase(bo));

        ArgumentCaptor<AigEvaluationCase> captor = ArgumentCaptor.forClass(AigEvaluationCase.class);
        verify(caseMapper).insert(captor.capture());
        AigEvaluationCase saved = captor.getValue();
        assertEquals("case-new", saved.getCaseCode());
        assertEquals("PLAN", saved.getCaseType());
        assertEquals("INTERNAL", saved.getDataLevel());
        assertEquals("0", saved.getStatus());
    }

    // ---------------------------------------------------------------- 跑评测

    @Test
    @DisplayName("评测只跑在 SANDBOX_TESTED：更早的结论会被改动作废，更晚的版本已经出去了")
    void runRejectsNonSandboxTested() {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setAgentId(AGENT_ID);
        version.setReleaseStatus("VALIDATED");
        version.setConfigJson("{\"golden_cases\":[\"" + CASE_A + "\"]}");
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);
        stubAgentDefinition();

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(error.getMessage().contains("只对 SANDBOX_TESTED"), error.getMessage());
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("挑着跑用例被拒：本次集合必须等于版本声明的黄金用例集合")
    void runRejectsCaseSetMismatch() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\",\"" + CASE_B + "\"]}", null);

        ServiceException tooFew = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(tooFew.getMessage().contains("不一致"), tooFew.getMessage());

        ServiceException wrongOne = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A, "case-made-up"))));
        assertTrue(wrongOne.getMessage().contains("不一致"), wrongOne.getMessage());
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("没有声明黄金用例集合 / 声明读不出来：都拒绝，且说清去哪查")
    void runRejectsMissingDeclaration() {
        stubAgentVersion("{\"workflow\":{}}", null);
        ServiceException none = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(none.getMessage().contains("没有声明黄金用例集合"), none.getMessage());
        assertTrue(none.getMessage().contains("config_json"), none.getMessage());

        stubAgentVersion("{oops", null);
        ServiceException broken = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(broken.getMessage().contains("读不出"), broken.getMessage());
    }

    @Test
    @DisplayName("没有注册执行器就报错，并列出平台认识哪些对象（不能假装跑过）")
    void runRejectsWithoutSubjectExecutor() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        subjectRegistry.setSubjects(List.of());

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(error.getMessage().contains("没有为评测对象"), error.getMessage());
        assertTrue(error.getMessage().contains("已注册"), error.getMessage());
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("主干：跑通两条用例、结论落库、最近一次评测记到版本上")
    void runRecordsVerdicts() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\",\"" + CASE_B + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"plan.title\"]}", null);
        stubCase(CASE_B, "{\"required_paths\":[\"plan.title\"]}", "{\"dimensions\":[]}");
        subject.outcome = new AigEvaluationOutcome("{\"plan\":{\"title\":\"标题\"}}", true,
            new BigDecimal("0.1200"), 250L, 5L, "qwen-image-3.0-pro", true, "trace-abc");
        // 记录「插入那一刻」的状态：结论是后来条件更新写回的，captor 抓到的是同一个可变对象，
        // 不在这里快照的话，事后读到的是最终状态（看起来像「一插库就是 PASS」）
        List<String> insertStates = new ArrayList<>();
        when(runMapper.insert(any(AigEvaluationRun.class))).thenAnswer(invocation -> {
            AigEvaluationRun inserted = invocation.getArgument(0);
            inserted.setRunId((long) runIdSeq.incrementAndGet());
            insertStates.add(inserted.getResultStatus());
            return 1;
        });
        AtomicReference<Wrapper<AigEvaluationRun>> written = new AtomicReference<>();
        when(runMapper.update(isNull(), any())).thenAnswer(invocation -> {
            written.set(invocation.getArgument(1));
            return 1;
        });

        List<AigEvaluationRun> runs = service.runEvaluation(runBo(List.of(CASE_A, CASE_B)));

        assertEquals(2, runs.size());
        assertEquals(AigEvaluationStatusEnum.PASS.getCode(), runs.get(0).getResultStatus());
        assertNull(runs.get(0).getReviewResult(), "没有 Rubric 就不需要人工复核");
        assertEquals(AigEvaluationReviewEnum.MANUAL.getCode(), runs.get(1).getReviewResult(),
            "带 Rubric 的用例机器通过后仍需人工复核");
        assertEquals(new BigDecimal("0.1200"), runs.get(0).getCostAmount());
        assertEquals(250L, runs.get(0).getLatencyMs());
        assertEquals("Y", runs.get(0).getExternalCall());
        assertEquals("trace-abc", runs.get(0).getTraceId());
        assertEquals(2, subject.calls);
        assertNotNull(runs.get(0).getRunNo());
        assertTrue(runs.get(0).getRunNo().startsWith("EV"), runs.get(0).getRunNo());

        // RUNNING 先落库、结论用条件更新写回（崩溃时库里留的是「跑过但没结论」）
        assertEquals(List.of(AigEvaluationStatusEnum.RUNNING.getCode(),
            AigEvaluationStatusEnum.RUNNING.getCode()), insertStates,
            "插入时必须是 RUNNING，结论是后来写回的");
        assertNotNull(written.get());
        LambdaUpdateWrapper<AigEvaluationRun> wrapper =
            (LambdaUpdateWrapper<AigEvaluationRun>) written.get();
        wrapper.getSqlSegment();
        assertTrue(wrapper.getSqlSegment().contains("result_status"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs()
            .containsValue(AigEvaluationStatusEnum.RUNNING.getCode()),
            "写回必须带「当前状态是 RUNNING」这个条件：" + wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(AigEvaluationStatusEnum.PASS.getCode()),
            "目标状态要落到 PASS：" + wrapper.getSqlSegment());

        // 最近一次评测写到版本上（供页面显示与「库被手工改过」的交叉核对）
        verify(agentVersionMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("判据不过 → FAIL 且失败摘要落 remark（改 Prompt 时看得到是哪条判据）")
    void runRecordsFailureWithSummary() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"plan.title\"]}", null);
        subject.outcome = new AigEvaluationOutcome("{\"plan\":{}}", true, BigDecimal.ZERO, 10L, null,
            null, false, null);

        List<AigEvaluationRun> runs = service.runEvaluation(runBo(List.of(CASE_A)));

        AigEvaluationRun run = runs.get(0);
        assertEquals(AigEvaluationStatusEnum.FAIL.getCode(), run.getResultStatus());
        assertNotNull(run.getRemark());
        assertTrue(run.getRemark().contains("plan.title"), run.getRemark());
        assertNull(run.getReviewResult(), "机器判据没过时不需要人工再确认一遍");
        assertNotNull(run.getScoreJson());
        assertTrue(run.getScoreJson().contains("failureCount"), run.getScoreJson());
    }

    @Test
    @DisplayName("执行器抛异常 → ERROR（不是 FAIL）：没跑完是环境/用例的问题，跑去改 Prompt 是白费功夫")
    void runRecordsErrorWhenExecutorThrows() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        subject.failure = new IllegalStateException("快照取不到");

        List<AigEvaluationRun> runs = service.runEvaluation(runBo(List.of(CASE_A)));

        AigEvaluationRun run = runs.get(0);
        assertEquals(AigEvaluationStatusEnum.ERROR.getCode(), run.getResultStatus());
        assertTrue(run.getRemark().contains("执行失败"), run.getRemark());
        assertTrue(run.getRemark().contains("快照取不到"), run.getRemark());
    }

    @Test
    @DisplayName("结论写回带 RUNNING 条件：影响 0 行即并发冲突（不覆盖他人结论）")
    void runReportsConcurrentConflict() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        subject.outcome = new AigEvaluationOutcome("{\"a\":1}", true, null, 1L, null, null, false, null);
        when(runMapper.update(isNull(), any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.runEvaluation(runBo(List.of(CASE_A))));
        assertTrue(error.getMessage().contains("并发"), error.getMessage());
    }

    // ---------------------------------------------------------------- 人工复核

    @Test
    @DisplayName("人工复核：只有已结束的运行可复核；MANUAL 不能作为复核结论提交")
    void reviewValidatesInput() {
        AigEvaluationReviewBo bo = new AigEvaluationReviewBo();
        bo.setReviewResult("PASS");
        bo.setRunId(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.reviewRun(bo))
            .getMessage().contains("运行ID不能为空"));

        bo.setRunId(1L);
        when(runMapper.selectById(1L)).thenReturn(run(CASE_A,
            AigEvaluationStatusEnum.RUNNING.getCode(), null));
        assertTrue(assertThrows(ServiceException.class, () -> service.reviewRun(bo))
            .getMessage().contains("尚未得出结论"));

        when(runMapper.selectById(1L)).thenReturn(run(CASE_A,
            AigEvaluationStatusEnum.PASS.getCode(), AigEvaluationReviewEnum.MANUAL.getCode()));
        bo.setReviewResult("MANUAL");
        assertTrue(assertThrows(ServiceException.class, () -> service.reviewRun(bo))
            .getMessage().contains("待人工复核"), "MANUAL 是状态本身，不是复核结论");

        bo.setReviewResult("YES");
        assertTrue(assertThrows(ServiceException.class, () -> service.reviewRun(bo))
            .getMessage().contains("未知的复核结论"));

        bo.setReviewResult("PASS");
        bo.setTotalScore(new BigDecimal("10000"));
        assertTrue(assertThrows(ServiceException.class, () -> service.reviewRun(bo))
            .getMessage().contains("超出可存储范围"));
    }

    @Test
    @DisplayName("人工复核落库：结论/复核人/时间/总分，且 remark 追加而不是覆盖自动摘要")
    void reviewRecordsVerdict() {
        AigEvaluationRun existing = run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(),
            AigEvaluationReviewEnum.MANUAL.getCode());
        existing.setRemark("[required_paths] x 缺失");
        when(runMapper.selectById(existing.getRunId())).thenReturn(existing);
        AigEvaluationReviewBo bo = new AigEvaluationReviewBo();
        bo.setRunId(existing.getRunId());
        bo.setReviewResult("PASS");
        bo.setTotalScore(new BigDecimal("4.5"));
        bo.setReviewerId(77L);
        bo.setRemark("人工看过了");
        AtomicReference<Wrapper<AigEvaluationRun>> written = new AtomicReference<>();
        when(runMapper.update(isNull(), any())).thenAnswer(invocation -> {
            written.set(invocation.getArgument(1));
            return 1;
        });

        service.reviewRun(bo);

        LambdaUpdateWrapper<AigEvaluationRun> wrapper =
            (LambdaUpdateWrapper<AigEvaluationRun>) written.get();
        assertTrue(wrapper.getSqlSet().contains("review_result"), wrapper.getSqlSet());
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PASS"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(77L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(new BigDecimal("4.5")));
        String remark = wrapper.getParamNameValuePairs().values().stream()
            .filter(value -> value instanceof String text && text.contains("人工看过了"))
            .map(String::valueOf).findFirst().orElse(null);
        assertNotNull(remark, "复核说明应落库");
        assertTrue(remark.startsWith("[required_paths] x 缺失"), "自动判据摘要不能被覆盖：" + remark);
        assertTrue(remark.contains("复核：人工看过了"), remark);
    }

    // ---------------------------------------------------------------- 证据结论

    @Test
    @DisplayName("证据：全部用例最近一次 PASS 且无需复核 → 满足")
    void evidenceSatisfied() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", 7000L + Math.abs(CASE_A.hashCode() % 100));
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        AigEvaluationRun pass = run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(), null);
        when(runMapper.selectList(any())).thenReturn(List.of(pass));

        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);

        assertTrue(evidence.satisfied(), evidence.reason());
        assertEquals(AigGoldenCaseEvidence.VERDICT_PASS, evidence.caseVerdicts().get(CASE_A));
        assertNull(evidence.reason());
    }

    @Test
    @DisplayName("证据：最近一次 FAIL / 从未跑过 / 待人工复核 → 都不放行，且说清是哪条用例")
    void evidenceBlocked() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);

        when(runMapper.selectList(any())).thenReturn(List.of());
        AigGoldenCaseEvidence never = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(never.satisfied());
        assertTrue(never.reason().contains("还没有任何评测运行"), never.reason());
        assertEquals(AigGoldenCaseEvidence.VERDICT_NO_RUN, never.caseVerdicts().get(CASE_A));

        when(runMapper.selectList(any())).thenReturn(List.of(
            run(CASE_A, AigEvaluationStatusEnum.FAIL.getCode(), null)));
        assertTrue(service.goldenCaseEvidence(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            VERSION_ID).reason().contains("不通过"));

        when(runMapper.selectList(any())).thenReturn(List.of(
            run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(),
                AigEvaluationReviewEnum.MANUAL.getCode())));
        AigGoldenCaseEvidence pending = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(pending.satisfied(), "「待复核」不是「复核通过」");
        assertEquals(AigGoldenCaseEvidence.VERDICT_REVIEW_MANUAL, pending.caseVerdicts().get(CASE_A));
        assertTrue(pending.reason().contains("待人工复核"), pending.reason());
    }

    @Test
    @DisplayName("证据：版本上的「最近一次评测」与账本不一致 → 拦下（说明库被手工改过）")
    void evidenceBlocksStalePointer() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", 4242L);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        when(runMapper.selectList(any())).thenReturn(List.of(
            run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(), null)));

        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("不一致"), evidence.reason());
    }

    @Test
    @DisplayName("证据：没有声明集合 / 声明读不出来 → 都拦下并指出去哪查")
    void evidenceBlockedWithoutDeclaration() {
        stubAgentVersion("{\"workflow\":{}}", null);
        AigGoldenCaseEvidence none = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(none.satisfied());
        assertTrue(none.reason().contains("没有声明"), none.reason());

        stubAgentVersion("{oops", null);
        AigGoldenCaseEvidence broken = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(broken.satisfied());
        assertTrue(broken.reason().contains("读不出"), broken.reason());
    }

    @Test
    @DisplayName("证据：更新的一条被逻辑删除 → 拦下（删记录不能让它回退到更早的 PASS 从而放行）")
    void evidenceBlocksNewerDeletedRun() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        AigEvaluationRun visiblePass = run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(), null);
        visiblePass.setOperateTime(LocalDateTime.now().minusHours(2));
        when(runMapper.selectList(any())).thenReturn(List.of(visiblePass));

        // 被删的那条更新（真库探针实测：@TableLogic 会让普通查询看不见它，最近一次就回退到更早的 PASS）
        AigEvaluationRun deletedFail = run(CASE_A, AigEvaluationStatusEnum.FAIL.getCode(), null);
        deletedFail.setRunId(visiblePass.getRunId() + 1);
        deletedFail.setOperateTime(LocalDateTime.now().minusHours(1));
        when(runMapper.selectNewestDeletedRun(any(), any(), any())).thenReturn(deletedFail);

        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);

        assertFalse(evidence.satisfied(), "删除一条更新的失败记录不该让它变成通过");
        assertEquals(AigGoldenCaseEvidence.VERDICT_DELETED_NEWER, evidence.caseVerdicts().get(CASE_A));
        assertTrue(evidence.reason().contains("逻辑删除"), evidence.reason());
        assertTrue(evidence.reason().contains("重跑"), "要给出可恢复的出路：" + evidence.reason());

        // 被删的那条更旧（只是清理历史）→ 不影响结论
        AigEvaluationRun olderDeleted = run(CASE_A, AigEvaluationStatusEnum.FAIL.getCode(), null);
        olderDeleted.setRunId(visiblePass.getRunId() - 1);
        olderDeleted.setOperateTime(LocalDateTime.now().minusHours(3));
        when(runMapper.selectNewestDeletedRun(any(), any(), any())).thenReturn(olderDeleted);
        assertTrue(service.goldenCaseEvidence(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(),
            VERSION_ID).satisfied(), "被删的是更旧的记录时不该拦住");
    }

    @Test
    @DisplayName("Package 版本的黄金用例声明在 Manifest 里（包版本表没有 config_json）")
    void packageGoldenCasesComeFromManifest() {
        AigPackageVersion version = new AigPackageVersion();
        version.setPackageVersionId(PACKAGE_VERSION_ID);
        version.setPackageId(1L);
        version.setVersion("1.0.0");
        version.setReleaseStatus("SANDBOX_TESTED");
        version.setManifestJson("{\"golden_cases\":[\"case-pkg-1\",\"case-pkg-2\"]}");
        when(packageVersionMapper.selectById(PACKAGE_VERSION_ID)).thenReturn(version);
        AigPackage pkg = new AigPackage();
        pkg.setPackageId(1L);
        pkg.setPackageCode("vision-planning-skill");
        when(packageMapper.selectById(1L)).thenReturn(pkg);

        List<String> declared = service.declaredGoldenCases(
            AigReleaseTargetTypeEnum.PACKAGE_VERSION.getCode(), PACKAGE_VERSION_ID);

        assertEquals(List.of("case-pkg-1", "case-pkg-2"), declared);
    }

    /**
     * 造一个评测运行入参。
     */
    private static AigEvaluationRunBo runBo(List<String> caseCodes) {
        AigEvaluationRunBo bo = new AigEvaluationRunBo();
        bo.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        bo.setTargetVersionId(VERSION_ID);
        bo.setCaseCodes(caseCodes);
        bo.setOperatorId(9L);
        return bo;
    }

    // ---------------------------------------------------------------- 人工评测录入（2026-10-09 裁定）

    /**
     * 模拟「平台没有该对象的评测执行器」——这正是人工录入存在的理由。
     */
    private void withoutExecutors() {
        subjectRegistry.setSubjects(List.of());
    }

    /**
     * 造一个人工评测入参。
     *
     * @param entries 用例编码 → 结论
     */
    private static AigEvaluationManualRunBo manualBo(String method, String... entries) {
        AigEvaluationManualRunBo bo = new AigEvaluationManualRunBo();
        bo.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        bo.setTargetVersionId(VERSION_ID);
        bo.setMethod(method);
        bo.setOperatorId(7L);
        List<AigEvaluationManualRunBo.AigEvaluationManualCaseBo> cases = new ArrayList<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            AigEvaluationManualRunBo.AigEvaluationManualCaseBo item =
                new AigEvaluationManualRunBo.AigEvaluationManualCaseBo();
            item.setCaseCode(entries[i]);
            item.setVerdict(entries[i + 1]);
            cases.add(item);
        }
        bo.setCases(cases);
        return bo;
    }

    @Test
    @DisplayName("人工录入：平台没有执行器时能产出证据，且行上标着 executed_by=ADMIN")
    void manualRunRecordsAdminEvidence() {
        withoutExecutors();
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);

        List<AigEvaluationRun> runs = service.recordManualRuns(
            manualBo("在本地环境按用例逐条人工核对，输入快照同用例", CASE_A, "PASS"));

        assertEquals(1, runs.size());
        AigEvaluationRun run = runs.get(0);
        assertEquals("ADMIN", run.getExecutedBy(), "人工结论必须与机器结论在库里分得开");
        assertEquals(AigEvaluationStatusEnum.PASS.getCode(), run.getResultStatus());
        assertEquals("N", run.getExternalCall());
        assertEquals(7L, run.getCreateBy());
        assertTrue(run.getRemark().startsWith("人工评测（管理员）："), run.getRemark());
        assertTrue(run.getScoreJson().contains("\"mode\":\"MANUAL\""), run.getScoreJson());
        assertNull(run.getCostAmount(), "未上报成本要留空：未知不能用 0 冒充");
        verify(agentVersionMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("人工录入：照样不许挑着录（集合必须等于版本声明的集合）")
    void manualRunRefusesCherryPicking() {
        withoutExecutors();
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\",\"" + CASE_B + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        stubCase(CASE_B, "{\"required_paths\":[\"a\"]}", null);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.recordManualRuns(manualBo("只看了一条", CASE_A, "PASS")));
        assertTrue(error.getMessage().contains("不一致"), error.getMessage());
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));

        ServiceException missing = assertThrows(ServiceException.class,
            () -> service.recordManualRuns(manualBo("一条都没给")));
        assertTrue(missing.getMessage().contains("必须逐条给出结论"), missing.getMessage());
    }

    @Test
    @DisplayName("人工录入：平台有该对象执行器时拒绝——人工录入不能变成绕过平台判据的通道")
    void manualRunRefusesWhenExecutorExists() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.recordManualRuns(manualBo("绕过判据", CASE_A, "PASS")));
        assertTrue(error.getMessage().contains("请走机器评测"), error.getMessage());
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("人工录入：阶段/方式/操作人/结论取值四道前置校验，缺一不可")
    void manualRunValidations() {
        withoutExecutors();
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);

        AigEvaluationManualRunBo noMethod = manualBo("  ", CASE_A, "PASS");
        assertTrue(assertThrows(ServiceException.class, () -> service.recordManualRuns(noMethod))
            .getMessage().contains("方法与依据"), "写不出方法与依据的人工 PASS 只是一句主张");

        AigEvaluationManualRunBo noOperator = manualBo("核对过", CASE_A, "PASS");
        noOperator.setOperatorId(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.recordManualRuns(noOperator))
            .getMessage().contains("操作人"));

        AigEvaluationManualRunBo badVerdict = manualBo("核对过", CASE_A, "ERROR");
        assertTrue(assertThrows(ServiceException.class, () -> service.recordManualRuns(badVerdict))
            .getMessage().contains("只接受 PASS/FAIL"));

        // 版本还没到 SANDBOX_TESTED：人工录入与机器评测同一口径，不在这个阶段取证
        AigAgentVersion draft = new AigAgentVersion();
        draft.setAgentVersionId(VERSION_ID);
        draft.setAgentId(AGENT_ID);
        draft.setVersion("1.2.0");
        draft.setReleaseStatus("DRAFT");
        draft.setConfigJson("{\"golden_cases\":[\"" + CASE_A + "\"]}");
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(draft);
        stubAgentDefinition();
        assertTrue(assertThrows(ServiceException.class,
            () -> service.recordManualRuns(manualBo("核对过", CASE_A, "PASS")))
            .getMessage().contains("SANDBOX_TESTED"));
        verify(runMapper, never()).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("人工录入：用例声明了成本范围就必须上报成本（未上报≠在范围内）")
    void manualRunEnforcesCostRange() {
        withoutExecutors();
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        when(caseMapper.selectOne(argThatWrapper(CASE_A))).thenReturn(caseEntity(CASE_A,
            "{\"required_paths\":[\"a\"]}", null, BigDecimal.ZERO, BigDecimal.ZERO));

        AigEvaluationManualRunBo bo = manualBo("核对过", CASE_A, "PASS");
        assertTrue(assertThrows(ServiceException.class, () -> service.recordManualRuns(bo))
            .getMessage().contains("未上报成本"), "拿未知当合规");

        bo.setCostAmount(new BigDecimal("12.5"));
        assertTrue(assertThrows(ServiceException.class, () -> service.recordManualRuns(bo))
            .getMessage().contains("超出用例声明的范围"));

        bo.setCostAmount(BigDecimal.ZERO);
        List<AigEvaluationRun> runs = service.recordManualRuns(bo);
        assertEquals(0, BigDecimal.ZERO.compareTo(runs.get(0).getCostAmount()));
        // 三次调用里只有最后一次落库：前两次都被成本口径挡在写账本之前
        verify(runMapper, times(1)).insert(any(AigEvaluationRun.class));
    }

    @Test
    @DisplayName("人工录入：含 Rubric 的用例仍然走一次复核（录结论的人与复核的人不该合并）")
    void manualRunKeepsRubricReviewStep() {
        withoutExecutors();
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, null, "{\"criteria\":\"看文案\"}");

        List<AigEvaluationRun> runs = service.recordManualRuns(
            manualBo("按 Rubric 逐条看过", CASE_A, "PASS"));

        assertEquals(AigEvaluationReviewEnum.MANUAL.getCode(), runs.get(0).getReviewResult(),
            "人工录入 PASS 解决的是「谁产出结论」，不是「另一个人认不认」");

        // 而证据在复核通过之前不算满足
        when(runMapper.selectList(any())).thenReturn(List.of(runs.get(0)));
        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(evidence.satisfied());
        assertEquals(AigGoldenCaseEvidence.VERDICT_REVIEW_MANUAL, evidence.caseVerdicts().get(CASE_A));
    }

    @Test
    @DisplayName("证据：人工录入的 PASS 也放行，但把 executed_by=ADMIN 的用例带在证据里")
    void evidenceCarriesAdminProducedCases() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        AigEvaluationRun manual = run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(), null);
        manual.setExecutedBy("ADMIN");
        when(runMapper.selectList(any())).thenReturn(List.of(manual));

        AigGoldenCaseEvidence evidence = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);

        assertTrue(evidence.satisfied(), "门槛对两种来源一视同仁（2026-10-09 裁定：管理员来评测）");
        assertTrue(evidence.hasAdminProducedCases());
        assertEquals(List.of(CASE_A), evidence.adminCaseCodes());

        // 平台跑出来的不标人工来源
        AigEvaluationRun platform = run(CASE_A, AigEvaluationStatusEnum.PASS.getCode(), null);
        platform.setExecutedBy("PLATFORM");
        when(runMapper.selectList(any())).thenReturn(List.of(platform));
        AigGoldenCaseEvidence machine = service.goldenCaseEvidence(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID);
        assertFalse(machine.hasAdminProducedCases());
        assertTrue(machine.adminCaseCodes().isEmpty());
    }

    @Test
    @DisplayName("机器评测：运行行标着 executed_by=PLATFORM（不能与人工结论混为一谈）")
    void machineRunIsMarkedPlatform() {
        stubAgentVersion("{\"golden_cases\":[\"" + CASE_A + "\"]}", null);
        stubCase(CASE_A, "{\"required_paths\":[\"a\"]}", null);
        subject.outcome = new AigEvaluationOutcome("{\"a\":1}", true, BigDecimal.ZERO, 3L, null,
            null, false, null);

        List<AigEvaluationRun> runs = service.runEvaluation(runBo(List.of(CASE_A)));

        assertEquals("PLATFORM", runs.get(0).getExecutedBy());
    }

    @Test
    @DisplayName("执行器注册表探针：0 个/多个都不算「平台能自己跑」（多个是配置错误，不能把对象锁死）")
    void registryProbeOnlyCountsExactlyOne() {
        assertTrue(subjectRegistry.hasSingleExecutor(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), AGENT_CODE));
        assertFalse(subjectRegistry.hasSingleExecutor(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), "third_party_agent"));
        assertFalse(subjectRegistry.hasSingleExecutor(
            AigReleaseTargetTypeEnum.PACKAGE_VERSION.getCode(), AGENT_CODE));

        subjectRegistry.setSubjects(List.of(
            new FakeSubject(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), AGENT_CODE),
            new FakeSubject(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), AGENT_CODE)));
        assertFalse(subjectRegistry.hasSingleExecutor(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), AGENT_CODE),
            "重复注册是配置错误，不该变成一道没人能过的门");
    }

}
