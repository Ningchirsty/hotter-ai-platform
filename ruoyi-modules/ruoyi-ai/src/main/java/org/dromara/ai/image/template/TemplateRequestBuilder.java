package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.image.cloud.CloudImageRequest;
import org.dromara.ai.image.cloud.CloudImageOutput;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** 固定模型/端点/档位及 canonical 哈希，变量只能替换发布模板声明的占位符。 */
public final class TemplateRequestBuilder {
    static final Map<String,String> PINS=Map.of(
        "img-square-hd","95cf6f71e0b3b4e6bf7a4682a807fe314bb766e6ab6196948c983f7a598f00a3",
        "img-portrait-916","da7281f19c7158d939fee7a7fd18f568d3313295abe652b03f1793b07476be50",
        "img-landscape-169","4452a30ff5acc7398ff7f814f3793f457b509c3e4fff082c507ce2ffc2b6b5fd");
    private TemplateRequestBuilder() {}
    static boolean bindingAllowed(JsonNode template,Map<String,JsonNode> profiles) {
        var binding=template.path("binding"); String id=binding.path("params_profile").asText(); var profile=profiles.get(id);
        return profile!=null && PINS.getOrDefault(id,"").equals(profile.path("sha256").asText())
            && profile.path("sha256").asText().equals(TemplateFeedCache.sha(TemplateFeedCache.canonical(profile.path("definition"))))
            && "gpt-image-2.5-sunburst".equals(binding.path("model").asText()) && "/v1/images/generations".equals(binding.path("endpoint").asText());
    }
    public record Built(CloudImageRequest input,String hash,UUID clientId,String templateId,int revision) {}
    public static Built build(JsonNode body,JsonNode template,Map<String,JsonNode> profiles,List<String> blockedTerms) {
        var errors=new ArrayList<Map<String,String>>();
        Set<String> fields=Set.of("template_id","revision","client_request_id","variables");
        body.fieldNames().forEachRemaining(k->{if(!fields.contains(k)) error(errors,k,"unknown_field");});
        if(!body.isObject()) error(errors,"request","object_required");
        String id=body.path("template_id").asText(); int revision=body.path("revision").asInt(); UUID uuid=null;
        try { String value=body.path("client_request_id").asText(); uuid=UUID.fromString(value); if(!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); }
        catch(Exception e) { error(errors,"client_request_id","uuid_required"); }
        if(!body.path("revision").isIntegralNumber() || revision<1) error(errors,"revision","positive_integer_required");
        if(!id.equals(template.path("id").asText())) error(errors,"template_id","template_mismatch");
        JsonNode binding=template.path("binding"); String profileId=binding.path("params_profile").asText(); JsonNode profile=profiles.get(profileId);
        if(profile==null || !PINS.getOrDefault(profileId,"").equals(profile.path("sha256").asText())
            || !profile.path("sha256").asText().equals(TemplateFeedCache.sha(TemplateFeedCache.canonical(profile.path("definition"))))
            || !"gpt-image-2.5-sunburst".equals(binding.path("model").asText())
            || !"/v1/images/generations".equals(binding.path("endpoint").asText())) error(errors,"template","binding_not_allowed");
        var variables=body.path("variables"); if(!variables.isObject()) error(errors,"variables","object_required");
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
        if(!errors.isEmpty()) throw new TemplateFeedException(422,"模板参数校验未通过",errors);
        var fixed=profile.path("definition").path("fixed");
        var input=new CloudImageRequest("gpt-image-2.5-sunburst",prompt,"T2I",List.of(),null,new CloudImageOutput(fixed.path("size").asText(),1,fixed.path("quality").asText(),null));
        String hash=TemplateFeedCache.sha(TemplateFeedCache.canonical(body));
        return new Built(input,hash,uuid,id,revision);
    }
    private static void error(List<Map<String,String>> errors,String field,String rule) { errors.add(Map.of("field",field,"rule",rule)); }
    /** 仅模板内部入口可用，不改变原有云端表单的参数验证门禁。 */
    public static void requireTemplateInput(CloudImageRequest input) {
        input.validateShape();
        if(!"gpt-image-2.5-sunburst".equals(input.model()) || !"T2I".equals(input.capability())
            || !input.referenceAssetIds().isEmpty() || input.maskAssetId()!=null || input.output().n()!=1
            || !"high".equals(input.output().quality()) || input.output().outputFormat()!=null
            || !List.of("1024x1024","1024x1536","1536x1024").contains(input.output().size()))
            throw new TemplateFeedException(422,"模板请求不在服务端白名单中");
    }
}
