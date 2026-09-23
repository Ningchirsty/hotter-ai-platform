package org.dromara.content.invoker;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.service.invoker.ModelImagePayload;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.helper.ContentDocumentExtractor;
import org.dromara.content.helper.ContentFieldExtractor;
import org.dromara.content.helper.ContentImageInspector;
import org.dromara.content.helper.ContentPrecheckEngine;
import org.dromara.content.helper.ExtractedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容生产本地调用器：治理层的 {@code LOCAL} 执行者，负责
 * {@code document_parse}（资料解析）与 {@code brief_precheck}（资料预检）两个能力。
 *
 * <p><b>为什么放在内容模块而不是 aigov</b>：治理层不应该知道「Excel 怎么读」这类业务知识，
 * 且依赖方向是 {@code content → ai-gov}（单向）。本类实现 aigov 的 {@link ModelInvoker} SPI，
 * 通过 {@link #supportsCapability(String)} 声明归属的能力编码，由路由按能力精确分派——
 * 这样本地调用器之间不会互相抢能力，aigov 也不必为每种业务能力堆 if-else。</p>
 *
 * <p><b>全程进程内、不出网</b>：POI/PDFBox + 规则。设计文档 §9.3 要求「限制」级数据
 * 仅本地处理，本调用器满足该约束，且审计里 {@code external_call} 恒为 {@code N}。</p>
 *
 * <p><b>红线</b>：解析产出一律是「候选」，输出里带 {@code pendingConfirm} 明确提示
 * 需人工确认；预检只呈现冲突证据，不判定哪个取值正确。</p>
 *
 * @author content
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentLocalInvoker implements ModelInvoker {

    /**
     * 资料解析器
     */
    private final ContentDocumentExtractor documentExtractor;

    /**
     * 候选字段抽取器
     */
    private final ContentFieldExtractor fieldExtractor;

    /**
     * 预检引擎
     */
    private final ContentPrecheckEngine precheckEngine;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        return deploymentType == AigDeploymentTypeEnum.LOCAL;
    }

    @Override
    public boolean supportsCapability(String capabilityCode) {
        return ContentConstants.CAP_DOCUMENT_PARSE.equals(capabilityCode)
            || ContentConstants.CAP_BRIEF_PRECHECK.equals(capabilityCode)
            || ContentConstants.CAP_DELIVERABLE_CONSISTENCY.equals(capabilityCode);
    }

    @Override
    public boolean available() {
        // 纯本地计算，无外部依赖
        return true;
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        if (request == null || !supportsCapability(request.getCapabilityCode())) {
            return ModelInvokeResult.failure("内容本地调用器不处理该能力", 0L);
        }
        long start = System.currentTimeMillis();
        try {
            if (ContentConstants.CAP_DOCUMENT_PARSE.equals(request.getCapabilityCode())) {
                return parseDocument(request, start);
            }
            if (ContentConstants.CAP_DELIVERABLE_CONSISTENCY.equals(request.getCapabilityCode())) {
                return checkConsistency(request, start);
            }
            return precheck(request, start);
        } catch (Exception e) {
            // SPI 约定：不向外抛异常
            log.error("内容本地调用异常, capabilityCode={}", request.getCapabilityCode(), e);
            return ModelInvokeResult.failure("内容本地调用异常：" + e.getClass().getSimpleName(),
                System.currentTimeMillis() - start);
        }
    }

    /**
     * 资料解析：把文件读成候选字段。
     *
     * @param request 调用请求（payload 需含 fileBytes / fileExt / fileName）
     * @param start   起始时间
     * @return 调用结果
     */
    private ModelInvokeResult parseDocument(ModelInvokeRequest request, long start) {
        Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();
        Object bytesObj = payload.get("fileBytes");
        if (!(bytesObj instanceof byte[] bytes)) {
            return ModelInvokeResult.failure("解析入参缺少文件内容", System.currentTimeMillis() - start);
        }
        String ext = str(payload.get("fileExt"));
        ExtractedDocument doc = documentExtractor.extract(bytes, ext);

        Map<String, Object> out = new LinkedHashMap<>();
        if (!doc.isExtracted()) {
            // 不支持或失败：返回空候选 + 明确原因，由调用方落为「已跳过」并展示给用户
            out.put("candidates", new ArrayList<>());
            out.put("fieldCount", 0);
            out.put("extracted", false);
            out.put("skipReason", StringUtils.blankToDefault(doc.getSkipReason(), "未能解析该文件"));
            out.put("pendingConfirm", List.of(StringUtils.blankToDefault(doc.getSkipReason(), "未能解析该文件")));
            return ModelInvokeResult.success(JsonUtils.toJsonString(out), System.currentTimeMillis() - start);
        }
        List<ContentFieldExtractor.Candidate> candidates = fieldExtractor.extract(doc);
        out.put("candidates", candidates);
        out.put("fieldCount", candidates.size());
        out.put("extracted", true);
        out.put("skipReason", null);
        List<String> pending = new ArrayList<>();
        if (candidates.isEmpty()) {
            pending.add("未从该文件识别到已知事实字段，请人工核对或补充录入");
        } else {
            pending.add("解析结果均为候选值，须人工确认后才可作为产品事实");
        }
        out.put("pendingConfirm", pending);
        log.info("资料解析完成, ext={}, size={}, pairCount={}, candidateCount={}",
            ext, bytes.length, doc.getPairs().size(), candidates.size());
        return ModelInvokeResult.success(JsonUtils.toJsonString(out), System.currentTimeMillis() - start);
    }

    /**
     * 资料预检：冲突与缺失检测。
     *
     * @param request 调用请求（payload 需含 candidates / gateRules）
     * @param start   起始时间
     * @return 调用结果
     */
    @SuppressWarnings("unchecked")
    private ModelInvokeResult precheck(ModelInvokeRequest request, long start) {
        Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();
        List<Map<String, Object>> candidates = asMapList(payload.get("candidates"));
        List<Map<String, Object>> rules = asMapList(payload.get("gateRules"));
        ContentPrecheckEngine.Result r = precheckEngine.precheck(candidates, rules);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conflicts", r.getConflicts());
        out.put("missings", r.getMissings());
        out.put("pendingConfirm", r.getPendingConfirm());
        log.info("资料预检完成, candidateCount={}, ruleCount={}, conflictCount={}, missingCount={}",
            candidates.size(), rules.size(), r.getConflicts().size(), r.getMissings().size());
        return ModelInvokeResult.success(JsonUtils.toJsonString(out), System.currentTimeMillis() - start);
    }

    /**
     * 成品一致性检查：本地确定性比对（参考图 vs 成品图）。
     *
     * <p><b>约定 {@code images[0]} 是参考图、{@code images[1]} 是成品图</b>。标签不符时直接失败，
     * 不靠顺序猜：两张图说反了，结论就完全说反了，而且从结果上根本看不出来。</p>
     *
     * <p>本方法只做不需要模型也能确凿测出的部分（画布几何 + 归一化网格上的
     * <b>颜色与版面结构</b>差异），语义判断（画面里的文字内容、Logo 是否被改）
     * 交给视觉模型或人工——摘要里会明确写出这个边界，不让「一致」这两个字被过度解读。</p>
     *
     * <p>比对对象始终是<b>参考图与成品图两张独立的图</b>：两张图各自采样成网格签名后
     * 逐格相对比较，不存在拼接、叠加或合成。</p>
     *
     * @param request 调用请求（payload 需含 images）
     * @param start   起始时间
     * @return 调用结果
     */
    private ModelInvokeResult checkConsistency(ModelInvokeRequest request, long start) {
        Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();
        List<ModelImagePayload.ImagePart> images = ModelImagePayload.extract(payload);
        if (images.size() < 2) {
            return ModelInvokeResult.failure("成品一致性检查需要「参考图 + 成品图」共两张，实际收到 "
                + images.size() + " 张", System.currentTimeMillis() - start);
        }
        ModelImagePayload.ImagePart reference = images.get(0);
        ModelImagePayload.ImagePart result = images.get(1);
        if (!ContentConstants.IMAGE_LABEL_REFERENCE.equals(reference.label())
            || !ContentConstants.IMAGE_LABEL_RESULT.equals(result.label())) {
            return ModelInvokeResult.failure("图片标签不符合约定：第 1 张须为「"
                + ContentConstants.IMAGE_LABEL_REFERENCE + "」、第 2 张须为「"
                + ContentConstants.IMAGE_LABEL_RESULT + "」，实际为「" + reference.label()
                + "」「" + result.label() + "」", System.currentTimeMillis() - start);
        }

        byte[] referenceBytes = decodeImage(reference);
        byte[] resultBytes = decodeImage(result);
        if (referenceBytes == null || resultBytes == null) {
            return ModelInvokeResult.failure("图片 base64 解码失败", System.currentTimeMillis() - start);
        }

        ContentImageInspector.Comparison comparison = ContentImageInspector.compare(referenceBytes, resultBytes);
        String verdict = ContentImageInspector.localVerdict(comparison);
        String summary = ContentImageInspector.localSummary(comparison, verdict);

        List<Map<String, Object>> findings = new ArrayList<>();
        for (String note : comparison.notes()) {
            findings.add(finding("结构比对", comparison.comparable() ? "INFO" : "WARN", note));
        }
        // 差异方位单独列一条：验收时最想知道的就是「哪里不一样」，
        // 混在说明性 notes 里会被读过去
        if (comparison.diffZones() != null && !comparison.diffZones().isEmpty()) {
            findings.add(finding("差异方位",
                "INCONSISTENT".equals(verdict) ? "ERROR" : "WARN",
                "差异最集中的区域：" + String.join("、", comparison.diffZones())));
        }
        if (!comparison.comparable()) {
            findings.add(finding("版面关系", "WARN",
                "两图不可直接做像素级比对，本次未给出相似度分值，请以视觉模型或人工复核为准"));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", verdict);
        // score 只在真的算出来时才写：输出模板不强制该字段，编造一个分值比不给更糟
        if (comparison.similarity() != null) {
            out.put("score", Math.round(comparison.similarity() * 100d) / 100d);
        }
        out.put("summary", summary);
        out.put("findings", findings);
        out.put("pendingConfirm", List.of(
            "本地结构化比对覆盖颜色、纹理与版面结构，不含画面内文字内容与 Logo 语义判断，"
                + "该部分需人工或视觉模型确认"));
        log.info("成品一致性本地比对完成, verdict={}, comparable={}, mode={}, similarity={}, channelDiff={}",
            verdict, comparison.comparable(), comparison.compareMode(),
            comparison.similarity(), comparison.meanChannelDiff());
        return ModelInvokeResult.success(JsonUtils.toJsonString(out), System.currentTimeMillis() - start);
    }

    /**
     * 解码 base64 图片。
     *
     * @param part 图片分片
     * @return 字节数组；失败返回 null
     */
    private byte[] decodeImage(ModelImagePayload.ImagePart part) {
        try {
            return Base64.getDecoder().decode(part.base64());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 构造一条差异项。
     *
     * @param category    类别
     * @param severity    严重度（INFO/WARN/ERROR）
     * @param description 描述
     * @return Map
     */
    private Map<String, Object> finding(String category, String severity, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("category", category);
        item.put("severity", severity);
        item.put("description", description);
        return item;
    }

    /**
     * 把 payload 中的列表安全转成 Map 列表。
     *
     * @param value payload 值
     * @return Map 列表（null 安全，元素非 Map 时忽略）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(value instanceof Iterable<?> it)) {
            return out;
        }
        for (Object o : it) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /**
     * 安全取字符串。
     *
     * @param v 值
     * @return 字符串
     */
    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 是否处理该能力（供单测与排障使用）。
     *
     * @param capabilityCodes 能力编码集合
     * @return 任一命中返回 true
     */
    public boolean handlesAny(Iterable<String> capabilityCodes) {
        if (CollUtil.isEmpty(capabilityCodes)) {
            return false;
        }
        for (String code : capabilityCodes) {
            if (supportsCapability(code)) {
                return true;
            }
        }
        return false;
    }

}
