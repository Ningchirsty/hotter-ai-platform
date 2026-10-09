package org.dromara.ai.video.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.video.exception.VideoTaskException;
import java.util.List;

/** 用户提交的参数快照；参考素材仅接受本人素材 ID。 */
public record CloudVideoRequest(String model, String capability, String prompt, int seconds,
                                String resolution, String ratio, List<Reference> references,
                                Boolean generateAudio, String negativePrompt, Long seed,
                                String idempotencyKey) {
    public record Reference(long assetId, String role) { }
    public CloudVideoRequest {
        references = references == null ? List.of() : List.copyOf(references);
    }
    public void validate(JsonNode profile) {
        if (profile == null || !profile.path("id").asText().equals(model) || model.toLowerCase(java.util.Locale.ROOT).contains("happyhorse")) reject("视频型号不在接入范围");
        if (prompt == null || prompt.isBlank() || prompt.length() > 10000) reject("请填写不超过 10000 字符的视频描述");
        if (!contains(profile.path("capabilities"), capability) || !contains(profile.path("durations"), seconds)
            || !contains(profile.path("resolutions"), resolution) || !contains(profile.path("ratios"), ratio)) reject("型号、创作能力或输出参数不匹配");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) reject("缺少合法的提交幂等键");
        if (seed != null && (!profile.path("hasSeed").asBoolean() || seed < 0 || seed > 2147483647L)) reject("随机种子超出范围");
        if (negativePrompt != null && (negativePrompt.length() > 500 || (!negativePrompt.isBlank() && !"Wan".equals(profile.path("family").asText())))) reject("该型号不支持此反向描述参数");
        if (Boolean.TRUE.equals(generateAudio) && !profile.path("hasAudioOutput").asBoolean()) reject("该型号未开放音频生成参数");
        for (Reference ref : references) {
            if (ref == null || ref.role() == null || ref.assetId() <= 0 || !List.of("first_frame","last_frame","reference_image","reference_video","reference_audio").contains(ref.role())) reject("参考素材参数不合法");
            if (ref.role().equals("reference_video") && !profile.path("hasVideoReference").asBoolean()) reject("该型号不支持参考视频");
            if (ref.role().equals("reference_audio") && !profile.path("hasAudioReference").asBoolean()) reject("该型号不支持参考音频");
        }
        if (references.size() > profile.path("maxImages").asInt() + profile.path("maxVideos").asInt() + profile.path("maxAudios").asInt()) reject("参考素材数量超过上限");
        long first=count("first_frame"), last=count("last_frame"), images=count("reference_image"), videos=count("reference_video"), audios=count("reference_audio");
        if (first>1 || last>1 || images>profile.path("maxImages").asInt() || videos>profile.path("maxVideos").asInt() || audios>profile.path("maxAudios").asInt()) reject("参考素材数量超过型号上限");
        if("MiniMax".equals(profile.path("family").asText()) && "T2V".equals(capability) && "adaptive".equals(ratio))reject("文生视频需要具体画面比例");
        switch (capability) {
            case "T2V" -> { if (!references.isEmpty()) reject("文生视频不能附带参考素材"); }
            case "I2V" -> { if (first!=1 || references.size()!=1) reject("图生视频需要一张首帧图片"); }
            case "FL2V" -> { if (first!=1 || last!=1 || references.size()!=2) reject("首尾帧视频需要首帧和尾帧各一张"); }
            case "R2V" -> { if (references.isEmpty() || first+last>0) reject("参考生视频需要参考素材，不能混用首尾帧"); }
            default -> reject("不支持的云端视频创作能力");
        }
    }
    private long count(String role) { return references.stream().filter(r -> role.equals(r.role())).count(); }
    private static boolean contains(JsonNode values, Object value) {
        for (JsonNode n : values) if (n.asText().equals(String.valueOf(value))) return true;
        return false;
    }
    private static void reject(String message) { throw VideoTaskException.invalidContract(message); }
}
