package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.IAigEvaluationSubject;
import org.dromara.creative.helper.CreativeImageRuleChecker;
import org.dromara.creative.helper.CreativeQaRules;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉 QA Agent 的评测执行器（设计 §13.2 + §5.2 的 QA 内置 Agent）。
 *
 * <p>交付图确定性规则体检（{@code CreativeImageRuleChecker}）：白底、主体占比、方形、
 * 无 alpha、不贴边——这些是「阈值到了就翻脸」的硬规则，天然适合做可判定判据。</p>
 *
 * <p><b>规则集来自用例快照，不用平台默认</b>：规则写在用例的 {@code rules} 里，评审看用例行就知道
 * 「这次是按什么规则判的」。代价是平台改了默认规则、用例里的规则不会跟着变——这是刻意的：
 * 用例要表达的是「按这套规则，这张图应当判过」，规则随平台漂移会让历史结论失去含义。
 * 用例也可以随时补一条新规则的新用例。</p>
 *
 * <p><b>没配规则不是通过</b>：{@code CreativeQaRules.NONE} 时 checker 判 {@code NOT_CONFIGURED}
 * 且 {@code passed()=false}；本执行器更进一步——快照里没有 {@code rules} 直接报错，
 * 因为「用例忘了写规则」与「这张图没通过」是两件事，不能混成一条 FAIL。</p>
 *
 * <p>输入快照（内联 JSON，图片用 {@code classpath:} 引用随平台发布的快照）：</p>
 * <pre>
 * inline:{"artifact":"classpath:creative/eval/qa-clean-800.png",
 *         "rules":{"schema":"screen-qa/1","square":true,"minSide":800,...}}
 * </pre>
 *
 * <p>成本：本地体检不调模型，成本如实上报为「可知且为 0」。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class CreativeVisualQaEvaluationSubject implements IAigEvaluationSubject {

    /**
     * 本执行器负责的 Agent 编码（与 {@code aig_agent.agent_code} 一致）
     */
    public static final String SUBJECT_CODE = "creative_visual_qa";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String targetType, String subjectCode) {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode().equalsIgnoreCase(targetType)
            && SUBJECT_CODE.equalsIgnoreCase(subjectCode);
    }

    @Override
    public String describe() {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode() + ":" + SUBJECT_CODE
            + "（交付图确定性规则体检）";
    }

    @Override
    public AigEvaluationOutcome execute(AigEvaluationRequest request) {
        long start = System.currentTimeMillis();
        JsonNode snapshot = CreativeEvaluationSnapshots.readInlineJson(request.inputSnapshotRef());
        String artifactRef = CreativeEvaluationSnapshots.requireText(snapshot, "artifact",
            "artifact（候选产物快照引用）");
        JsonNode rulesNode = CreativeEvaluationSnapshots.requireObject(snapshot, "rules",
            "rules（QA 规则集：没写规则时无法判定，不能当成通过）");

        CreativeQaRules rules;
        try {
            rules = CreativeQaRules.parse(rulesNode.toString());
        } catch (Exception e) {
            throw new ServiceException("rules 不是合法的 QA 规则 JSON：" + e.getMessage());
        }
        byte[] bytes = CreativeEvaluationSnapshots.readImage(artifactRef);
        CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(bytes, rules);

        List<Map<String, Object>> findings = new ArrayList<>();
        List<String> failedCodes = new ArrayList<>();
        List<String> hardFailedCodes = new ArrayList<>();
        for (CreativeImageRuleChecker.Finding finding : report.findings()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("code", finding.code());
            node.put("label", finding.label());
            node.put("level", finding.level());
            node.put("ok", finding.ok());
            node.put("detail", finding.detail());
            findings.add(node);
            if (!finding.ok()) {
                failedCodes.add(finding.code());
                if (CreativeQaRules.LEVEL_HARD.equals(finding.level())) {
                    hardFailedCodes.add(finding.code());
                }
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("subject", SUBJECT_CODE);
        output.put("case_code", request.caseCode());
        output.put("version", request.version());
        output.put("deterministic_only", true);
        output.put("artifact_ref", artifactRef);
        // 规则来自用例而不是平台默认：评审要能看出「这次按什么规则判的」
        output.put("rules_source", "case_snapshot");
        output.put("rules_schema", rulesNode.path("schema").isTextual()
            ? rulesNode.path("schema").asText() : null);
        output.put("configured", report.configured());
        output.put("verdict", report.verdict());
        output.put("passed", report.passed());
        output.put("metrics", report.metrics());
        output.put("findings", findings);
        output.put("finding_count", findings.size());
        output.put("failed_codes", failedCodes);
        output.put("failed_count", failedCodes.size());
        output.put("hard_failed_codes", hardFailedCodes);

        String outputJson;
        try {
            outputJson = MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            throw new ServiceException("视觉 QA 评测产出序列化失败：" + e.getMessage());
        }
        long latency = System.currentTimeMillis() - start;
        log.info("视觉 QA 评测执行完成, case={}, artifact={}, verdict={}, 未过项={}",
            request.caseCode(), artifactRef, report.verdict(), failedCodes);
        return new AigEvaluationOutcome(outputJson, true, BigDecimal.ZERO, latency, null, null, false,
            null);
    }

}
