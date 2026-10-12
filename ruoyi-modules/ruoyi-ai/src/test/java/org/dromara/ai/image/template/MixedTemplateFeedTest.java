package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.image.service.ImageInspirationTenantResolver;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MixedTemplateFeedTest {
    @TempDir Path directory;
    TemplateFeedProperties properties() { var p=new TemplateFeedProperties();p.setCacheDirectory(directory.toString());return p; }
    TemplateFeedCache cache(boolean webp) throws Exception {
        var feed=TemplateFeedCache.JSON.readTree(getClass().getResourceAsStream("/template-feed/mixed-18.json"));
        byte[] cover;
        if(webp) cover=getClass().getResourceAsStream("/template-feed/cover.webp").readAllBytes();
        else {
            var stream=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,5,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",stream);cover=stream.toByteArray();
        }
        String hash=TemplateFeedCache.sha(cover),name="covers/"+hash.substring(0,12)+(webp?".webp":".png");
        for(var t:feed.path("templates")) {
            var c=(com.fasterxml.jackson.databind.node.ObjectNode)t.path("cover");c.put("url",name);c.put("sha256",hash);
        }
        var manifest=TemplateFeedCache.JSON.createObjectNode();manifest.put("schema_version",2);manifest.put("feed_version",18);manifest.put("base_url",TemplateFeedCache.BASE);manifest.put("template_count",2);
        var profiles=manifest.putObject("profiles");feed.path("profiles").fields().forEachRemaining(e->profiles.put(e.getKey(),e.getValue().path("sha256").asText()));
        byte[] full=TemplateFeedCache.JSON.writeValueAsBytes(feed);manifest.putObject("files").put("full-18.json",TemplateFeedCache.sha(full)).put(name,hash);
        Map<String,byte[]> resources=Map.of("manifest.json",TemplateFeedCache.JSON.writeValueAsBytes(manifest),"full-18.json",full,name,cover);
        var cache=new TemplateFeedCache(properties(),(resource,etag)->new TemplateFeedCache.Resource(200,resources.get(resource),""),Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"),ZoneOffset.UTC));
        cache.refresh();assertTrue(cache.online(),cache.state().toString());assertEquals(2,cache.current().templates().size());return cache;
    }
    @Test void webpAndMixedMediaCannotDisableTheWholeTemplateFeed() throws Exception {
        var cache=cache(true);
        var probe=TemplateFeedCache.probeCover(cache.cover("video-example"),"cover.webp");
        assertTrue(probe.measured());assertEquals(3,probe.width());assertEquals(5,probe.height());
        assertTrue(cache.published(cache.require("image-example")));
        assertFalse(cache.published(cache.require("video-example")));
        assertTrue(VideoTemplateDrafts.published(cache,cache.require("video-example")));
    }
    @Test void separateHttpApisFilterListsAndRejectOppositeMediaIds() throws Exception {
        var cache=cache(false);var tenants=mock(ImageInspirationTenantResolver.class);when(tenants.resolve(7L)).thenReturn("tenant");
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),eq(String.class),eq(7L))).thenReturn(List.of("tenant"));
        var mvc=MockMvcBuilders.standaloneSetup(new TemplateFeedController(cache,mock(TemplateGenerationService.class),tenants),new VideoTemplateController(cache,properties(),jdbc))
            .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        try(var login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            mvc.perform(get("/image/templates")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("image-example"));
            mvc.perform(get("/video/templates")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("video-example")).andExpect(jsonPath("$.data.items[0].binding").doesNotExist());
            for(String suffix:List.of("","/cover")) {
                mvc.perform(get("/image/templates/video-example"+suffix)).andExpect(status().isNotFound());
                mvc.perform(get("/video/templates/image-example"+suffix)).andExpect(status().isNotFound());
            }
        }
    }
    @Test void videoShotDirectivesArePreservedWithoutRelaxingImagePlaceholders() throws Exception {
        var cache=cache(false);
        var template=(com.fasterxml.jackson.databind.node.ObjectNode)cache.require("video-example");
        String prompt="[00:00-00:04] [Wide tracking] A bicycle follows the coast. [Side tracking] A smooth continuous shot.";
        ((com.fasterxml.jackson.databind.node.ObjectNode)template.path("prompt")).put("en",prompt);
        var body=TemplateFeedCache.JSON.readTree("{\"template_id\":\"video-example\",\"revision\":1,\"variables\":{}}");
        assertEquals(prompt,VideoTemplateDrafts.prepare(cache,properties(),body).get("prompt"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)template.path("prompt")).put("en","A scene with {{undeclared}}");
        assertThrows(TemplateFeedException.class,()->VideoTemplateDrafts.prepare(cache,properties(),body));
        var image=TemplateFeedCache.JSON.readTree("{\"type\":\"image\",\"variables\":[],\"prompt\":{\"en\":\"A poster for [PRODUCT]\"}}");
        var errors=new ArrayList<Map<String,String>>();
        TemplateVariables.resolve(TemplateFeedCache.JSON.createObjectNode(),image,List.of(),errors);
        assertEquals(List.of(Map.of("field","prompt","rule","unresolved_placeholder")),errors);
    }
    @Test void videoPreparationPinsOutputAndRejectsOverridesStaleRevisionAndImageTemplates() throws Exception {
        var cache=cache(false);
        var body=TemplateFeedCache.JSON.readTree("{\"template_id\":\"video-example\",\"revision\":1,\"variables\":{}}");
        var draft=VideoTemplateDrafts.prepare(cache,properties(),body);
        assertEquals("doubao-seedance-2-5-260628",draft.get("model"));assertEquals("T2V",draft.get("capability"));assertEquals(10,draft.get("seconds"));assertEquals("9:16",draft.get("ratio"));assertEquals(List.of(),draft.get("references"));
        assertFalse(draft.containsKey("binding"));assertFalse(draft.containsKey("endpoint"));
        for(String invalid:List.of("{\"template_id\":\"image-example\",\"revision\":1,\"variables\":{}}","{\"template_id\":\"video-example\",\"revision\":2,\"variables\":{}}","{\"template_id\":\"video-example\",\"revision\":1,\"variables\":{},\"model\":\"override\"}","{\"template_id\":\"video-example\",\"revision\":1,\"variables\":{\"undeclared\":\"value\"}}")) {
            JsonNode request=TemplateFeedCache.JSON.readTree(invalid);assertThrows(TemplateFeedException.class,()->VideoTemplateDrafts.prepare(cache,properties(),request));
        }
    }
}
