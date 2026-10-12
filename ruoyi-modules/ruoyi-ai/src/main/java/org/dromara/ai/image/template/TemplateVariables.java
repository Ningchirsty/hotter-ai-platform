package org.dromara.ai.image.template;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Same declared-variable validation for image and video templates. */
final class TemplateVariables {
    private TemplateVariables() {}
    static String resolve(JsonNode variables,JsonNode template,List<String> blockedTerms,List<Map<String,String>> errors) {
        if(!variables.isObject()) error(errors,"variables","object_required");
        var declarations=new LinkedHashMap<String,JsonNode>();
        for(var v:template.path("variables")) declarations.put(v.path("key").asText(),v);
        variables.fieldNames().forEachRemaining(k->{if(!declarations.containsKey(k)) error(errors,"variables."+k,"undeclared_variable");});
        String prompt=template.path("prompt").path("en").asText();
        for(var entry:declarations.entrySet()) {
            String key=entry.getKey(); JsonNode declaration=entry.getValue(), value=variables.path(key);
            String type=declaration.path("type").asText(); String text=value.isTextual()?value.asText().strip():"";
            if(declaration.path("required").asBoolean() && text.isEmpty()) error(errors,key,"required");
            if(!value.isMissingNode() && !value.isTextual()) error(errors,key,"text_required");
            if(!List.of("text","select").contains(type)) error(errors,key,"type_not_supported");
            if(text.codePointCount(0,text.length())>declaration.path("max_len").asInt(200)) error(errors,key,"max_length");
            if(text.contains("{{") || text.contains("}}")) error(errors,key,"placeholder_injection");
            if("select".equals(type) && !text.isEmpty()) {
                boolean allowed=false; for(var option:declaration.path("options")) if(option.asText().equals(text)) allowed=true;
                if(!allowed) error(errors,key,"select_option");
            }
            String pattern=declaration.path("pattern").asText();
            if(!pattern.isBlank() && !text.isEmpty()) {
                try { if(pattern.length()>512 || text.length()>2000 || !Pattern.compile(pattern,Pattern.UNICODE_CHARACTER_CLASS).matcher(text).matches()) error(errors,key,"pattern"); }
                catch(Exception e) { error(errors,key,"invalid_template_pattern"); }
            }
            String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            if(blockedTerms.stream().filter(s->!s.isBlank()).anyMatch(s->normalized.contains(Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)))) error(errors,key,"blocked_term");
            prompt=prompt.replace("{{"+key+"}}",text);
        }
        if(prompt.contains("{{") || prompt.contains("}}") || Pattern.compile("\\[[^\\]\\r\\n]{1,200}\\]").matcher(prompt).find()) error(errors,"prompt","unresolved_placeholder");
        if(prompt.isBlank() || prompt.length()>8000) error(errors,"prompt","length");
        return prompt;
    }
    private static void error(List<Map<String,String>> errors,String field,String rule) { errors.add(Map.of("field",field,"rule",rule)); }
}
