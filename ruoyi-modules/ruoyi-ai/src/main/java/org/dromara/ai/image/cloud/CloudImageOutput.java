package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 输出参数快照；默认值不向供应商发送，数量最多两张。 */
public record CloudImageOutput(String size, Integer n, String quality, String outputFormat) {
    public static final CloudImageOutput DEFAULT = new CloudImageOutput(null, 1, null, null);
    public CloudImageOutput {
        size = empty(size); quality = empty(quality); outputFormat = empty(outputFormat);
        n = n == null ? 1 : n;
    }
    private static String empty(String value) { return value == null || value.isBlank() ? null : value; }
    public boolean custom() { return !equals(DEFAULT); }
    public void validate(String model, String capability) {
        if (n < 1 || n > 2) throw invalid("每次生成数量须为 1 或 2 张");
        boolean gpt = model != null && model.startsWith("gpt-");
        if (size != null && !(gpt ? List.of("1024x1024", "1536x1024", "1024x1536") : List.of("1024x1024", "1536x1024", "1024x1536", "2048x2048")).contains(size)) throw invalid("输出尺寸不在本次接入档位中");
        if (quality != null && (!gpt || !List.of("low", "medium", "high").contains(quality))) throw invalid("此型号不接受该画面质量参数");
        if (outputFormat != null && (!gpt || !List.of("png", "jpeg", "webp").contains(outputFormat))) throw invalid("此型号不接受该输出格式参数");
        if ("TRANSPARENT".equals(capability) && "jpeg".equals(outputFormat)) throw invalid("透明背景须选择 PNG 或 WEBP");
    }
    public Map<String,Object> vendorFields() {
        var result = new LinkedHashMap<String,Object>();
        if(size != null) result.put("size",size);
        if(n != 1 || size != null) result.put("n",n);
        if(quality != null) result.put("quality",quality);
        if(outputFormat != null) result.put("output_format",outputFormat);
        return result;
    }
    private static ImageTaskException invalid(String message) { return ImageTaskException.invalidContract(message); }
}
