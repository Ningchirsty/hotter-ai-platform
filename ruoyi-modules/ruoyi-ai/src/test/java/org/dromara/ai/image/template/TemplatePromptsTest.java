package org.dromara.ai.image.template;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplatePromptsTest {
    @Test void previewAndPreparationUseTheSameChineseAndPreserveVariables() throws Exception {
        var template=TemplateFeedCache.JSON.readTree("{\"type\":\"video\",\"prompt\":{\"en\":\"A shot of {{subject}}\",\"zh\":\"[00:00-00:04] 广角跟拍{{subject}}，保持人物一致。\"},\"variables\":[{\"key\":\"subject\",\"type\":\"text\",\"required\":true}]}");
        var variables=TemplateFeedCache.JSON.readTree("{\"subject\":\"骑车的人\"}");
        var errors=new ArrayList<Map<String,String>>();
        assertEquals("[00:00-00:04] 广角跟拍骑车的人，保持人物一致。",TemplateVariables.resolve(variables,template,List.of(),errors));
        assertTrue(errors.isEmpty());assertTrue(TemplatePrompts.chinese(template).contains("{{subject}}"));
    }
    @Test void all82SourcePinnedVideoTranslationsResolveWithoutChangingMetadata() throws Exception {
        try(var input=getClass().getResourceAsStream("/template-feed/chinese-input-samples.json")) {
            var samples=TemplateFeedCache.JSON.readTree(input);assertEquals(82,samples.size());
            for(var sample:samples) {
                var template=TemplateFeedCache.JSON.createObjectNode();
                template.put("id",sample.path("id").asText());template.put("revision",sample.path("revision").asInt());template.putObject("prompt").put("zh",sample.path("prompt").asText());
                String chinese=TemplatePrompts.chinese(template);
                assertTrue(chinese.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN));
                assertFalse(chinese.isBlank());assertTrue(chinese.length()<=8000);
                ((ObjectNode)template.path("prompt")).put("zh",sample.path("prompt").asText()+" Changed source.");
                assertThrows(TemplateFeedException.class,()->TemplatePrompts.chinese(template));
            }
        }
    }
}
