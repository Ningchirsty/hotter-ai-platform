package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

/** Use the same Chinese recipe for preview and preparation; translated overrides pin source content. */
final class TemplatePrompts {
    private static final Pattern HAN=Pattern.compile("[\\p{IsHan}]");
    private static final JsonNode TRANSLATIONS=load();
    private TemplatePrompts() { }
    private static JsonNode load() {
        try(var input=TemplatePrompts.class.getResourceAsStream("/template-feed/chinese-prompts.json")) {
            if(input==null) throw new IllegalStateException("Chinese template resource missing");
            return TemplateFeedCache.JSON.readTree(input);
        } catch(Exception exception) { throw new IllegalStateException("Chinese template resource invalid",exception); }
    }
    static String chinese(JsonNode template) {
        String zh=template.path("prompt").path("zh").asText();
        String original=zh.isBlank()?template.path("prompt").path("en").asText():zh;
        if(HAN.matcher(original).find()) return original;
        JsonNode translated=TRANSLATIONS.path(template.path("id").asText());
        if(translated.path("sourceSha256").asText().equals(TemplateFeedCache.sha(original.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            && HAN.matcher(translated.path("prompt").asText()).find()) return translated.path("prompt").asText();
        // Older feed fixtures did not declare a Chinese field; preserve their original contract.
        if(zh.isBlank()) return original;
        throw new TemplateFeedException(503,"模板中文版已更新，请同步后重试");
    }
}
