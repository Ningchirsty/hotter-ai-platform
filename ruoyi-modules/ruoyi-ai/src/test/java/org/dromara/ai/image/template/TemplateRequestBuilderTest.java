package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateRequestBuilderTest {
    JsonNode full() throws Exception { return TemplateFeedCache.JSON.readTree(getClass().getResourceAsStream("/template-feed/full-4.json")); }
    ObjectNode body(JsonNode t) {
        var b=TemplateFeedCache.JSON.createObjectNode(); b.put("template_id",t.path("id").asText()); b.put("revision",t.path("revision").asInt()); b.put("client_request_id",UUID.randomUUID().toString()); b.putObject("variables"); return b;
    }
    @Test void allThirteenPublishedTemplatesRebuildWithoutClientBindingAndKeepActualDimensions() throws Exception {
        var f=full(); var profiles=TemplateFeedCache.nodes(f.path("profiles"));
        for(var t:f.path("templates")) {
            var b=body(t); for(var v:t.path("variables")) ((ObjectNode)b.path("variables")).put(v.path("key").asText(),"Modern perfume bottle");
            var built=TemplateRequestBuilder.build(b,t,profiles,List.of());
            assertEquals("gpt-image-2.5-sunburst",built.input().model()); assertEquals(1,built.input().output().n()); assertEquals("high",built.input().output().quality());
            assertFalse(built.input().prompt().contains("{{")); assertFalse(built.input().prompt().contains("[SUBJECT]"));
            TemplateRequestBuilder.requireTemplateInput(built.input());
        }
    }
    @Test void maliciousBindingAndUnknownFieldsAreRejectedTogether() throws Exception {
        var f=full(); var t=f.path("templates").get(0); var b=body(t); b.put("model","evil"); b.put("endpoint","https://evil.test"); b.putObject("params").put("n",100);
        var e=assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(b,t,TemplateFeedCache.nodes(f.path("profiles")),List.of()));
        assertEquals(3,e.errors.size()); assertTrue(e.errors.stream().allMatch(x->x.get("rule").equals("unknown_field")));
        var changed=t.deepCopy(); ((ObjectNode)changed.path("binding")).put("model","another-model");
        assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(body(t),changed,TemplateFeedCache.nodes(f.path("profiles")),List.of()));
    }
    @Test void unknownVariablesPlaceholderInjectionUnicodeLengthAndNfkcTermsAreCheckedServerSide() throws Exception {
        var f=full(); var t=f.path("templates").get(2); var b=body(t); ((ObjectNode)b.path("variables")).put("subject","{{n}}").put("not_declared","x");
        var e=assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(b,t,TemplateFeedCache.nodes(f.path("profiles")),List.of()));
        assertTrue(e.errors.stream().anyMatch(x->x.get("rule").equals("placeholder_injection"))); assertTrue(e.errors.stream().anyMatch(x->x.get("rule").equals("undeclared_variable")));
        ((ObjectNode)b.path("variables")).remove("not_declared"); ((ObjectNode)b.path("variables")).put("subject","ＢＡＤ");
        assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(b,t,TemplateFeedCache.nodes(f.path("profiles")),List.of("bad")));
        var unicode=t.deepCopy(); ((ObjectNode)unicode.path("variables").get(0)).put("pattern","[\\p{L}\\p{N} ]+"); ((ObjectNode)b.path("variables")).put("subject","花瓶 2026");
        assertDoesNotThrow(()->TemplateRequestBuilder.build(b,unicode,TemplateFeedCache.nodes(f.path("profiles")),List.of()));
        ((ObjectNode)b.path("variables")).put("subject","a".repeat(61)); assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(b,t,TemplateFeedCache.nodes(f.path("profiles")),List.of()));
    }
    @Test void modifiedProfileCannotPassByRecomputingItsHash() throws Exception {
        var f=full(); var t=f.path("templates").get(0); var profiles=TemplateFeedCache.nodes(f.path("profiles")); var changed=profiles.get("img-square-hd").deepCopy();
        ((ObjectNode)changed.path("definition").path("fixed")).put("quality","low"); ((ObjectNode)changed).put("sha256",TemplateFeedCache.sha(TemplateFeedCache.canonical(changed.path("definition")))); profiles.put("img-square-hd",changed);
        assertThrows(TemplateFeedException.class,()->TemplateRequestBuilder.build(body(t),t,profiles,List.of()));
    }
}
