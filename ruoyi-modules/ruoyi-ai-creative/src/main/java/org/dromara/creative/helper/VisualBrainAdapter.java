package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.service.invoker.ModelImagePayload;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视觉大脑适配器：把「参考图 + 已有基因种子」交给治理层里的视觉模型分析，只回收可信字段。
 *
 * <p><b>为什么不直接写调用、也不自己决定用哪个模型</b>：治理层（{@code ruoyi-ai-gov}）
 * 负责路由、数据等级、外部调用授权与逐次审计。业务侧只提交「能力编码 + 数据等级 + 载荷」，
 * 由治理层决定走本地还是外部模型。这也意味着：<b>能力编码必须在治理台注册并配好路由策略</b>，
 * 否则调用会被明确拒绝（{@code decision=DENIED}），不存在静默回落。</p>
 *
 * <p><b>没有可用模型时怎么办</b>：如实回落。先用 {@code dryRun} 探测路由（不产生审计、不调模型），
 * 只有能路由到模型才真正 {@code invoke}；否则返回「未启用」的原因，让调用方把基因来源标成
 * 「由已确认事实推导」。<b>绝不把默认值包装成模型结论，也绝不在模型输出不可信时凑数。</b></p>
 *
 * <p><b>逐字段验收</b>：模型返回的每个字段都要过 {@link #accept} 的格式校验（色值必须合法、
 * 枚举必须在集合内、数值必须在区间内），通过才采纳；被丢弃的字段会记进原因里。
 * 模型胡说一个不存在的枚举值，不该污染已经锁定过的视觉规范。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class VisualBrainAdapter {

    /**
     * 默认能力编码（治理台需注册同名能力 + 路由策略 + 模型绑定才能生效）。
     * 可用配置 {@code creative.dna.capability-code} 覆盖，避免改代码。
     */
    @Value("${creative.dna.capability-code:visual_dna_extract}")
    private String capabilityCode;

    /**
     * 单张图上限，与治理层 {@link ModelImagePayload#MAX_BYTES_PER_IMAGE} 保持一致
     */
    private static final long MAX_IMAGE_BYTES = ModelImagePayload.MAX_BYTES_PER_IMAGE;

    private final ObjectProvider<IAigInvokeService> invokeService;

    public VisualBrainAdapter(ObjectProvider<IAigInvokeService> invokeService) {
        this.invokeService = invokeService;
    }

    /**
     * 路由探测结果。
     *
     * @param routing        是否可路由到模型（decision=MODEL）
     * @param decision       治理层结论（MODEL/MANUAL/DENIED）
     * @param modelKey       目标模型标识
     * @param deploymentType 部署类型（LOCAL/EXTERNAL_API…）
     * @param reason         不可用原因（policyHits 摘要）
     */
    public record Probe(boolean routing, String decision, String modelKey,
                        String deploymentType, String reason) {
    }

    /**
     * 分析结果。
     *
     * @param applied  是否采纳了模型结论
     * @param patch    可采纳字段（已逐项校验）
     * @param modelKey 模型标识
     * @param traceId  治理层调用链ID（可回溯审计）
     * @param reason   未采纳/部分采纳的原因（页面如实展示）
     * @param fields   实际采纳的字段名
     */
    public record Analysis(boolean applied, ObjectNode patch, String modelKey, String traceId,
                           String reason, List<String> fields) {
    }

    /**
     * 探测视觉模型是否可用（dryRun：不审计、不调用模型）。
     *
     * @param dataLevel 数据等级（PUBLIC/INTERNAL/RESTRICTED）
     * @return 探测结果
     */
    public Probe probe(String dataLevel) {
        IAigInvokeService service = invokeService.getIfAvailable();
        if (service == null) {
            return new Probe(false, "UNAVAILABLE", null, null, "治理层未启用（IAigInvokeService 不存在）");
        }
        try {
            AigInvokeBo bo = new AigInvokeBo();
            bo.setCapabilityCode(capabilityCode);
            bo.setDataLevel(StringUtils.blankToDefault(dataLevel, "INTERNAL"));
            bo.setPrompt("路由探测");
            AigInvokeVo vo = service.dryRun(bo);
            String decision = vo == null ? null : vo.getDecision();
            boolean routing = "MODEL".equalsIgnoreCase(decision);
            String reason = routing ? null : describe(vo);
            return new Probe(routing, decision, vo == null ? null : vo.getModelKey(),
                vo == null ? null : vo.getDeploymentType(), reason);
        } catch (Exception e) {
            log.warn("视觉模型路由探测失败：{}", e.getMessage());
            return new Probe(false, "ERROR", null, null, "路由探测异常：" + e.getMessage());
        }
    }

    /**
     * 让视觉模型分析参考图，返回「可采纳的字段」。
     *
     * @param dataLevel       数据等级
     * @param seed            已有基因种子（作为模型的对齐基准）
     * @param referenceBytes  参考图字节（可空＝无图，直接返回未采纳）
     * @param mimeType        参考图 MIME
     * @param subject         主体（产品名）
     * @param allowExternal   任务是否允许外部 AI（cp_task.allow_external）
     * @return 分析结果
     */
    public Analysis analyze(String dataLevel, ObjectNode seed, byte[] referenceBytes,
                            String mimeType, String subject, boolean allowExternal) {
        IAigInvokeService service = invokeService.getIfAvailable();
        if (service == null) {
            return notApplied(null, null, "治理层未启用");
        }
        if (referenceBytes == null || referenceBytes.length == 0) {
            return notApplied(null, null, "没有可分析的参考图");
        }
        if (referenceBytes.length > MAX_IMAGE_BYTES) {
            return notApplied(null, null, "参考图超过 " + (MAX_IMAGE_BYTES / 1024 / 1024) + "MB，无法用于在线分析");
        }

        Probe probe = probe(dataLevel);
        if (!probe.routing()) {
            return notApplied(probe.modelKey(), null, "治理层未路由到可用模型：" + probe.reason());
        }
        boolean external = probe.deploymentType() != null
            && probe.deploymentType().toUpperCase(Locale.ROOT).contains("EXTERNAL");
        if (external && !allowExternal) {
            // 与内容模块同一道闸：任务未授权外部 AI 时，即便路由选了外部模型也不发出去
            return notApplied(probe.modelKey(), null,
                "路由命中的是外部模型（" + probe.deploymentType() + "），但该任务未授权外部 AI（allow_external≠Y）");
        }

        try {
            AigInvokeBo bo = new AigInvokeBo();
            bo.setCapabilityCode(capabilityCode);
            bo.setDataLevel(StringUtils.blankToDefault(dataLevel, "INTERNAL"));
            bo.setPrompt(buildPrompt(subject));
            bo.setPayload(buildPayload(referenceBytes, mimeType, seed));
            AigInvokeVo vo = service.invoke(bo);
            if (vo == null || !"MODEL".equalsIgnoreCase(vo.getDecision())) {
                return notApplied(vo == null ? null : vo.getModelKey(), vo == null ? null : vo.getTraceId(),
                    "模型未产出结论：" + describe(vo));
            }
            if (StringUtils.isBlank(vo.getOutput())) {
                return notApplied(vo.getModelKey(), vo.getTraceId(), "模型返回为空");
            }
            return accept(vo.getOutput(), vo.getModelKey(), vo.getTraceId());
        } catch (Exception e) {
            log.warn("视觉模型分析失败：{}", e.getMessage());
            return notApplied(null, null, "模型调用异常：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 逐字段验收模型输出。
     *
     * <p>包级可见（不是 private）：这段逻辑是「不编造」的执行点，必须能被单测直接钉住，
     * 而不是只能通过真实模型调用间接验证。</p>
     *
     * @param output   模型返回的 json 文本
     * @param modelKey 模型标识
     * @param traceId  调用链ID
     * @return 验收结果
     */
    Analysis accept(String output, String modelKey, String traceId) {
        ObjectNode patch = VisualDnaSchema.empty();
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        ObjectNode root = VisualDnaSchema.readTreeOrNull(output);
        if (root == null) {
            return notApplied(modelKey, traceId, "模型输出不是合法 JSON 对象，已整体丢弃");
        }

        // 关键词：字符串数组，长度与单项长度都设限（模型容易一次给一大串形容词）
        copyKeywords(root.path("styleKeywords"), patch.withArray("styleKeywords"), accepted, rejected,
            "styleKeywords", 12);
        copyKeywords(root.path("avoidKeywords"), patch.withArray("avoidKeywords"), accepted, rejected,
            "avoidKeywords", 20);

        ObjectNode colors = patch.withObject("/colors");
        copyColor(root.path("colors"), "primary", colors, accepted, rejected);
        copyColor(root.path("colors"), "secondary", colors, accepted, rejected);
        copyColor(root.path("colors"), "accent", colors, accepted, rejected);
        copyColor(root.path("colors"), "background", colors, accepted, rejected);

        copyLevel(root, "saturation", patch, accepted, rejected);
        copyLevel(root, "contrastLevel", patch, accepted, rejected);
        copyLevel(root, "whitespaceLevel", patch, accepted, rejected);

        ObjectNode lighting = patch.withObject("/lighting");
        copyEnum(root.path("lighting"), "type", List.of("SOFT", "HARD", "STUDIO", "NATURAL"),
            lighting, accepted, rejected);
        copyEnum(root.path("lighting"), "direction", List.of("FRONT", "SIDE", "TOP", "BACK"),
            lighting, accepted, rejected);

        JsonNode ratio = root.path("productRatio");
        Integer min = validRatio(ratio.path("min"));
        Integer max = validRatio(ratio.path("max"));
        if (min != null && max != null && min > max) {
            rejected.add("productRatio（区间颠倒）");
            min = null;
            max = null;
        }
        if (min != null || max != null) {
            VisualDnaSchema.setProductRatio(patch, min, max);
            accepted.add("productRatio");
        } else if (ratio.isObject()) {
            rejected.add("productRatio");
        }

        copyText(root, "sceneType", 64, patch, accepted, rejected);
        copyText(root, "typographyStyle", 64, patch, accepted, rejected);

        // 期望的字段一个都没通过 → 视为不可用（避免「模型返回了但全是胡说」被静默采纳）
        boolean corePresent = accepted.stream().anyMatch(f ->
            f.startsWith("colors") || "styleKeywords".equals(f) || "sceneType".equals(f));
        if (!corePresent) {
            return notApplied(modelKey, traceId,
                "模型输出没有可采纳的核心字段（被丢弃：" + String.join("、", rejected) + "）");
        }

        String reason = rejected.isEmpty() ? null
            : "以下字段格式不可信已丢弃：" + String.join("、", rejected);
        return new Analysis(true, patch, modelKey, traceId, reason, accepted);
    }

    private static void copyKeywords(JsonNode source, ArrayNode target, List<String> accepted,
                                     List<String> rejected, String label, int limit) {
        if (!source.isArray()) {
            if (!source.isMissingNode()) {
                rejected.add(label);
            }
            return;
        }
        int count = 0;
        for (JsonNode item : source) {
            String text = item.asText("").trim();
            if (text.isEmpty() || text.length() > 12) {
                continue;
            }
            if (count >= limit) {
                break;
            }
            target.add(text);
            count++;
        }
        if (count > 0) {
            accepted.add(label);
        } else {
            rejected.add(label);
        }
    }

    private static void copyColor(JsonNode colors, String field, ObjectNode target,
                                  List<String> accepted, List<String> rejected) {
        String value = colors.path(field).asText(null);
        if (StringUtils.isBlank(value)) {
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^#([0-9A-F]{6}|[0-9A-F]{3})$")) {
            rejected.add("colors." + field);
            return;
        }
        target.put(field, normalized);
        accepted.add("colors." + field);
    }

    private static void copyLevel(JsonNode root, String field, ObjectNode patch,
                                  List<String> accepted, List<String> rejected) {
        copyEnum(root, field, List.of("LOW", "MEDIUM", "HIGH"), patch, accepted, rejected);
    }

    private static void copyEnum(JsonNode parent, String field, List<String> allowed,
                                 ObjectNode patch, List<String> accepted, List<String> rejected) {
        String value = parent.path(field).asText(null);
        if (StringUtils.isBlank(value)) {
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            rejected.add(field);
            return;
        }
        patch.put(field, normalized);
        accepted.add(field);
    }

    private static void copyText(JsonNode root, String field, int maxLength, ObjectNode patch,
                                 List<String> accepted, List<String> rejected) {
        String value = root.path(field).asText(null);
        if (StringUtils.isBlank(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            rejected.add(field);
            return;
        }
        patch.put(field, trimmed);
        accepted.add(field);
    }

    private static Integer validRatio(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        int value = node.asInt();
        return value < 0 || value > 100 ? null : value;
    }

    private Map<String, Object> buildPayload(byte[] imageBytes, String mimeType, ObjectNode seed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("label", "参考图");
        part.put("mimeType", StringUtils.blankToDefault(mimeType, "image/png"));
        part.put("base64", Base64.getEncoder().encodeToString(imageBytes));
        payload.put(ModelImagePayload.KEY, List.of(part));
        payload.put("seedDna", VisualDnaSchema.toJson(seed));
        payload.put("task", "请基于参考图补全/修正视觉基因，只返回 JSON，字段见提示词");
        return payload;
    }

    private static String buildPrompt(String subject) {
        return "你是电商详情页视觉规范分析助手。参考图是产品图，主体为「"
            + StringUtils.blankToDefault(subject, "当前产品")
            + "」。请只输出一个 JSON 对象，可包含以下字段（不确定就不要输出该字段，不要编造）："
            + "styleKeywords（字符串数组，最多 12 项，每项不超过 12 字）、"
            + "avoidKeywords（字符串数组）、"
            + "colors{primary,secondary,accent,background}（#RRGGBB）、"
            + "saturation/contrastLevel/whitespaceLevel（LOW|MEDIUM|HIGH）、"
            + "lighting{type:SOFT|HARD|STUDIO|NATURAL, direction:FRONT|SIDE|TOP|BACK}、"
            + "productRatio{min,max}（0~100 整数）、"
            + "sceneType、typographyStyle。"
            + "只描述画面本身的视觉特征，不要输出营销文案。";
    }

    private static Analysis notApplied(String modelKey, String traceId, String reason) {
        return new Analysis(false, VisualDnaSchema.empty(), modelKey, traceId, reason, List.of());
    }

    private static String describe(AigInvokeVo vo) {
        if (vo == null) {
            return "治理层无响应";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(vo.getReason())) {
            sb.append(vo.getReason());
        }
        if (vo.getPolicyHits() != null && !vo.getPolicyHits().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(String.join("；", vo.getPolicyHits()));
        }
        return sb.length() == 0 ? StringUtils.blankToDefault(vo.getDecision(), "未知原因") : sb.toString();
    }

}
