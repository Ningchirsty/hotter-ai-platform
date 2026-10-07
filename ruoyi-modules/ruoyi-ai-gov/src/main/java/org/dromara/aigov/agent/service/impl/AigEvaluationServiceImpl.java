package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigSkill;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.bo.AigEvaluationCaseBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationReviewBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationRunBo;
import org.dromara.aigov.agent.enums.AigEvaluationCaseTypeEnum;
import org.dromara.aigov.agent.enums.AigEvaluationReviewEnum;
import org.dromara.aigov.agent.enums.AigEvaluationStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.AigEvaluationSubjectRegistry;
import org.dromara.aigov.agent.evaluation.AigExpectedRuleCheck;
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
import org.dromara.aigov.agent.service.IAigEvaluationService;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 黄金用例与评测运行实现（设计 §13.2、§5.4 的 GOLDEN_CASE 门槛）。
 *
 * <h3>四处刻意口径</h3>
 * <ol>
 *     <li><b>评测只跑在 SANDBOX_TESTED 的版本上</b>。跑在 DRAFT 上为时过早（结论会被后续改动
 *         作废），跑在 STABLE 上为时已晚（已经放出去了）。这条限制同时也让「评测证据」
 *         与「发布阶段」对得上：证据产生于门槛要求它的那个阶段。</li>
 *     <li><b>用例集合必须等于版本声明的黄金用例集合</b>（不许只跑自选的一两条）：
 *         能挑着跑，就能用最容易过的用例换一个「黄金用例通过」。</li>
 *     <li><b>先写 RUNNING 再写结论</b>（条件更新 {@code where result_status='RUNNING'}）：
 *         崩溃/断电时库里留下的是「跑过但没结论」，而不是什么都没有——「什么都没发生」
 *         与「跑失败了」在排查时是两件事。</li>
 *     <li><b>只写结论，不覆盖历史</b>：每次评测都新增一行运行记录，重跑不会抹掉上一次的失败。
 *         「上次没过、这次过了」本身就是评审要看的信息。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigEvaluationServiceImpl implements IAigEvaluationService {

    /**
     * 备注列宽（{@code remark varchar(500)}）
     */
    private static final int REMARK_MAX = 500;

    /**
     * 错误消息回显上限
     */
    private static final int ERROR_MSG_MAX = 300;

    /**
     * 阻断原因里最多列举的用例数
     */
    private static final int MAX_LISTED_BLOCKERS = 3;

    /**
     * 启用（Y）
     */
    private static final String YES = "Y";

    /**
     * 未启用（N）
     */
    private static final String NO = "N";

    /**
     * 记录状态：正常
     */
    private static final String STATUS_NORMAL = "0";

    private final AigEvaluationCaseMapper caseMapper;

    private final AigEvaluationRunMapper runMapper;

    private final AigAgentMapper agentMapper;

    private final AigSkillMapper skillMapper;

    private final AigPackageMapper packageMapper;

    private final AigAgentVersionMapper agentVersionMapper;

    private final AigSkillVersionMapper skillVersionMapper;

    private final AigPackageVersionMapper packageVersionMapper;

    private final AigEvaluationSubjectRegistry subjectRegistry;

    private final AigExpectedRuleChecker ruleChecker;

    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long defineCase(AigEvaluationCaseBo bo) {
        if (bo == null) {
            throw new ServiceException("用例入参不能为空");
        }
        String code = StringUtils.trim(bo.getCaseCode());
        if (StringUtils.isBlank(code)) {
            throw new ServiceException("用例编码不能为空");
        }
        if (StringUtils.isBlank(bo.getCaseName())) {
            throw new ServiceException("用例名称不能为空");
        }
        AigEvaluationCaseTypeEnum type = AigEvaluationCaseTypeEnum.find(bo.getCaseType());
        if (type == null) {
            throw new ServiceException("未知的用例类型：" + bo.getCaseType()
                + "；可选 " + AigEvaluationCaseTypeEnum.codes());
        }
        String dataLevel = StringUtils.isBlank(bo.getDataLevel())
            ? AigDataLevelEnum.INTERNAL.getCode() : StringUtils.trim(bo.getDataLevel());
        if (AigDataLevelEnum.find(dataLevel) == null) {
            throw new ServiceException("未知的数据等级：" + bo.getDataLevel()
                + "；可选 PUBLIC/INTERNAL/RESTRICTED/STRICT");
        }
        boolean hasRules = StringUtils.isNotBlank(bo.getExpectedJson());
        boolean hasRubric = StringUtils.isNotBlank(bo.getRubricJson());
        if (!hasRules && !hasRubric) {
            throw new ServiceException("用例既没有机器判据（expected_json）也没有人工 Rubric（rubric_json）："
                + "这样的用例无法判定通过与否——它只会让「黄金用例通过」这句结论失去内容");
        }
        List<String> problems = ruleChecker.validateExpectedJson(bo.getExpectedJson());
        if (!problems.isEmpty()) {
            throw new ServiceException("用例判据不可执行：" + String.join("；", problems));
        }
        if (hasRubric && !isJsonObject(bo.getRubricJson())) {
            throw new ServiceException("rubric_json 必须是 JSON 对象");
        }
        checkCostRange(bo.getCostMin(), bo.getCostMax());

        Long exists = caseMapper.selectCount(new LambdaQueryWrapper<AigEvaluationCase>()
            .eq(AigEvaluationCase::getCaseCode, code));
        if (exists != null && exists > 0L) {
            throw new ServiceException("用例编码已存在：" + code);
        }

        AigEvaluationCase entity = new AigEvaluationCase();
        entity.setCaseCode(code);
        entity.setCaseName(StringUtils.substring(bo.getCaseName().trim(), 0, 128));
        entity.setCaseType(type.getCode());
        entity.setScenarioCode(StringUtils.trim(bo.getScenarioCode()));
        entity.setInputSnapshotRef(StringUtils.substring(bo.getInputSnapshotRef(), 0, 500));
        entity.setExpectedJson(bo.getExpectedJson());
        entity.setRubricJson(bo.getRubricJson());
        entity.setCostMin(bo.getCostMin());
        entity.setCostMax(bo.getCostMax());
        entity.setDataLevel(dataLevel);
        entity.setClassification(StringUtils.trim(bo.getClassification()));
        entity.setStatus(STATUS_NORMAL);
        entity.setRemark(StringUtils.substring(bo.getRemark(), 0, REMARK_MAX));
        caseMapper.insert(entity);
        log.info("新增黄金用例, caseId={}, caseCode={}, caseType={}, 机器判据={}, 人工Rubric={}",
            entity.getCaseId(), code, type.getCode(), hasRules, hasRubric);
        return entity.getCaseId();
    }

    @Override
    public AigEvaluationCase getCase(Long caseId) {
        if (caseId == null) {
            throw new ServiceException("用例ID不能为空");
        }
        AigEvaluationCase entity = caseMapper.selectById(caseId);
        if (entity == null) {
            throw new ServiceException("用例不存在：" + caseId);
        }
        return entity;
    }

    @Override
    public List<AigEvaluationCase> listCases(String caseType, String scenarioCode) {
        LambdaQueryWrapper<AigEvaluationCase> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(caseType)) {
            AigEvaluationCaseTypeEnum type = AigEvaluationCaseTypeEnum.find(caseType);
            if (type == null) {
                throw new ServiceException("未知的用例类型：" + caseType
                    + "；可选 " + AigEvaluationCaseTypeEnum.codes());
            }
            wrapper.eq(AigEvaluationCase::getCaseType, type.getCode());
        }
        if (StringUtils.isNotBlank(scenarioCode)) {
            wrapper.eq(AigEvaluationCase::getScenarioCode, StringUtils.trim(scenarioCode));
        }
        wrapper.orderByAsc(AigEvaluationCase::getCaseCode);
        return caseMapper.selectList(wrapper);
    }

    @Override
    public List<String> declaredGoldenCases(String targetType, Long targetVersionId) {
        AigReleaseTargetTypeEnum type = requireType(targetType);
        return loadTarget(type, targetVersionId).goldenCases();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AigEvaluationRun> runEvaluation(AigEvaluationRunBo bo) {
        if (bo == null || bo.getTargetVersionId() == null) {
            throw new ServiceException("评测对象与版本ID不能为空");
        }
        AigReleaseTargetTypeEnum type = requireType(bo.getTargetType());
        TargetVersion target = loadTarget(type, bo.getTargetVersionId());
        if (AigReleaseStatusEnum.find(target.status()) != AigReleaseStatusEnum.SANDBOX_TESTED) {
            throw new ServiceException("评测只对 SANDBOX_TESTED 的版本进行："
                + type.getCode() + " #" + target.id() + " 当前是 " + target.status()
                + "。评测是「进 CANDIDATE」的门槛证据：跑在更早的阶段，结论会被后续改动作废；"
                + "跑在更晚的阶段，版本已经出去了");
        }
        List<String> declared = target.goldenCases();
        if (declared == null) {
            throw new ServiceException("读不出黄金用例集合：" + target.sourceName()
                + " 不是合法 JSON（对象中应有 golden_cases 数组）");
        }
        if (declared.isEmpty()) {
            throw new ServiceException("该版本没有声明黄金用例集合（" + target.sourceName()
                + ".golden_cases）：没有评测集合就无法证明「黄金用例通过」");
        }
        List<String> requested = normalizeCodes(bo.getCaseCodes());
        if (requested.isEmpty()) {
            throw new ServiceException("必须指明要跑的用例，且应与版本声明的黄金用例集合一致："
                + "声明=" + declared);
        }
        if (!new HashSet<>(requested).equals(new HashSet<>(declared))) {
            throw new ServiceException("本次用例集合与版本声明的黄金用例集合不一致：声明=" + declared
                + "、本次=" + requested + "。只跑自选的一两条不能当作整组通过");
        }

        // 执行器先解析一次：它与用例无关，且「平台压根没有这个对象的执行器」属于配置问题，
        // 应当在碰任何用例之前就说清楚（而不是先报「用例不存在」把人引到错的方向）
        IAigEvaluationSubject subject = subjectRegistry.find(type.getCode(), target.subjectCode());

        List<AigEvaluationRun> runs = new ArrayList<>();
        for (String caseCode : requested) {
            AigEvaluationCase evaluationCase = findEnabledCase(caseCode);
            runs.add(runOne(type, target, subject, evaluationCase, bo));
        }
        attachEvaluationRun(type, target.id(), runs.get(runs.size() - 1).getRunId());
        log.info("评测运行完成, target={}#{}, cases={}, statuses={}", type.getCode(), target.id(),
            requested, statusesOf(runs));
        return runs;
    }

    @Override
    public AigEvaluationRun getRun(Long runId) {
        if (runId == null) {
            throw new ServiceException("运行ID不能为空");
        }
        AigEvaluationRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new ServiceException("评测运行不存在：" + runId);
        }
        return run;
    }

    @Override
    public List<AigEvaluationRun> listRuns(String targetType, Long targetVersionId) {
        AigReleaseTargetTypeEnum type = requireType(targetType);
        if (targetVersionId == null) {
            throw new ServiceException("对象版本ID不能为空");
        }
        return runMapper.selectList(new LambdaQueryWrapper<AigEvaluationRun>()
            .eq(AigEvaluationRun::getTargetType, type.getCode())
            .eq(AigEvaluationRun::getTargetVersionId, targetVersionId)
            .orderByDesc(AigEvaluationRun::getOperateTime)
            .orderByDesc(AigEvaluationRun::getRunId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRun(AigEvaluationReviewBo bo) {
        if (bo == null || bo.getRunId() == null) {
            throw new ServiceException("运行ID不能为空");
        }
        AigEvaluationRun run = runMapper.selectById(bo.getRunId());
        if (run == null) {
            throw new ServiceException("评测运行不存在：" + bo.getRunId());
        }
        AigEvaluationStatusEnum status = AigEvaluationStatusEnum.find(run.getResultStatus());
        if (status == null || !status.finished()) {
            throw new ServiceException("该评测运行尚未得出结论（" + run.getResultStatus()
                + "）：没有结论的运行不接受复核");
        }
        AigEvaluationReviewEnum review = AigEvaluationReviewEnum.find(bo.getReviewResult());
        if (review == null) {
            throw new ServiceException("未知的复核结论：" + bo.getReviewResult() + "；只接受 PASS/FAIL");
        }
        if (review == AigEvaluationReviewEnum.MANUAL) {
            throw new ServiceException("MANUAL 是「待人工复核」这个中间状态本身，不能作为复核结论提交："
                + "复核要么给 PASS，要么给 FAIL");
        }
        if (bo.getTotalScore() != null
            && (bo.getTotalScore().compareTo(BigDecimal.ZERO) < 0
                || bo.getTotalScore().compareTo(BigDecimal.valueOf(10000)) >= 0)) {
            throw new ServiceException("人工总分超出可存储范围（0 ~ 9999.99）：" + bo.getTotalScore());
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AigEvaluationRun> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AigEvaluationRun::getRunId, run.getRunId())
            .eq(AigEvaluationRun::getResultStatus, run.getResultStatus())
            .set(AigEvaluationRun::getReviewResult, review.getCode())
            .set(AigEvaluationRun::getReviewerId, bo.getReviewerId())
            .set(AigEvaluationRun::getReviewedAt, now);
        if (bo.getTotalScore() != null) {
            wrapper.set(AigEvaluationRun::getTotalScore, bo.getTotalScore());
        }
        if (StringUtils.isNotBlank(bo.getRemark())) {
            // 追加而不是覆盖：remark 里可能已经有自动判据的失败摘要，那是排查依据
            String merged = StringUtils.isBlank(run.getRemark())
                ? "复核：" + bo.getRemark().trim()
                : run.getRemark() + " ｜复核：" + bo.getRemark().trim();
            wrapper.set(AigEvaluationRun::getRemark, StringUtils.substring(merged, 0, REMARK_MAX));
        }
        int rows = runMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("该评测运行已被并发复核或状态已变化，请刷新后重试：#"
                + run.getRunId());
        }
        log.info("评测人工复核, runId={}, runNo={}, result={}, score={}, reviewerId={}",
            run.getRunId(), run.getRunNo(), review.getCode(), bo.getTotalScore(), bo.getReviewerId());
    }

    @Override
    public AigGoldenCaseEvidence goldenCaseEvidence(String targetType, Long targetVersionId) {
        AigReleaseTargetTypeEnum type = requireType(targetType);
        TargetVersion target = loadTarget(type, targetVersionId);
        List<String> declared = target.goldenCases();
        if (declared == null) {
            return AigGoldenCaseEvidence.blocked(List.of(), Map.of(),
                "读不出黄金用例集合：" + target.sourceName() + " 不是合法 JSON");
        }
        if (declared.isEmpty()) {
            return AigGoldenCaseEvidence.blocked(List.of(), Map.of(),
                "该版本没有声明黄金用例集合（" + target.sourceName() + ".golden_cases）："
                    + "没有评测集合就无法证明「黄金用例通过」");
        }

        Map<String, String> verdicts = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();
        Set<Long> latestRunIds = new LinkedHashSet<>();
        for (String caseCode : declared) {
            AigEvaluationCase evaluationCase = caseMapper.selectOne(new LambdaQueryWrapper<AigEvaluationCase>()
                .eq(AigEvaluationCase::getCaseCode, caseCode));
            if (evaluationCase == null) {
                verdicts.put(caseCode, "NO_CASE");
                blockers.add("用例 " + caseCode + " 不存在（或已删除）");
                continue;
            }
            AigEvaluationRun run = latestRun(type.getCode(), target.id(), evaluationCase.getCaseId());
            // 逻辑删除的那条要单独看一眼：@TableLogic 会让普通查询看不见它，
            // 于是「最近一次」可能回退到更早的 PASS（真库探针实测过）→ 删记录就成了放行的路
            AigEvaluationRun deleted = runMapper.selectNewestDeletedRun(type.getCode(), target.id(),
                evaluationCase.getCaseId());
            boolean deletedIsNewer = deleted != null && run != null && isNewerThan(deleted, run);
            if (run == null) {
                verdicts.put(caseCode, AigGoldenCaseEvidence.VERDICT_NO_RUN);
                blockers.add("用例 " + caseCode + " 还没有任何评测运行"
                    + (deleted == null ? "" : "（注意：该用例有一条被逻辑删除的运行 #" + deleted.getRunId()
                        + "，结论 " + deleted.getResultStatus() + "；删除记录不等于没跑过）"));
                continue;
            }
            latestRunIds.add(run.getRunId());
            String verdict = deletedIsNewer ? AigGoldenCaseEvidence.VERDICT_DELETED_NEWER : verdictOf(run);
            verdicts.put(caseCode, verdict);
            if (deletedIsNewer) {
                blockers.add("用例 " + caseCode + " 有一条比现存最近一次更新的运行被逻辑删除（#"
                    + deleted.getRunId() + " " + deleted.getRunNo() + "，结论 " + deleted.getResultStatus()
                    + "，现存最近一次结论 " + run.getResultStatus() + "）：评测报告是证据，"
                    + "删掉一条更近的结论不能让它变成通过。重跑一次评测即可恢复"
                    + "（新运行会比被删的那条更新）");
            } else if (!AigGoldenCaseEvidence.VERDICT_PASS.equals(verdict)) {
                blockers.add("用例 " + caseCode + " 最近一次运行（" + run.getRunNo() + "）"
                    + blockingText(verdict));
            }
        }

        // 版本上「最近一次评测」的指针与账本不一致 = 库被手工改过（正常路径下两者必然同步，
        // 因为批量评测要么整组成功、要么整体失败，指针写的是本组最后一条运行）
        Long pointer = target.evaluationRunId();
        if (pointer != null && !latestRunIds.contains(pointer)) {
            blockers.add("版本上记录的最近评测（#" + pointer + "）与评测账本里的最新运行不一致："
                + "正常路径下两者由同一次批量评测写入，不一致说明库被手工改动过，请重跑一次评测");
        }

        if (!blockers.isEmpty()) {
            return AigGoldenCaseEvidence.blocked(declared, verdicts, listing(blockers));
        }
        return AigGoldenCaseEvidence.satisfied(declared, verdicts);
    }

    /**
     * 跑一条用例并落结论。
     *
     * @param type          对象类型
     * @param target        目标版本
     * @param subject       评测执行器（已在批次开始时解析）
     * @param evaluationCase 用例
     * @param bo            运行入参
     * @return 运行行（含最终状态）
     */
    private AigEvaluationRun runOne(AigReleaseTargetTypeEnum type, TargetVersion target,
                                    IAigEvaluationSubject subject, AigEvaluationCase evaluationCase,
                                    AigEvaluationRunBo bo) {
        AigEvaluationRun run = new AigEvaluationRun();
        run.setRunNo(newRunNo());
        run.setTargetType(type.getCode());
        run.setTargetVersionId(target.id());
        run.setCaseId(evaluationCase.getCaseId());
        run.setExternalCall(NO);
        run.setResultStatus(AigEvaluationStatusEnum.RUNNING.getCode());
        run.setOperateTime(LocalDateTime.now());
        run.setDelFlag(STATUS_NORMAL);
        if (bo.getOperatorId() != null) {
            run.setCreateBy(bo.getOperatorId());
        }
        run.setRemark(StringUtils.substring(bo.getRemark(), 0, REMARK_MAX));
        runMapper.insert(run);

        AigEvaluationRequest request = new AigEvaluationRequest(type.getCode(), target.subjectCode(),
            target.id(), target.version(), evaluationCase.getCaseCode(), evaluationCase.getCaseType(),
            evaluationCase.getScenarioCode(), evaluationCase.getInputSnapshotRef());

        AigEvaluationOutcome outcome = null;
        AigExpectedRuleCheck check = null;
        String error = null;
        try {
            outcome = subject.execute(request);
            check = ruleChecker.check(evaluationCase, outcome);
        } catch (Exception e) {
            // 记 ERROR 而不是 FAIL：没跑完是评测环境/用例的问题，跑去改 Prompt 是白费功夫；
            // 但门槛对两者一视同仁地不放行
            error = e.getClass().getSimpleName()
                + (StringUtils.isBlank(e.getMessage()) ? "" : "：" + e.getMessage());
            log.warn("评测执行失败, runNo={}, case={}, error={}", run.getRunNo(),
                evaluationCase.getCaseCode(), error);
        }

        if (error != null) {
            return completeRun(run, evaluationCase, null, null, AigEvaluationStatusEnum.ERROR,
                StringUtils.substring("执行失败：" + error, 0, REMARK_MAX));
        }
        AigEvaluationStatusEnum status = check.passed()
            ? AigEvaluationStatusEnum.PASS : AigEvaluationStatusEnum.FAIL;
        return completeRun(run, evaluationCase, outcome, check, status,
            check.failureSummary(REMARK_MAX));
    }

    /**
     * 写回运行结论（条件更新 {@code result_status='RUNNING'}）。
     *
     * @param run            运行行
     * @param evaluationCase 用例
     * @param outcome        执行结果（ERROR 时为 null）
     * @param check          判据结论（ERROR 时为 null）
     * @param status         结论状态
     * @param remark         备注
     * @return 运行行（已填充结论字段）
     */
    private AigEvaluationRun completeRun(AigEvaluationRun run, AigEvaluationCase evaluationCase,
                                         AigEvaluationOutcome outcome, AigExpectedRuleCheck check,
                                         AigEvaluationStatusEnum status, String remark) {
        // 只有「机器判据已通过 + 用例带 Rubric」才进入待人工复核：
        // 机器都没过时不需要人工再确认一遍（人工复核也不能把机器的不通过改成通过）
        boolean awaitingReview = StringUtils.isNotBlank(evaluationCase.getRubricJson())
            && status == AigEvaluationStatusEnum.PASS;
        String reviewResult = awaitingReview ? AigEvaluationReviewEnum.MANUAL.getCode() : null;
        String scoreJson = check == null ? null : toJson(check.detail());

        LambdaUpdateWrapper<AigEvaluationRun> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AigEvaluationRun::getRunId, run.getRunId())
            .eq(AigEvaluationRun::getResultStatus, AigEvaluationStatusEnum.RUNNING.getCode())
            .set(AigEvaluationRun::getResultStatus, status.getCode())
            .set(AigEvaluationRun::getReviewResult, reviewResult)
            .set(AigEvaluationRun::getScoreJson, scoreJson)
            .set(AigEvaluationRun::getRemark, remark)
            .set(AigEvaluationRun::getProviderId, outcome == null ? null : outcome.providerId())
            .set(AigEvaluationRun::getModelCode, outcome == null ? null : outcome.modelCode())
            .set(AigEvaluationRun::getCostAmount, outcome == null ? null : outcome.costAmount())
            .set(AigEvaluationRun::getLatencyMs, outcome == null ? null : outcome.latencyMs())
            .set(AigEvaluationRun::getTraceId, outcome == null ? null : outcome.traceId())
            .set(AigEvaluationRun::getExternalCall,
                outcome != null && outcome.externalCall() ? YES : NO);
        int rows = runMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("评测运行状态已被并发修改，结论未落库：#" + run.getRunId());
        }

        run.setResultStatus(status.getCode());
        run.setReviewResult(reviewResult);
        run.setScoreJson(scoreJson);
        run.setRemark(remark);
        if (outcome != null) {
            run.setProviderId(outcome.providerId());
            run.setModelCode(outcome.modelCode());
            run.setCostAmount(outcome.costAmount());
            run.setLatencyMs(outcome.latencyMs());
            run.setTraceId(outcome.traceId());
            run.setExternalCall(outcome.externalCall() ? YES : NO);
        }
        log.info("评测运行结论, runNo={}, case={}, status={}, 待人工复核={}",
            run.getRunNo(), evaluationCase.getCaseCode(), status.getCode(), awaitingReview);
        return run;
    }

    /**
     * 把「最近一次评测运行」记录到版本上。
     *
     * <p>Skill 版本表没有这一列（设计只给了 Agent/Package 版本），它的证据以评测账本为准；
     * 这不是缺口，而是不做冗余存第二份「最近一次」——两份必然有一天不一致。</p>
     *
     * @param type  对象类型
     * @param id    版本ID
     * @param runId 运行ID
     */
    private void attachEvaluationRun(AigReleaseTargetTypeEnum type, Long id, Long runId) {
        int rows;
        switch (type) {
            case AGENT_VERSION: {
                LambdaUpdateWrapper<AigAgentVersion> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AigAgentVersion::getAgentVersionId, id)
                    .eq(AigAgentVersion::getReleaseStatus, AigReleaseStatusEnum.SANDBOX_TESTED.getCode())
                    .set(AigAgentVersion::getEvaluationRunId, runId);
                rows = agentVersionMapper.update(null, wrapper);
                break;
            }
            case PACKAGE_VERSION: {
                LambdaUpdateWrapper<AigPackageVersion> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AigPackageVersion::getPackageVersionId, id)
                    .eq(AigPackageVersion::getReleaseStatus, AigReleaseStatusEnum.SANDBOX_TESTED.getCode())
                    .set(AigPackageVersion::getEvaluationRunId, runId);
                rows = packageVersionMapper.update(null, wrapper);
                break;
            }
            default:
                // Skill 版本没有 evaluation_run_id 列：不写，也没有需要写的地方
                return;
        }
        if (rows == 0) {
            throw new ServiceException("版本状态已被并发修改，最近一次评测未记录到版本上："
                + type.getCode() + " #" + id);
        }
    }

    /**
     * 取某用例在某对象上的最近一次运行。
     *
     * @param targetType      对象类型
     * @param targetVersionId 版本ID
     * @param caseId          用例ID
     * @return 运行行；没有则返回 null
     */
    private AigEvaluationRun latestRun(String targetType, Long targetVersionId, Long caseId) {
        List<AigEvaluationRun> runs = runMapper.selectList(new LambdaQueryWrapper<AigEvaluationRun>()
            .eq(AigEvaluationRun::getTargetType, targetType)
            .eq(AigEvaluationRun::getTargetVersionId, targetVersionId)
            .eq(AigEvaluationRun::getCaseId, caseId)
            .orderByDesc(AigEvaluationRun::getOperateTime)
            .orderByDesc(AigEvaluationRun::getRunId));
        return runs.isEmpty() ? null : runs.get(0);
    }

    /**
     * 判断某次运行是否比另一次更「新」（与 {@link #latestRun} 的排序口径一致：时间优先、ID 兜底）。
     *
     * @param candidate 待判断
     * @param current   基准
     * @return candidate 更新则 true
     */
    private static boolean isNewerThan(AigEvaluationRun candidate, AigEvaluationRun current) {
        LocalDateTime candidateTime = candidate.getOperateTime();
        LocalDateTime currentTime = current.getOperateTime();
        if (candidateTime != null && currentTime != null) {
            int compared = candidateTime.compareTo(currentTime);
            if (compared != 0) {
                return compared > 0;
            }
        }
        Long candidateId = candidate.getRunId();
        Long currentId = current.getRunId();
        return candidateId != null && currentId != null && candidateId > currentId;
    }

    /**
     * 逐用例结论。
     *
     * @param run 运行行
     * @return 结论编码
     */
    private String verdictOf(AigEvaluationRun run) {
        AigEvaluationStatusEnum status = AigEvaluationStatusEnum.find(run.getResultStatus());
        if (status == null) {
            return "UNKNOWN";
        }
        if (status != AigEvaluationStatusEnum.PASS) {
            return status.getCode();
        }
        AigEvaluationReviewEnum review = AigEvaluationReviewEnum.find(run.getReviewResult());
        if (review == AigEvaluationReviewEnum.MANUAL) {
            return AigGoldenCaseEvidence.VERDICT_REVIEW_MANUAL;
        }
        if (review == AigEvaluationReviewEnum.FAIL) {
            return AigGoldenCaseEvidence.VERDICT_REVIEW_FAIL;
        }
        return AigGoldenCaseEvidence.VERDICT_PASS;
    }

    /**
     * 阻断原因的可读描述。
     *
     * @param verdict 结论编码
     * @return 描述
     */
    private static String blockingText(String verdict) {
        return switch (verdict) {
            case "FAIL" -> "不通过";
            case "ERROR" -> "出错（没跑完）";
            case "RUNNING" -> "还在运行中";
            case "NO_CASE" -> "对应的用例不存在";
            case AigGoldenCaseEvidence.VERDICT_REVIEW_MANUAL -> "仍是「待人工复核」：Rubric 用例的最后判断在人手里，"
                + "平台不会把「还没复核」当成「复核通过」";
            case AigGoldenCaseEvidence.VERDICT_REVIEW_FAIL -> "人工复核不通过";
            case AigGoldenCaseEvidence.VERDICT_DELETED_NEWER ->
                "有一条更新的运行被逻辑删除（评测报告是证据，删除记录不算数）";
            case "UNKNOWN" -> "状态无法识别";
            default -> "结论是 " + verdict;
        };
    }

    /**
     * 找到启用中的用例。
     *
     * @param caseCode 用例编码
     * @return 用例
     */
    private AigEvaluationCase findEnabledCase(String caseCode) {
        AigEvaluationCase evaluationCase = caseMapper.selectOne(new LambdaQueryWrapper<AigEvaluationCase>()
            .eq(AigEvaluationCase::getCaseCode, caseCode));
        if (evaluationCase == null) {
            throw new ServiceException("用例不存在或已删除：" + caseCode);
        }
        if (!STATUS_NORMAL.equals(evaluationCase.getStatus())) {
            throw new ServiceException("用例已停用：" + caseCode);
        }
        return evaluationCase;
    }

    /**
     * 载入目标版本的上下文（编码、状态、黄金用例集合）。
     *
     * @param type 对象类型
     * @param id   版本ID
     * @return 上下文
     */
    private TargetVersion loadTarget(AigReleaseTargetTypeEnum type, Long id) {
        if (id == null) {
            throw new ServiceException("对象版本ID不能为空");
        }
        switch (type) {
            case AGENT_VERSION: {
                AigAgentVersion version = agentVersionMapper.selectById(id);
                if (version == null) {
                    throw new ServiceException("Agent 版本不存在：" + id);
                }
                AigAgent agent = agentMapper.selectById(version.getAgentId());
                if (agent == null) {
                    throw new ServiceException("Agent 定义不存在：" + version.getAgentId());
                }
                return new TargetVersion(id, agent.getAgentCode(), version.getVersion(),
                    version.getReleaseStatus(), version.getEvaluationRunId(),
                    readGoldenCases(version.getConfigJson()), "config_json");
            }
            case SKILL_VERSION: {
                AigSkillVersion version = skillVersionMapper.selectById(id);
                if (version == null) {
                    throw new ServiceException("Skill 版本不存在：" + id);
                }
                AigSkill skill = skillMapper.selectById(version.getSkillId());
                if (skill == null) {
                    throw new ServiceException("Skill 定义不存在：" + version.getSkillId());
                }
                return new TargetVersion(id, skill.getSkillCode(), version.getVersion(),
                    version.getReleaseStatus(), null,
                    readGoldenCases(version.getConfigJson()), "config_json");
            }
            case PACKAGE_VERSION: {
                AigPackageVersion version = packageVersionMapper.selectById(id);
                if (version == null) {
                    throw new ServiceException("Package 版本不存在：" + id);
                }
                AigPackage pkg = packageMapper.selectById(version.getPackageId());
                if (pkg == null) {
                    throw new ServiceException("Package 主记录不存在：" + version.getPackageId());
                }
                // Package 的黄金用例声明在 Manifest 里（§6.1 最小字段集含 golden_cases，
                // 且 Manifest 校验要求它非空）：Package 版本表没有 config_json 列
                return new TargetVersion(id, pkg.getPackageCode(), version.getVersion(),
                    version.getReleaseStatus(), version.getEvaluationRunId(),
                    readGoldenCases(version.getManifestJson()), "manifest_json");
            }
            default:
                throw new ServiceException("未支持的对象类型：" + type.getCode());
        }
    }

    /**
     * 读 {@code golden_cases} 数组。
     *
     * @param json config_json 或 manifest_json
     * @return 用例编码清单；{@code null} 表示不是合法 JSON 对象（读不出来），空清单表示未声明
     */
    private List<String> readGoldenCases(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode array = root.get("golden_cases");
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : array) {
            if (item.isString() && StringUtils.isNotBlank(item.stringValue())) {
                String code = item.stringValue().trim();
                if (seen.add(code)) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /**
     * 对象类型解析（未知即报错，并列出可选值）。
     *
     * @param targetType 类型编码
     * @return 枚举
     */
    private static AigReleaseTargetTypeEnum requireType(String targetType) {
        AigReleaseTargetTypeEnum type = AigReleaseTargetTypeEnum.find(targetType);
        if (type == null) {
            StringBuilder sb = new StringBuilder();
            for (AigReleaseTargetTypeEnum item : AigReleaseTargetTypeEnum.values()) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(item.getCode());
            }
            throw new ServiceException("未知的评测对象类型：" + targetType + "；可选 " + sb);
        }
        return type;
    }

    /**
     * 用例编码归一（去空白、去重、保序）。
     *
     * @param codes 原始清单
     * @return 归一后的清单
     */
    private static List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            String trimmed = code.trim();
            if (seen.add(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    /**
     * 成本范围校验。
     *
     * @param min 下限
     * @param max 上限
     */
    private static void checkCostRange(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("成本下限不能为负：" + min);
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("成本上限不能为负：" + max);
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ServiceException("成本下限大于上限：" + min + " > " + max);
        }
    }

    /**
     * 生成运行编号（对外引用用；不可猜测）。
     *
     * @return 运行编号，形如 {@code EV20261007201530-3F9A2C41}
     */
    private static String newRunNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
            .substring(0, 8).toUpperCase(Locale.ROOT);
        return "EV" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + suffix;
    }

    /**
     * 裁决清单的可读摘要。
     *
     * @param runs 运行行
     * @return 例如 {@code case-a=PASS}
     */
    private static String statusesOf(List<AigEvaluationRun> runs) {
        StringBuilder sb = new StringBuilder();
        for (AigEvaluationRun run : runs) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(run.getRunNo()).append('=').append(run.getResultStatus());
        }
        return sb.toString();
    }

    /**
     * 阻断原因摘要（最多列举若干条，其余概括）。
     *
     * @param blockers 阻断原因
     * @return 摘要
     */
    private static String listing(List<String> blockers) {
        if (blockers.size() <= MAX_LISTED_BLOCKERS) {
            return String.join("；", blockers);
        }
        return String.join("；", blockers.subList(0, MAX_LISTED_BLOCKERS))
            + "；等 " + blockers.size() + " 项不满足";
    }

    /**
     * 是否为 JSON 对象。
     *
     * @param json JSON 文本
     * @return 是则 true
     */
    private boolean isJsonObject(String json) {
        try {
            JsonNode node = jsonMapper.readTree(json);
            return node != null && node.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Map 转 JSON 字符串。
     *
     * @param map 明细
     * @return JSON 文本；转换失败返回 null（明细丢了也不能让运行结论写不进去）
     */
    private String toJson(Map<String, Object> map) {
        try {
            return jsonMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("打分明细序列化失败，已放弃写入 score_json: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 目标版本的上下文。
     *
     * @param id              版本ID
     * @param subjectCode     评测对象编码
     * @param version         版本号
     * @param status          发布状态
     * @param evaluationRunId 版本上记录的最近一次评测（Skill 版本没有该列，为 null）
     * @param goldenCases     声明的黄金用例（null = 读不出来）
     * @param sourceName      声明来源（config_json / manifest_json），用于报错指明去哪查
     */
    private record TargetVersion(Long id, String subjectCode, String version, String status,
                                 Long evaluationRunId, List<String> goldenCases, String sourceName) {
    }

}
