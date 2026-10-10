package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateFeedCacheTest {
    @TempDir Path directory;
    static final Instant NOW=Instant.parse("2026-10-09T12:00:00Z");
    JsonNode node(String json) throws Exception { return TemplateFeedCache.JSON.readTree(json); }
    TemplateFeedProperties properties() { var p=new TemplateFeedProperties(); p.setCacheDirectory(directory.toString()); return p; }
    Map<String,byte[]> resources(int version,String profiles, String templates) throws Exception {
        var f=node("{\"schema_version\":2,\"feed_version\":"+version+",\"profiles\":"+profiles+",\"templates\":"+templates+"}");
        byte[] full=TemplateFeedCache.JSON.writeValueAsBytes(f);
        var m=TemplateFeedCache.JSON.createObjectNode(); m.put("base_url",TemplateFeedCache.BASE); m.put("schema_version",2); m.put("feed_version",version); m.put("template_count",f.path("templates").size());
        m.putObject("files").put("full-"+version+".json",TemplateFeedCache.sha(full)); var hashes=m.putObject("profiles"); f.path("profiles").fields().forEachRemaining(e->hashes.put(e.getKey(),e.getValue().path("sha256").asText()));
        var data=new HashMap<String,byte[]>(); data.put("manifest.json",TemplateFeedCache.JSON.writeValueAsBytes(m)); data.put("full-"+version+".json",full); return data;
    }
    @Test void actualFeedProfileHashesMatchPythonCanonicalAndRejectTampering() throws Exception {
        var full=TemplateFeedCache.JSON.readTree(getClass().getResourceAsStream("/template-feed/full-4.json"));
        var manifest=TemplateFeedCache.JSON.readTree(getClass().getResourceAsStream("/template-feed/manifest.json"));
        TemplateFeedCache.validateProfiles(TemplateFeedCache.nodes(full.path("profiles")),manifest.path("profiles"));
        assertEquals(13,full.path("templates").size());
        var altered=full.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)altered.path("profiles").path("img-square-hd").path("definition").path("fixed")).put("quality","low");
        assertThrows(TemplateFeedException.class,()->TemplateFeedCache.validateProfiles(TemplateFeedCache.nodes(altered.path("profiles")),manifest.path("profiles")));
    }
    @Test void tombstonesPreventLateUpsertsButDoNotDeleteNewerRevisions() throws Exception {
        var items=new LinkedHashMap<String,JsonNode>(); var tombstones=new HashMap<String,Integer>();
        TemplateFeedCache.upsert(node("{\"id\":\"one\",\"revision\":3}"),items,tombstones);
        TemplateFeedCache.apply(node("{\"deletes\":[{\"id\":\"one\",\"revision\":2}]}"),items,tombstones);
        assertEquals(3,items.get("one").path("revision").asInt());
        TemplateFeedCache.apply(node("{\"deletes\":[{\"id\":\"one\",\"revision\":3}]}"),items,tombstones);
        TemplateFeedCache.upsert(node("{\"id\":\"one\",\"revision\":2}"),items,tombstones); assertFalse(items.containsKey("one"));
        TemplateFeedCache.upsert(node("{\"id\":\"one\",\"revision\":4}"),items,tombstones); assertTrue(items.containsKey("one"));
    }
    @Test void unknownSchemaAndRollbackPreserveLastGoodCacheAndDisableGeneration() throws Exception {
        var data=resources(4,"{}","[]");
        var cache=new TemplateFeedCache(properties(),(name,etag)->new TemplateFeedCache.Resource(200,data.get(name),"etag"),Clock.fixed(NOW,ZoneOffset.UTC));
        cache.refresh(); assertEquals(4,cache.current().version()); assertTrue(cache.online());
        var future=node(new String(data.get("manifest.json"),java.nio.charset.StandardCharsets.UTF_8)); ((com.fasterxml.jackson.databind.node.ObjectNode)future).put("schema_version",3); data.put("manifest.json",TemplateFeedCache.JSON.writeValueAsBytes(future));
        assertThrows(TemplateFeedException.class,()->cache.sync(NOW.plusSeconds(601))); assertEquals(4,cache.current().version());
        var restarted=new TemplateFeedCache(properties(),(name,etag)->new TemplateFeedCache.Resource(403,new byte[0],""),Clock.fixed(NOW.plusSeconds(601),ZoneOffset.UTC));
        restarted.refresh(); assertEquals(4,restarted.current().version()); assertFalse(restarted.online()); assertTrue(restarted.browsable());
        assertEquals("FEED_HTTP_403",restarted.state().get("syncCode"));
        var rollback=resources(3,"{}","[]"); data.putAll(rollback); assertThrows(TemplateFeedException.class,()->cache.sync(NOW.plusSeconds(602))); assertEquals(4,cache.current().version());
    }
    @Test void missingAndBrokenDeltaFallBackToLatestFullAndDoNotCommitBadHashes() throws Exception {
        var data=resources(4,"{}","[]"); var calls=new ArrayList<String>();
        var cache=new TemplateFeedCache(properties(),(name,etag)-> { calls.add(name); return new TemplateFeedCache.Resource(data.containsKey(name)?200:404,data.getOrDefault(name,new byte[0]),""); },Clock.fixed(NOW,ZoneOffset.UTC));
        cache.refresh(); data.putAll(resources(5,"{}","[]")); cache.sync(NOW.plusSeconds(601));
        assertEquals(5,cache.current().version()); assertTrue(calls.contains("delta-5.json")); assertTrue(calls.contains("full-5.json"));
        data.putAll(resources(6,"{}","[]")); data.put("delta-6.json","{\"from\":0,\"to\":6}".getBytes()); cache.sync(NOW.plusSeconds(1202)); assertEquals(6,cache.current().version());
        data.putAll(resources(7,"{}","[]")); data.put("full-7.json","{}".getBytes());
        assertThrows(TemplateFeedException.class,()->cache.sync(NOW.plusSeconds(1803))); assertEquals(6,cache.current().version());
        assertEquals(6,node(Files.readString(directory.resolve("snapshot.json"))).path("version").asInt());
    }
    @Test void etag304AdvancesFreshnessAndSevenDayExpiryStopsBrowsing() throws Exception {
        var data=resources(4,"{}","[]"); var seen=new ArrayList<String>();
        var cache=new TemplateFeedCache(properties(),(name,etag)-> { seen.add(etag); return etag.isBlank()?new TemplateFeedCache.Resource(200,data.get(name),"tag"):new TemplateFeedCache.Resource(304,new byte[0],""); },Clock.fixed(NOW,ZoneOffset.UTC));
        cache.refresh(); cache.sync(NOW.plusSeconds(601)); assertTrue(seen.contains("tag")); assertEquals(NOW.plusSeconds(601),cache.current().checkedAt());
        var expired=new TemplateFeedCache(properties(),(n,e)->null,Clock.fixed(NOW.plus(Duration.ofDays(8)),ZoneOffset.UTC)); assertFalse(expired.browsable());
    }
    @Test void validCoverUsesMeasuredDimensionsWithoutChangingGenerationProfile() throws Exception {
        var png=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,5,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",png);
        byte[] bytes=png.toByteArray(); String hash=TemplateFeedCache.sha(bytes),name="covers/"+hash.substring(0,12)+".png";
        String template="{\"id\":\"actual\",\"revision\":1,\"schema_version\":2,\"type\":\"image\",\"cover\":{\"url\":\""+name+"\",\"sha256\":\""+hash+"\",\"width\":1024,\"height\":1024}}";
        var data=resources(4,"{}","["+template+"]");
        var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)node(new String(data.get("manifest.json"),java.nio.charset.StandardCharsets.UTF_8));
        ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("files")).put(name,hash);
        data.put("manifest.json",TemplateFeedCache.JSON.writeValueAsBytes(manifest));data.put(name,bytes);
        var cache=new TemplateFeedCache(properties(),(n,e)->new TemplateFeedCache.Resource(200,data.get(n),""),Clock.fixed(NOW,ZoneOffset.UTC));
        cache.refresh();assertTrue(cache.online());assertEquals(4,cache.current().version());assertArrayEquals(bytes,cache.cover("actual"));
        data.put(name,"not an image".getBytes());Files.delete(directory.resolve(name));
        assertThrows(TemplateFeedException.class,()->cache.sync(NOW.plusSeconds(601)));assertEquals(4,cache.current().version());
    }
    @Test void credentialCannotEscapeToExternalOriginOrResourcePath() {
        for(var path:List.of("https://evil.test/cover","../manifest.json","covers/abc.png?key=value","covers/%2e%2e/file.png")) assertThrows(TemplateFeedException.class,()->TemplateFeedCache.resourceName(path));
        assertDoesNotThrow(()->TemplateFeedCache.resourceName("covers/06101545ccb7.png"));
    }
}
