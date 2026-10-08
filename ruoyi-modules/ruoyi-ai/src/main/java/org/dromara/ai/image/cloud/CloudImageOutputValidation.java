package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.exception.ImageTaskException;
import java.util.List;
import java.util.Map;

/** 真实输出参数验收档位，按型号和创作能力隔离；浏览器不可覆写。 */
public final class CloudImageOutputValidation {
    private CloudImageOutputValidation() {}
    private static final List<Map<String,Object>> PROFILES = load();
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> load() {
        try(var source=CloudImageOutputValidation.class.getResourceAsStream("/cloud-image-output-validation.json")) {
            if(source == null) return List.of();
            return new ObjectMapper().readValue(source, List.class);
        } catch(Exception e) { throw new IllegalStateException("无法加载云端输出参数验收档位"); }
    }
    public static List<Map<String,Object>> profiles(String model) {
        return PROFILES.stream().filter(p->model.equals(p.get("model"))).toList();
    }
    private static Object value(CloudImageOutput output,String key) {
        return switch(key) { case "size" -> output.size();case "n" -> output.n();case "quality" -> output.quality();case "outputFormat" -> output.outputFormat();default -> null; };
    }
    public static void requireVerified(CloudImageRequest input) {
        requireVerified(input, profiles(input.model()));
    }
    static void requireVerified(CloudImageRequest input, List<Map<String,Object>> profiles) {
        if(!input.output().custom()) return;
        for(String key : List.of("size","n","quality","outputFormat")) {
            Object value=value(input.output(),key);
            if(value == null || (key.equals("n") && value.equals(1))) continue;
            boolean accepted=profiles.stream().anyMatch(p->input.model().equals(p.get("model")) && input.capability().equals(p.get("capability"))
                && p.get("verifiedFields") instanceof List<?> keys && keys.contains(key)
                && value.equals(value(new ObjectMapper().convertValue(p.get("output"),CloudImageOutput.class),key)));
            if(!accepted) throw new ImageTaskException("CLOUD_OUTPUT_PARAMETERS_UNVERIFIED","所选输出参数尚未通过此型号、此能力验证："+key);
        }
    }
}
