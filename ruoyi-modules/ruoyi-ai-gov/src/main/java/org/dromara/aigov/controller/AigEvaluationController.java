package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.bo.AigEvaluationCaseBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationReviewBo;
import org.dromara.aigov.agent.domain.bo.AigEvaluationRunBo;
import org.dromara.aigov.agent.domain.vo.AigEvaluationCaseVo;
import org.dromara.aigov.agent.domain.vo.AigEvaluationRunVo;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;
import org.dromara.aigov.agent.service.IAigEvaluationService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 黄金用例与评测运行 控制层（设计 §13.2、§5.4 的 GOLDEN_CASE 门槛）。
 *
 * <p><b>详情接口刻意返回实体、列表接口返回裁剪 VO</b>：用例的 {@code expected_json} 与
 * 运行的 {@code score_json} 是 longtext，恰好是「这条用例凭什么判过」的证据本身——
 * 详情页需要原文，列表页不需要（列表返回 VO 以免把长文本塞进响应）。</p>
 *
 * <p>权限分四档：看清单（list）、看详情与证据（query）、定义用例（define）、跑评测（run）、
 * 人工复核（review）。跑评测可能产生外部调用成本、复核是放行链条上的一环，
 * 都与「看看有哪些用例」不是同一件事。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/evaluation")
public class AigEvaluationController {

    private final IAigEvaluationService evaluationService;

    /**
     * 列出黄金用例（裁剪 VO）。
     *
     * @param caseType     用例类型（可空）
     * @param scenarioCode 业务场景（可空）
     * @return 用例清单
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_LIST)
    @GetMapping("/case/list")
    public R<List<AigEvaluationCaseVo>> listCases(@RequestParam(required = false) String caseType,
                                                  @RequestParam(required = false) String scenarioCode) {
        return R.ok(evaluationService.listCaseVos(caseType, scenarioCode));
    }

    /**
     * 取用例详情（含判据原文 {@code expected_json}/{@code rubric_json}）。
     *
     * @param caseId 用例ID
     * @return 用例
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_QUERY)
    @GetMapping("/case/{caseId}")
    public R<AigEvaluationCase> getCase(@NotNull(message = "用例ID不能为空") @PathVariable Long caseId) {
        return R.ok(evaluationService.getCase(caseId));
    }

    /**
     * 定义黄金用例（判据写法在定义期即校验）。
     *
     * @param bo 用例入参
     * @return 用例ID
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_DEFINE)
    @RepeatSubmit
    @PostMapping("/case")
    public R<Long> defineCase(@RequestBody @Validated AigEvaluationCaseBo bo) {
        return R.ok(evaluationService.defineCase(bo));
    }

    /**
     * 列出某个版本的评测运行（裁剪 VO，按时间倒序）。
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 运行行清单
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_LIST)
    @GetMapping("/run/list")
    public R<List<AigEvaluationRunVo>> listRuns(
        @NotBlank(message = "对象类型不能为空") @RequestParam String targetType,
        @NotNull(message = "对象版本ID不能为空") @RequestParam Long targetVersionId) {
        return R.ok(evaluationService.listRunVos(targetType, targetVersionId));
    }

    /**
     * 取运行详情（含打分明细原文 {@code score_json}）。
     *
     * @param runId 运行ID
     * @return 运行行
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_QUERY)
    @GetMapping("/run/{runId}")
    public R<AigEvaluationRun> getRun(@NotNull(message = "运行ID不能为空") @PathVariable Long runId) {
        return R.ok(evaluationService.getRun(runId));
    }

    /**
     * 对某个版本跑一遍完整的黄金用例集合。
     *
     * @param bo 运行入参（用例集合必须与版本声明的集合一致）
     * @return 本次产生的运行行
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_RUN)
    @RepeatSubmit
    @PostMapping("/run")
    public R<List<AigEvaluationRun>> runEvaluation(@RequestBody @Validated AigEvaluationRunBo bo) {
        return R.ok(evaluationService.runEvaluation(bo));
    }

    /**
     * 提交人工复核结论（Rubric 用例的最后一道判断）。
     *
     * @param bo 复核入参
     * @return 空
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_REVIEW)
    @RepeatSubmit
    @PostMapping("/review")
    public R<Void> review(@RequestBody @Validated AigEvaluationReviewBo bo) {
        evaluationService.reviewRun(bo);
        return R.ok();
    }

    /**
     * 取「黄金用例是否通过」的证据（发布门槛 GOLDEN_CASE 的判据来源）。
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 证据结论（含逐用例结论与不满足原因）
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_QUERY)
    @GetMapping("/evidence")
    public R<AigGoldenCaseEvidence> evidence(
        @NotBlank(message = "对象类型不能为空") @RequestParam String targetType,
        @NotNull(message = "对象版本ID不能为空") @RequestParam Long targetVersionId) {
        return R.ok(evaluationService.goldenCaseEvidence(targetType, targetVersionId));
    }

    /**
     * 取版本声明的黄金用例集合。
     *
     * <p><b>为什么页面需要这个接口</b>：跑评测要求「用例集合等于版本声明的集合」，
     * 而声明在版本的 config_json 里（Agent/Skill）或 Manifest 里（Package）——页面读不到那些长文本，
     * 也就无从知道该跑哪几条。没有这个接口，页面只能让用户凭记忆手填，
     * 而手填错的结果是「集合不一致」被服务层拒绝。</p>
     *
     * @param targetType      评测对象类型
     * @param targetVersionId 对象版本ID
     * @return 用例编码清单；{@code null} 表示声明内容读不出来（不是合法 JSON）
     */
    @SaCheckPermission(AigConstants.PERM_EVALUATION_QUERY)
    @GetMapping("/declared-cases")
    public R<List<String>> declaredCases(
        @NotBlank(message = "对象类型不能为空") @RequestParam String targetType,
        @NotNull(message = "对象版本ID不能为空") @RequestParam Long targetVersionId) {
        return R.ok(evaluationService.declaredGoldenCases(targetType, targetVersionId));
    }

}
