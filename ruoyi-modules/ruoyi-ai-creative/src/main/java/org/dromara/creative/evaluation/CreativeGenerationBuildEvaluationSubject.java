package org.dromara.creative.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigEvaluationOutcome;
import org.dromara.aigov.agent.evaluation.AigEvaluationRequest;
import org.dromara.aigov.agent.evaluation.IAigEvaluationSubject;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成任务构建 Agent 的评测执行器（设计 §13.2 + §5.2 的 GENERATION 内置 Agent）。
 *
 * <h3>这个执行器覆盖的是「草案/prompt 构建」这一段，不覆盖实际提交</h3>
 * <p>该内置 Agent 的实现是「{@code CreativeProductionService} 编排 + {@code ImageTaskSubmissionService}
 * 提交 + {@code DnaPromptBuilder} 派生提示词」。前两者的入口都吃 {@code taskId} 并且<b>真的会提交出图任务</b>
 * （花钱、结果非确定），不适合当黄金用例——黄金用例要的是「同样的输入、同样的结论」。
 * 因此本执行器跑的是其中<b>确定性的那一段</b>：由 Visual DNA + 屏文案 + 差异种子派生出图提示词
 * （这正是"按已确认 Brief 与素材产出生成任务草案"的核心）。产出里显式写
 * {@code submission_not_performed=true} 与 {@code model_part_evaluated=false}，
 * 免得读者以为这一条用例覆盖了整条出图链路。</p>
 *
 * <p><b>实际提交那一段该由谁验</b>：沙箱试跑（§6.3-4）与真机连通性测试——它们才是有代价、
 * 有外部依赖的事情。把提交塞进黄金用例的后果是「每次跑评测都花钱」，那样没人会去跑它。</p>
 *
 * <p>输入快照（内联 JSON）：</p>
 * <pre>
 * inline:{"subject":"鸢尾花香水","screen_hint":"HERO 主图","screen_text":"画面独白…",
 *         "variant_seed":0,"dna":{"colors":{...},"styleKeywords":[...]}}
 * </pre>
 * <p>{@code dna} 可以省略（此时派生走内置兜底风格，不会产出半句话提示词）。</p>
 *
 * @author creative
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeGenerationBuildEvaluationSubject implements IAigEvaluationSubject {

    /**
     * 本执行器负责的 Agent 编码（与 {@code aig_agent.agent_code} 一致）
     */
    public static final String SUBJECT_CODE = "creative_generation_build";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DnaPromptBuilder promptBuilder;

    @Override
    public boolean supports(String targetType, String subjectCode) {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode().equalsIgnoreCase(targetType)
            && SUBJECT_CODE.equalsIgnoreCase(subjectCode);
    }

    @Override
    public String describe() {
        return AigReleaseTargetTypeEnum.AGENT_VERSION.getCode() + ":" + SUBJECT_CODE
            + "（由 DNA 派生提示词/草案；实际提交不在本用例范围）";
    }

    @Override
    public AigEvaluationOutcome execute(AigEvaluationRequest request) {
        long start = System.currentTimeMillis();
        JsonNode snapshot = CreativeEvaluationSnapshots.readInlineJson(request.inputSnapshotRef());
        String subject = CreativeEvaluationSnapshots.requireText(snapshot, "subject",
            "subject（主体/产品名）");
        String screenHint = optionalText(snapshot, "screen_hint");
        String screenText = optionalText(snapshot, "screen_text");
        long variantSeed = optionalSeed(snapshot);

        ObjectNode dna = null;
        JsonNode dnaNode = snapshot.get("dna");
        if (dnaNode != null && !dnaNode.isNull()) {
            if (!dnaNode.isObject()) {
                throw new ServiceException("内联输入快照的 dna 必须是对象");
            }
            dna = (ObjectNode) dnaNode;
        }

        DnaPromptBuilder.Prompt prompt = promptBuilder.build(dna, subject, screenHint, null,
            screenText, null, variantSeed);

        // 可复现自检：同一份输入再派生一遍必须逐字相同（差异只应来自种子）
        String first = canonical(dna, subject, screenHint, screenText, variantSeed);
        String second = canonical(dna, subject, screenHint, screenText, variantSeed);
        boolean reproducible = first.equals(second);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("subject", SUBJECT_CODE);
        output.put("case_code", request.caseCode());
        output.put("version", request.version());
        output.put("deterministic_only", true);
        output.put("model_part_evaluated", false);
        output.put("submission_not_performed", true);
        output.put("dna_provided", dna != null);
        output.put("variant_seed", variantSeed);
        output.put("screen_hint", screenHint);
        output.put("prompt", prompt.prompt());
        output.put("negative_prompt", prompt.negativePrompt());
        output.put("prompt_length", prompt.prompt() == null ? 0 : prompt.prompt().length());
        output.put("negative_prompt_length",
            prompt.negativePrompt() == null ? 0 : prompt.negativePrompt().length());
        output.put("has_negative_prompt",
            prompt.negativePrompt() != null && !prompt.negativePrompt().isBlank());
        output.put("applied", prompt.applied());
        output.put("applied_count", prompt.applied() == null ? 0 : prompt.applied().size());
        output.put("omitted", prompt.omitted());
        output.put("omitted_count", prompt.omitted() == null ? 0 : prompt.omitted().size());
        output.put("reproducible_probe", reproducible);

        String outputJson;
        try {
            outputJson = MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            throw new ServiceException("生成任务构建评测产出序列化失败：" + e.getMessage());
        }
        long latency = System.currentTimeMillis() - start;
        log.info("生成任务构建评测执行完成, case={}, seed={}, promptLen={}, applied={}, omitted={}",
            request.caseCode(), variantSeed, output.get("prompt_length"), output.get("applied_count"),
            output.get("omitted_count"));
        return new AigEvaluationOutcome(outputJson, true, BigDecimal.ZERO, latency, null, null, false,
            null);
    }

    /**
     * 派生一次并序列化（供可复现自检使用）。
     *
     * @param dna        DNA
     * @param subject    主体
     * @param screenHint 画面用途
     * @param screenText 屏文案
     * @param seed       差异种子
     * @return 序列化文本
     */
    private String canonical(ObjectNode dna, String subject, String screenHint, String screenText,
                             long seed) {
        DnaPromptBuilder.Prompt prompt = promptBuilder.build(dna, subject, screenHint, null,
            screenText, null, seed);
        List<Object> parts = new ArrayList<>();
        parts.add(prompt.prompt());
        parts.add(prompt.negativePrompt());
        parts.add(prompt.applied());
        try {
            return MAPPER.writeValueAsString(parts);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 取可空文本字段。
     *
     * @param snapshot 快照
     * @param key      字段
     * @return 文本；缺失/非文本/空白返回 null
     */
    private static String optionalText(JsonNode snapshot, String key) {
        JsonNode node = snapshot.get(key);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }

    /**
     * 取差异种子（缺失默认 0；给了非数字就报错，不猜）。
     *
     * @param snapshot 快照
     * @return 种子
     */
    private static long optionalSeed(JsonNode snapshot) {
        JsonNode node = snapshot.get("variant_seed");
        if (node == null || node.isNull()) {
            return 0L;
        }
        if (!node.isNumber()) {
            throw new ServiceException("variant_seed 必须是数字");
        }
        return node.asLong();
    }

}
