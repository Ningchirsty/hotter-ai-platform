package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 创作草稿大脑：把「本地 LLM 生成视觉方向 / 分镜文案」接到治理层，并且**只回收通过验收的字段**。
 *
 * <p>与 {@link VisualBrainAdapter}（视觉基因抽取）是同一套纪律，差别只在载荷：那边送图，这边送文本。</p>
 *
 * <p><b>为什么不直接调模型</b>：路由、数据等级、外发授权、逐次审计都在治理层（{@code ruoyi-ai-gov}）。
 * 业务侧只提交「能力编码 + 数据等级 + 提示词」，治理层决定走哪个模型、是否允许外发。
 * 能力未注册或没有可用模型时，治理层给出 {@code decision≠MODEL}，本类如实返回未采纳原因，
 * 调用方回落到参数化模板并把来源标成 {@code TEMPLATE}——**绝不把模板产物说成模型产物**。</p>
 *
 * <p><b>外发闸</b>：即使路由命中，若目标是外部部署而任务未授权外部 AI（{@code allow_external≠Y}），
 * 这里直接不发出去（与内容模块同一道闸）。公司内网的 LOCAL 端点不受此限——它本来就不出公司。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class CreativeDraftBrain {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 单次返回的文本字段长度上限（防止模型输出一大段散文塞进卡片）
     */
    private static final int MAX_TEXT = 400;

    private final ObjectProvider<IAigInvokeService> invokeService;

    public CreativeDraftBrain(ObjectProvider<IAigInvokeService> invokeService) {
        this.invokeService = invokeService;
    }

    /**
     * 路由探测结果（dryRun：不审计、不真调模型）。
     *
     * @param routing        是否可路由到模型
     * @param decision       治理层结论（MODEL/MANUAL/DENIED）
     * @param modelKey       目标模型
     * @param deploymentType 部署类型
     * @param reason         不可用原因
     */
    public record Probe(boolean routing, String decision, String modelKey,
                        String deploymentType, String reason) {
    }

    /**
     * 生成结果。
     *
     * @param applied  是否采纳了模型产出
     * @param json     模型产出的 JSON（仅在 applied 时有意义）
     * @param modelKey 模型标识
     * @param traceId  调用链ID（审计可回溯）
     * @param reason   未采纳原因（页面如实展示）
     */
    public record Suggestion(boolean applied, ObjectNode json, String modelKey,
                             String traceId, String reason) {
    }

    /**
     * 探测某能力是否可路由到模型。
     *
     * @param capability 能力编码
     * @param dataLevel  数据等级（PUBLIC/INTERNAL/RESTRICTED）
     * @return 探测结果
     */
    public Probe probe(String capability, String dataLevel) {
        IAigInvokeService service = invokeService.getIfAvailable();
        if (service == null) {
            return new Probe(false, "UNAVAILABLE", null, null, "治理层未启用（IAigInvokeService 不存在）");
        }
        try {
            AigInvokeBo bo = new AigInvokeBo();
            bo.setCapabilityCode(capability);
            bo.setDataLevel(StringUtils.blankToDefault(dataLevel, "INTERNAL"));
            bo.setPrompt("路由探测");
            AigInvokeVo vo = service.dryRun(bo);
            String decision = vo == null ? null : vo.getDecision();
            boolean routing = "MODEL".equalsIgnoreCase(decision);
            return new Probe(routing, decision, vo == null ? null : vo.getModelKey(),
                vo == null ? null : vo.getDeploymentType(), routing ? null : describe(vo));
        } catch (Exception e) {
            log.warn("创作能力路由探测失败 capability={} error={}", capability, e.getMessage());
            return new Probe(false, "ERROR", null, null, "路由探测异常：" + e.getMessage());
        }
    }

    /**
     * 让本地/受管模型产出结构化草稿。
     *
     * @param capability    能力编码（方向或分镜）
     * @param dataLevel     数据等级
     * @param allowExternal 任务是否允许外部 AI
     * @param prompt        提示词（须自带 JSON 字段约定）
     * @param payload       结构化载荷（基因摘要、事实、参数化草稿等）
     * @return 生成结果
     */
    public Suggestion suggest(String capability, String dataLevel, boolean allowExternal,
                              String prompt, Map<String, Object> payload) {
        IAigInvokeService service = invokeService.getIfAvailable();
        if (service == null) {
            return notApplied(null, null, "治理层未启用");
        }
        Probe probe = probe(capability, dataLevel);
        if (!probe.routing()) {
            return notApplied(probe.modelKey(), null,
                "能力 " + capability + " 未路由到可用模型：" + probe.reason());
        }
        boolean external = probe.deploymentType() != null
            && probe.deploymentType().toUpperCase(Locale.ROOT).contains("EXTERNAL");
        if (external && !allowExternal) {
            return notApplied(probe.modelKey(), null,
                "路由命中的是外部模型（" + probe.deploymentType() + "），但该任务未授权外部 AI（allow_external≠Y）");
        }
        try {
            AigInvokeBo bo = new AigInvokeBo();
            bo.setCapabilityCode(capability);
            bo.setDataLevel(StringUtils.blankToDefault(dataLevel, "INTERNAL"));
            bo.setPrompt(prompt);
            bo.setPayload(payload == null ? new LinkedHashMap<>() : payload);
            AigInvokeVo vo = service.invoke(bo);
            if (vo == null || !"MODEL".equalsIgnoreCase(vo.getDecision())) {
                return notApplied(vo == null ? null : vo.getModelKey(), vo == null ? null : vo.getTraceId(),
                    "模型未产出结论：" + describe(vo));
            }
            ObjectNode json = readObject(vo.getOutput());
            if (json == null) {
                return notApplied(vo.getModelKey(), vo.getTraceId(),
                    "模型输出不是合法 JSON 对象，已整体丢弃（原始片段：" + summarize(vo.getOutput()) + "）");
            }
            // 原始输出留一行日志：审计只存摘要，出问题时必须能看见模型到底说了什么
            log.info("创作模型原始输出, capability={}, model={}, length={}, head={}",
                capability, vo.getModelKey(), vo.getOutput() == null ? 0 : vo.getOutput().length(),
                summarize(vo.getOutput()));
            return new Suggestion(true, json, vo.getModelKey(), vo.getTraceId(), null);
        } catch (Exception e) {
            log.warn("创作模型调用失败 capability={} error={}", capability, e.getMessage());
            return notApplied(null, null, "模型调用异常：" + e.getMessage());
        }
    }

    /**
     * 从模型输出里取文本字段并做长度/空白校验。
     *
     * <p>公开（不是 private）：这是「不编造、不塞散文」的执行点，要被单测与各业务服务直接复用。</p>
     *
     * @param node   模型输出的 JSON
     * @param field  字段名
     * @param maxLen 长度上限
     * @return 通过验收的文本；不合格返回 null
     */
    public static String text(JsonNode node, String field, int maxLen) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > maxLen) {
            return null;
        }
        // 模型偶尔会把「null」「N/A」当值返回，这类占位一律视为没有
        String lowered = text.toLowerCase(Locale.ROOT);
        if ("null".equals(lowered) || "n/a".equals(lowered) || "none".equals(lowered)) {
            return null;
        }
        return text;
    }

    /**
     * 取文本字段，限定长度 400。
     *
     * @param node  模型输出
     * @param field 字段名
     * @return 文本或 null
     */
    public static String text(JsonNode node, String field) {
        return text(node, field, MAX_TEXT);
    }

    /**
     * 解析模型输出为 JSON 对象（允许被 Markdown 代码块包裹）。
     *
     * <p>根节点是数组时包一层 {@code items}：小模型偶尔会省掉外层对象，
     * 直接吐一个数组出来。省掉外层不影响内容可信度，没必要因此整次丢弃。</p>
     *
     * @param output 模型输出的原始文本
     * @return JSON 对象；解析不出返回 null
     */
    private static ObjectNode readObject(String output) {
        if (StringUtils.isBlank(output)) {
            return null;
        }
        String text = output.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine > 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode arrayNode) {
                ObjectNode wrapper = MAPPER.createObjectNode();
                wrapper.set("items", arrayNode);
                return wrapper;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 列出模型输出的顶层字段名（诊断用：出问题时一眼看出它到底返回了什么结构）。
     *
     * @param json 模型输出
     * @return 形如 [directions, note]
     */
    public static String fieldNames(ObjectNode json) {
        if (json == null) {
            return "[]";
        }
        List<String> names = new java.util.ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    private static Suggestion notApplied(String modelKey, String traceId, String reason) {
        return new Suggestion(false, null, modelKey, traceId, reason);
    }

    private static String describe(AigInvokeVo vo) {
        if (vo == null) {
            return "治理层无响应";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(vo.getReason())) {
            sb.append(vo.getReason());
        }
        List<String> hits = vo.getPolicyHits();
        if (hits != null && !hits.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(String.join("；", hits));
        }
        return sb.length() == 0 ? StringUtils.blankToDefault(vo.getDecision(), "未知原因") : sb.toString();
    }

    private static String summarize(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

}
