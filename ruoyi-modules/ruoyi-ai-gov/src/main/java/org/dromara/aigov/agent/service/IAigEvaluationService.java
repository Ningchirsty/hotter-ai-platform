package org.dromara.aigov.agent.service;

import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.bo.AigEvaluationCaseBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationReviewBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationRunBo;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;

import java.util.List;

/**
 * 黄金用例与评测运行服务（设计 §13.2、§5.4 的 GOLDEN_CASE 门槛）。
 *
 * <p><b>本服务与发布状态机的关系</b>：{@link #goldenCaseEvidence} 是「黄金用例通过」这道门槛的
 * <b>唯一判据实现</b>。发布推进不会自己去查评测表——两份判据必然走样，一份放行一份拦住，
 * 谁也不知道以哪份为准。</p>
 *
 * @author ai-gov
 */
public interface IAigEvaluationService {

    /**
     * 定义一条黄金用例（设计 §13.2）。
     *
     * <p>判据在<b>定义期</b>就校验：未知判据名、类型写错、空判据对象都当场拒绝。
     * 留到跑评测时才炸的话，一条写错的判据会被当成「跑不过」，于是大家去改 Prompt，
     * 而真正错的是用例。</p>
     *
     * @param bo 用例入参
     * @return 用例ID
     */
    Long defineCase(AigEvaluationCaseBo bo);

    /**
     * 取用例明细（含 {@code expected_json}/{@code rubric_json}）。
     *
     * @param caseId 用例ID
     * @return 用例
     */
    AigEvaluationCase getCase(Long caseId);

    /**
     * 按类型/场景列出用例。
     *
     * @param caseType     用例类型（可空）
     * @param scenarioCode 业务场景（可空）
     * @return 用例清单
     */
    List<AigEvaluationCase> listCases(String caseType, String scenarioCode);

    /**
     * 读取版本声明的黄金用例集合。
     *
     * <p>三类版本的声明位置不同，这是数据模型决定的：Agent/Skill 版本声明在
     * {@code config_json.golden_cases}，Package 版本声明在 Manifest 的
     * {@code golden_cases}（§6.1 最小字段集里就有它，且 Manifest 校验要求它非空）。</p>
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 用例编码清单；{@code null} 表示声明内容读不出来（不是合法 JSON），
     *         空清单表示没声明
     */
    List<String> declaredGoldenCases(String targetType, Long targetVersionId);

    /**
     * 对某个版本跑一遍完整的黄金用例集合（设计 §13.2）。
     *
     * @param bo 运行入参（用例集合必须与版本声明一致）
     * @return 本次产生的运行行（一条用例一行，按入参顺序）
     */
    List<AigEvaluationRun> runEvaluation(AigEvaluationRunBo bo);

    /**
     * 取运行明细。
     *
     * @param runId 运行ID
     * @return 运行行
     */
    AigEvaluationRun getRun(Long runId);

    /**
     * 列出某个版本的评测运行（按时间倒序，便于「最近一次」排在最前）。
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 运行行清单
     */
    List<AigEvaluationRun> listRuns(String targetType, Long targetVersionId);

    /**
     * 提交人工复核结论（Rubric 用例的最后一道判断）。
     *
     * @param bo 复核入参
     */
    void reviewRun(AigEvaluationReviewBo bo);

    /**
     * 计算「黄金用例是否通过」的证据（发布门槛用，也是页面上「还差什么」的来源）。
     *
     * <p>判据：版本声明的<b>每一条</b>用例，其最近一次运行必须 {@code result_status=PASS}，
     * 且人工复核结论必须是 {@code null}（不要求复核）或 {@code PASS}。
     * 待复核（{@code MANUAL}）、失败、出错、从未运行、没有声明集合，都不放行。</p>
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 证据结论
     */
    AigGoldenCaseEvidence goldenCaseEvidence(String targetType, Long targetVersionId);

}
