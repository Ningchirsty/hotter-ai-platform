package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.IAigEvaluationSubject;
import org.dromara.creative.helper.ReferenceImageAnalyzer;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉 DNA Agent 的评测执行器（设计 §13.2 + §5.2 的 VISUAL_DNA 内置 Agent）。
 *
 * <h3>这个执行器只覆盖「参考图确定性实测」这一段</h3>
 * <p>该内置 Agent 的实现由三部分组成（见注册种子 config_json）：DNA 服务编排、模型分析
 * （{@code VisualBrainAdapter}）、参考图实测（{@code ReferenceImageAnalyzer}）。本执行器跑的是
 * <b>确定性实测那一段</b>——它能给出「量出来的值对不对」的确定答案；模型分析那一段的结论只能靠
 * 人工 Rubric 或沙箱试跑，不在本条用例的范围里。产出里显式写
 * {@code model_part_evaluated=false}，避免读者以为这一条用例覆盖了整个 Agent。</p>
 *
 * <p>输入快照（内联 JSON，图片用 {@code classpath:} 引用随平台发布的快照）：</p>
 * <pre>
 * inline:{"reference":"classpath:creative/eval/ref-red-block-200.png"}
 * </pre>
 *
 * <p>成本：本地实测不调模型，成本如实上报为「可知且为 0」。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class CreativeVisualDnaEvaluationSubject implements IAigEvaluationSubject {

    /**
     * 本执行器负责的 Agent 编码（与 {@code aig_agent.agent_code} 一致）
     */
    public static final String SUBJECT_CODE = "creative_visual_dna";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String targetType, String subjectCode) {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode().equalsIgnoreCase(targetType)
            && SUBJECT_CODE.equalsIgnoreCase(subjectCode);
    }

    @Override
    public String describe() {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode() + ":" + SUBJECT_CODE
            + "（参考图确定性实测；模型分析部分不在本用例范围）";
    }

    @Override
    public AigEvaluationOutcome execute(AigEvaluationRequest request) {
        long start = System.currentTimeMillis();
        JsonNode snapshot = CreativeEvaluationSnapshots.readInlineJson(request.inputSnapshotRef());
        String referenceRef = CreativeEvaluationSnapshots.requireText(snapshot, "reference",
            "reference（参考图快照引用）");
        byte[] bytes = CreativeEvaluationSnapshots.readImage(referenceRef);

        ReferenceImageAnalyzer.Analysis analysis = new ReferenceImageAnalyzer().analyze(bytes);
        if (analysis == null) {
            throw new ServiceException("参考图分析返回空：图片可能为空或不可解码（" + referenceRef + "）");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();
        List<String> lowReliabilityFields = new ArrayList<>();
        for (ReferenceImageAnalyzer.Recommendation recommendation : analysis.recommendations()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("field", recommendation.field());
            node.put("value", recommendation.value());
            node.put("basis", recommendation.basis());
            node.put("reliability", recommendation.reliability());
            recommendations.add(node);
            // values 里统一成文本并按点号**嵌套**取值：判据的路径语法（values.colors.primary）
            // 是按点号展开的，若这里写成一个字面键 "colors.primary"，判据就会报「缺失」——
            // 这个错在单测里用字面键断言时看不见，只有拿真实判据求值才会暴露
            putNested(values, recommendation.field(), String.valueOf(recommendation.value()));
            if (!ReferenceImageAnalyzer.RELIABILITY_HIGH.equals(recommendation.reliability())) {
                lowReliabilityFields.add(recommendation.field());
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("subject", SUBJECT_CODE);
        output.put("case_code", request.caseCode());
        output.put("version", request.version());
        output.put("deterministic_only", true);
        output.put("model_part_evaluated", false);
        output.put("reference_ref", referenceRef);
        output.put("width", analysis.width());
        output.put("height", analysis.height());
        output.put("observed_ratio", analysis.observedRatio());
        output.put("values", values);
        output.put("recommendations", recommendations);
        output.put("recommendation_count", recommendations.size());
        output.put("low_reliability_fields", lowReliabilityFields);
        output.put("skipped", analysis.skipped());
        output.put("skipped_count", analysis.skipped().size());
        output.put("notes", analysis.notes());

        String outputJson;
        try {
            outputJson = MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            throw new ServiceException("视觉 DNA 评测产出序列化失败：" + e.getMessage());
        }
        long latency = System.currentTimeMillis() - start;
        log.info("视觉 DNA 评测执行完成, case={}, ref={}, {}x{}, observedRatio={}, 低可信字段={}",
            request.caseCode(), referenceRef, analysis.width(), analysis.height(),
            analysis.observedRatio(), lowReliabilityFields);
        return new AigEvaluationOutcome(outputJson, true, BigDecimal.ZERO, latency, null, null, false,
            null);
    }

    /**
     * 按点号路径写入嵌套 map（{@code colors.primary} → {@code {"colors":{"primary":...}}}）。
     *
     * <p><b>为什么必须嵌套</b>：判据的路径语法是按点号展开的。若这里写成一个字面键
     * {@code "colors.primary"}，判据 {@code values.colors.primary} 就会报「缺失」——
     * 这个错在单测里用字面键断言时看不见（测试自己也会照着错的形状写），只有拿真实判据求值才暴露。</p>
     *
     * <p>路径冲突（既有标量又要往下嵌套）时不覆盖已有值，而是退回字面键并记 warn：
     * 丢数据比键名难看要严重得多。</p>
     *
     * @param target    目标 map
     * @param fieldPath 字段路径
     * @param value     值
     */
    @SuppressWarnings("unchecked")
    private static void putNested(Map<String, Object> target, String fieldPath, Object value) {
        String[] segments = fieldPath.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < segments.length - 1; i++) {
            Object child = current.get(segments[i]);
            if (child == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segments[i], created);
                current = created;
            } else if (child instanceof Map) {
                current = (Map<String, Object>) child;
            } else {
                log.warn("视觉 DNA 字段路径冲突，退回字面键: {}", fieldPath);
                target.put(fieldPath, value);
                return;
            }
        }
        current.put(segments[segments.length - 1], value);
    }

}
