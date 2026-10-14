package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Video templates use their own pinned provider profiles, never image bindings. */
public final class VideoTemplateDrafts {
    static final Map<String,String> PINS=Map.of(
        "vid-5s-landscape","160473f54051ae94e6dc66243094113adf0af5a15991181b1dd73c576d3d069f",
        "vid-5s-portrait","afd1ab7837119abe8b193671147733e2a1c643196c96c732b466f00a5964eff9",
        "vid-10s-landscape","04f76f6a67f6844470cee01d6bb8e1f14ad38b345291f947f2582c0024a2b9de",
        "vid-10s-portrait","c1a6f44578a0364c8430f925db0754179705b87ccf63b8dbafc892570147a4f2");
    private VideoTemplateDrafts() {}
    static boolean published(TemplateFeedCache cache,JsonNode template) {
        try {
            var binding=template.path("binding");
            var profile=cache.current().profiles().get(binding.path("params_profile").asText());
            return cache.online() && "video".equals(template.path("type").asText()) && "published".equals(template.path("status").asText())
                && "doubao-seedance-2-5-260628".equals(binding.path("model").asText())
                && "/v1/video/generations".equals(binding.path("endpoint").asText())
                && profile!=null && PINS.getOrDefault(binding.path("params_profile").asText(),"").equals(profile.path("sha256").asText())
                && profile.path("sha256").asText().equals(TemplateFeedCache.sha(TemplateFeedCache.canonical(profile.path("definition"))))
                && cache.bindingFresh(template);
        } catch(Exception exception) { return false; }
    }
    static Map<String,Object> project(TemplateFeedCache cache,JsonNode template) {
        var out=new LinkedHashMap<String,Object>();
        for(String field:List.of("id","revision","category","tags","title","variables")) out.put(field,TemplateFeedCache.JSON.convertValue(template.path(field),Object.class));
        out.put("media","video");
        out.put("model",template.path("binding").path("model").asText());
        out.put("hasVideoPreview",TemplateVideoPreviewCache.available(template));
        out.put("cover","/video/templates/"+template.path("id").asText()+"/cover");
        out.put("status",published(cache,template)?"published":"maintenance");
        var profile=cache.current().profiles().get(template.path("binding").path("params_profile").asText());
        var fixed=profile==null?TemplateFeedCache.JSON.createObjectNode():profile.path("definition").path("fixed");
        out.put("outputSize",fixed.path("width").asText()+"x"+fixed.path("height").asText());
        out.put("durationSeconds",fixed.path("duration").asInt());out.put("canGenerate",false);
        return out;
    }
    static Map<String,Object> detail(TemplateFeedCache cache,JsonNode template) {
        var out=project(cache,template);out.put("promptTemplate",TemplatePrompts.chinese(template));return out;
    }
    static Map<String,Object> prepare(TemplateFeedCache cache,TemplateFeedProperties properties,JsonNode body) {
        if(!body.isObject()) throw new TemplateFeedException(422,"模板参数须为对象");
        var names=body.fieldNames();var allowed=Set.of("template_id","revision","variables");
        while(names.hasNext()) if(!allowed.contains(names.next())) throw new TemplateFeedException(422,"模板参数包含未支持的字段");
        String id=body.path("template_id").asText();
        if(!id.matches("[A-Za-z0-9_-]{1,128}")) throw new TemplateFeedException(422,"模板编号无效");
        cache.refresh();var template=cache.require(id);
        if(!"video".equals(template.path("type").asText())) throw new TemplateFeedException(404,"视频模板不存在");
        if(!published(cache,template)) throw new TemplateFeedException(422,"视频模板暂不可用，请刷新重试");
        if(!body.path("revision").isIntegralNumber() || body.path("revision").asInt()!=template.path("revision").asInt()) throw new TemplateFeedException(409,"模板已更新，请重新选择");
        var errors=new ArrayList<Map<String,String>>();
        String prompt=TemplateVariables.resolve(body.path("variables"),template,properties.getBlockedTerms(),errors);
        if(!errors.isEmpty()) throw new TemplateFeedException(422,"模板参数校验未通过",errors);
        var fixed=cache.current().profiles().get(template.path("binding").path("params_profile").asText()).path("definition").path("fixed");
        return Map.of("templateId",id,"revision",template.path("revision").asInt(),"model",template.path("binding").path("model").asText(),
            "capability","T2V","prompt",prompt,"seconds",fixed.path("duration").asInt(),"resolution","1080p",
            "ratio",fixed.path("width").asInt()>fixed.path("height").asInt()?"16:9":"9:16","references",List.of());
    }
}
