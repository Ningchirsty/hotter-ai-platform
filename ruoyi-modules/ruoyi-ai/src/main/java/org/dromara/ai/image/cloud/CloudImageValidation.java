package org.dromara.ai.image.cloud;

import java.util.List;
import java.util.Map;

/** 逐型号真实验收快照；未通过的能力在服务端关闭，浏览器不能覆写。 */
public final class CloudImageValidation {
    private CloudImageValidation() {}
    public static final String TESTED_AT = "2026-10-07";
    private static final Map<String, Map<String, String>> LATEST = Map.of(
        "flux-2-pro", Map.of("T2I", "HTTP_503"),
        "gpt-image-2.5-flare", Map.of("T2I", "RESULT_UNKNOWN", "EDIT", "PASSED", "MULTI", "UNVERIFIED", "MASK", "UNVERIFIED", "OUTPAINT", "UNVERIFIED", "TRANSPARENT", "UNVERIFIED"),
        "gpt-image-2.5-sunburst", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED", "MASK", "PASSED", "OUTPAINT", "PASSED", "TRANSPARENT", "PASSED"),
        "qwen-image-3.0", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"),
        "qwen-image-3.0-pro", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"),
        "wan2.7-image", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "HTTP_400"),
        "wan2.7-image-pro", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"));
    public static String status(String model, String capability) {
        if (model == null || capability == null) return "UNVERIFIED";
        return LATEST.getOrDefault(model, Map.of()).getOrDefault(capability, "UNVERIFIED");
    }
    public static boolean verified(String model, String capability) { return "PASSED".equals(status(model, capability)); }
    public static List<String> capabilities(String model) {
        return CloudImageRequest.CAPABILITIES.stream().filter(cap -> LATEST.getOrDefault(model == null ? "" : model, Map.of()).containsKey(cap)).toList();
    }
    public static int maxReferences(String model) { return model != null && model.startsWith("qwen-") ? 3 : model != null && model.startsWith("wan") ? 9 : 16; }
    public static int maxReferenceBytes(String model) { return model != null && model.startsWith("gpt-") ? 20 * 1024 * 1024 : 10 * 1024 * 1024; }
}
