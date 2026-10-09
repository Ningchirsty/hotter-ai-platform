package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** 校验完成后原子替换快照；同步失败仅提供最长七天的只读缓存。 */
public class TemplateFeedCache {
    static final String BASE = "https://bluocto.com/tpl-51be1abed562f687809636aef5549952/";
    static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(TemplateFeedCache.class);
    private final TemplateFeedProperties properties;
    private final Fetcher fetcher;
    private final java.time.Clock clock;
    private volatile Snapshot snapshot = new Snapshot(0, Map.of(), Map.of(), Map.of(), Instant.EPOCH, "");
    private volatile boolean online;
    private volatile Instant attemptedAt = Instant.EPOCH;
    private volatile String syncCode = "NOT_SYNCED";
    public record Resource(int status, byte[] bytes, String etag) {}
    @FunctionalInterface interface Fetcher { Resource get(String name, String etag) throws Exception; }
    public record Snapshot(int version, Map<String,JsonNode> templates, Map<String,Integer> tombstones,
                           Map<String,JsonNode> profiles, Instant checkedAt, String etag) {}
    public TemplateFeedCache(TemplateFeedProperties properties) {
        this(properties, httpFetcher(properties), java.time.Clock.systemUTC());
    }
    TemplateFeedCache(TemplateFeedProperties properties, Fetcher fetcher, java.time.Clock clock) {
        this.properties=properties; this.fetcher=fetcher; this.clock=clock;
        try {
            JsonNode saved=JSON.readTree(Files.readAllBytes(root().resolve("snapshot.json")));
            snapshot=new Snapshot(saved.path("version").asInt(), nodes(saved.path("templates")),
                JSON.convertValue(saved.path("tombstones"), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Integer>>() {}),
                nodes(saved.path("profiles")), Instant.parse(saved.path("checkedAt").asText()), saved.path("etag").asText());
        } catch(Exception ignored) { /* 没有可用快照时不展示伪造模板。 */ }
    }
    private static Fetcher httpFetcher(TemplateFeedProperties properties) {
        HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (name,etag)-> {
            resourceName(name);
            String key=properties.readKey();
            if(key.isEmpty()) throw new TemplateFeedException(503,"FEED_KEY_MISSING");
            HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(BASE+name+"?key="+key))
                .timeout(Duration.ofSeconds(30)).header("User-Agent","hotter-template-feed/2").GET();
            if(etag!=null && !etag.isBlank()) builder.header("If-None-Match",etag);
            HttpResponse<InputStream> r=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream stream=r.body()) {
                if(r.statusCode()!=200) return new Resource(r.statusCode(),new byte[0],"");
                var read=new java.util.concurrent.FutureTask<byte[]>(()->stream.readNBytes(12*1024*1024+1));
                Thread.startVirtualThread(read);
                byte[] bytes;
                try { bytes=read.get(30,java.util.concurrent.TimeUnit.SECONDS); }
                catch(Exception e) { read.cancel(true); throw new TemplateFeedException(503,"FEED_READ_TIMEOUT"); }
                if(bytes.length>12*1024*1024) throw new TemplateFeedException(503,"FEED_TOO_LARGE");
                return new Resource(200,bytes,r.headers().firstValue("ETag").orElse(""));
            }
        };
    }
    static void resourceName(String name) {
        if(!name.matches("(?:manifest|full-[1-9][0-9]*|delta-[1-9][0-9]*)\\.json|covers/[a-f0-9]{12}\\.(?:png|webp|jpg)"))
            throw new TemplateFeedException(503,"FEED_PATH_REJECTED");
    }
    private Path root() { return Path.of(properties.getCacheDirectory()).toAbsolutePath().normalize(); }
    static Map<String,JsonNode> nodes(JsonNode node) {
        var map=new LinkedHashMap<String,JsonNode>(); node.fields().forEachRemaining(e->map.put(e.getKey(),e.getValue())); return map;
    }
    public synchronized void refresh() {
        Instant now=clock.instant();
        if(now.isBefore(attemptedAt.plusSeconds(600))) return;
        attemptedAt=now;
        try { sync(now); online=true; syncCode="OK"; }
        catch(Exception e) {
            online=false; syncCode=e instanceof TemplateFeedException ? e.getMessage() : "FEED_SYNC_FAILED";
            LOG.warn("Template feed sync stopped: {}", syncCode); // 不记录异常堆栈中的 URL/凭据。
        }
    }
    void sync(Instant now) throws Exception {
        Snapshot old=snapshot;
        Resource res=fetcher.get("manifest.json",old.version()>0 ? old.etag() : "");
        if(res.status()==304) {
            if(old.version()==0) throw new TemplateFeedException(503,"FEED_EMPTY_304");
            commit(new Snapshot(old.version(),old.templates(),old.tombstones(),old.profiles(),now,old.etag())); return;
        }
        if(res.status()!=200) throw new TemplateFeedException(503,"FEED_HTTP_"+res.status());
        JsonNode manifest=JSON.readTree(res.bytes());
        int target=manifest.path("feed_version").asInt();
        if(manifest.path("schema_version").asInt()!=2) throw new TemplateFeedException(503,"FEED_SCHEMA_UNSUPPORTED");
        if(target<old.version() || target<1) throw new TemplateFeedException(503,"FEED_VERSION_ROLLBACK");
        if(!BASE.equals(manifest.path("base_url").asText())) throw new TemplateFeedException(503,"FEED_ORIGIN_REJECTED");
        boolean full=old.version()==0 || target-old.version()>20;
        for(var entry:nodes(manifest.path("profiles")).entrySet())
            if(!old.profiles().containsKey(entry.getKey()) || !entry.getValue().asText().equals(old.profiles().get(entry.getKey()).path("sha256").asText())) full=true;
        var templates=new LinkedHashMap<>(old.templates()); var tombstones=new LinkedHashMap<>(old.tombstones());
        var profiles=new LinkedHashMap<>(old.profiles());
        if(!full) {
            for(int n=old.version()+1;n<=target;n++) {
                Resource delta=fetcher.get("delta-"+n+".json","");
                if(delta.status()==404) { full=true; break; }
                if(delta.status()!=200) throw new TemplateFeedException(503,"FEED_DELTA_HTTP_"+delta.status());
                checkFile(manifest,"delta-"+n+".json",delta.bytes(),false);
                JsonNode d=JSON.readTree(delta.bytes());
                if(d.path("from").asInt(-1)!=n-1 || d.path("to").asInt(-1)!=n) { full=true; break; }
                apply(d,templates,tombstones); profiles=new LinkedHashMap<>(nodes(d.path("profiles")));
            }
        }
        if(full) {
            String name="full-"+target+".json"; Resource r=fetcher.get(name,"");
            if(r.status()!=200) throw new TemplateFeedException(503,"FEED_FULL_HTTP_"+r.status());
            checkFile(manifest,name,r.bytes(),true); JsonNode f=JSON.readTree(r.bytes());
            if(f.path("schema_version").asInt()!=2 || f.path("feed_version").asInt()!=target) throw new TemplateFeedException(503,"FEED_FULL_VERSION_INVALID");
            templates=new LinkedHashMap<>(); tombstones=new LinkedHashMap<>(old.tombstones());
            for(var t:f.path("templates")) upsert(t,templates,tombstones);
            for(var t:old.templates().values()) if(!templates.containsKey(t.path("id").asText())) tombstones.merge(t.path("id").asText(),t.path("revision").asInt(),Math::max);
            profiles=new LinkedHashMap<>(nodes(f.path("profiles")));
        }
        validateProfiles(profiles,manifest.path("profiles"));
        if(templates.size()!=manifest.path("template_count").asInt(-1)) throw new TemplateFeedException(503,"FEED_COUNT_INVALID");
        for(var t:templates.values()) {
            if(t.path("schema_version").asInt()!=2 || !"image".equals(t.path("type").asText())) throw new TemplateFeedException(503,"FEED_TEMPLATE_INVALID");
            String cover=t.path("cover").path("url").asText(); resourceName(cover);
            String hash=t.path("cover").path("sha256").asText();
            if(!hash.matches("[a-f0-9]{64}") || !manifest.path("files").path(cover).asText().equals(hash)) throw new TemplateFeedException(503,"FEED_COVER_HASH_INVALID");
            Path path=root().resolve(cover); byte[] bytes=Files.exists(path)?Files.readAllBytes(path):null;
            if(bytes==null || !sha(bytes).equals(hash)) {
                Resource c=fetcher.get(cover,""); if(c.status()!=200) throw new TemplateFeedException(503,"FEED_COVER_HTTP_"+c.status());
                checkFile(manifest,cover,c.bytes(),true); bytes=c.bytes();
                var image=org.dromara.ai.image.service.ImageAssetProbe.probeBytes(bytes);
                if(!image.measured() || image.width()!=t.path("cover").path("width").asInt() || image.height()!=t.path("cover").path("height").asInt()) throw new TemplateFeedException(503,"FEED_COVER_IMAGE_INVALID");
                atomicWrite(path,bytes);
            }
        }
        commit(new Snapshot(target,Map.copyOf(templates),Map.copyOf(tombstones),Map.copyOf(profiles),now,res.etag()));
    }
    static void apply(JsonNode delta, Map<String,JsonNode> templates, Map<String,Integer> tombstones) {
        for(var t:delta.path("upserts")) upsert(t,templates,tombstones);
        for(var d:delta.path("deletes")) {
            String id=d.path("id").asText(); int revision=d.path("revision").asInt();
            tombstones.merge(id,revision,Math::max);
            if(templates.containsKey(id) && templates.get(id).path("revision").asInt()<=revision) templates.remove(id);
        }
    }
    static void upsert(JsonNode t, Map<String,JsonNode> templates, Map<String,Integer> tombstones) {
        String id=t.path("id").asText(); int revision=t.path("revision").asInt();
        if(!id.matches("[A-Za-z0-9_-]{1,128}") || revision<1) throw new TemplateFeedException(503,"FEED_TEMPLATE_ID_INVALID");
        if(revision<=tombstones.getOrDefault(id,0)) return;
        if(!templates.containsKey(id) || templates.get(id).path("revision").asInt()<revision) templates.put(id,t.deepCopy());
    }
    static void checkFile(JsonNode manifest,String name,byte[] bytes,boolean required) {
        String hash=manifest.path("files").path(name).asText();
        if((required && hash.isEmpty()) || (!hash.isEmpty() && !sha(bytes).equals(hash))) throw new TemplateFeedException(503,"FEED_FILE_HASH_INVALID");
    }
    static void validateProfiles(Map<String,JsonNode> profiles,JsonNode hashes) {
        if(!profiles.keySet().equals(nodes(hashes).keySet())) throw new TemplateFeedException(503,"FEED_PROFILE_SET_INVALID");
        profiles.forEach((id,p)-> {
            String hash=sha(canonical(p.path("definition")));
            if(!hash.equals(p.path("sha256").asText()) || !hash.equals(hashes.path(id).asText())) throw new TemplateFeedException(503,"FEED_PROFILE_HASH_INVALID");
        });
    }
    public static byte[] canonical(JsonNode node) {
        try { return JSON.writeValueAsBytes(sorted(node)); } catch(Exception e) { throw new TemplateFeedException(503,"FEED_JSON_INVALID"); }
    }
    static JsonNode sorted(JsonNode node) {
        if(node.isObject()) { ObjectNode out=JSON.createObjectNode(); new TreeMap<>(nodes(node)).forEach((k,v)->out.set(k,sorted(v))); return out; }
        if(node.isArray()) { var out=JSON.createArrayNode(); node.forEach(v->out.add(sorted(v))); return out; } return node;
    }
    static String sha(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(Exception e) { throw new IllegalStateException("SHA-256 unavailable"); } }
    private void commit(Snapshot next) throws Exception {
        atomicWrite(root().resolve("snapshot.json"),JSON.writeValueAsBytes(Map.of("version",next.version(),"templates",next.templates(),
            "tombstones",next.tombstones(),"profiles",next.profiles(),"checkedAt",next.checkedAt().toString(),"etag",next.etag()))); snapshot=next;
    }
    static void atomicWrite(Path path, byte[] bytes) throws Exception {
        Files.createDirectories(path.getParent()); Path temp=Files.createTempFile(path.getParent(),"feed-",".tmp");
        try { Files.write(temp,bytes); Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    public Snapshot current() { return snapshot; }
    public boolean online() { return online && clock.instant().isBefore(snapshot.checkedAt().plusSeconds(600)); }
    public Map<String,Object> state() { return Map.of("version",snapshot.version(),"online",online(),"checkedAt",snapshot.checkedAt().toString(),"syncCode",syncCode); }
    public JsonNode require(String id) {
        if(snapshot.tombstones().containsKey(id) && !snapshot.templates().containsKey(id)) throw new TemplateFeedException(410,"模板已下架");
        JsonNode t=snapshot.templates().get(id); if(t==null) throw new TemplateFeedException(404,"模板不存在"); return t;
    }
    public boolean published(JsonNode t) {
        try { return online() && TemplateRequestBuilder.bindingAllowed(t,snapshot.profiles()) && "published".equals(t.path("status").asText()) && clock.instant().isBefore(Instant.parse(t.path("binding").path("last_verified_at").asText()).plus(Duration.ofDays(14))); }
        catch(Exception e) { return false; }
    }
    public boolean browsable() { return clock.instant().isBefore(snapshot.checkedAt().plus(Duration.ofDays(7))); }
    public Map<String,Object> project(JsonNode t) {
        String id=t.path("id").asText(); var out=new LinkedHashMap<String,Object>();
        for(String field:List.of("id","revision","category","tags","title","variables")) out.put(field,JSON.convertValue(t.path(field),Object.class));
        out.put("cover",properties.getPublicBaseUrl()+"/"+id+"/cover?revision="+t.path("revision").asInt());
        out.put("status",published(t)?"published":"maintenance");
        JsonNode profile=snapshot.profiles().get(t.path("binding").path("params_profile").asText());
        out.put("outputSize",profile==null?"":profile.path("definition").path("fixed").path("size").asText());
        out.put("canGenerate",properties.isGenerationEnabled() && published(t)); return out;
    }
    public byte[] cover(String id) {
        JsonNode t=require(id); if(!browsable()) throw new TemplateFeedException(503,"模板缓存已过期");
        String name=t.path("cover").path("url").asText(); resourceName(name);
        try { byte[] bytes=Files.readAllBytes(root().resolve(name)); if(!sha(bytes).equals(t.path("cover").path("sha256").asText())) throw new Exception(); return bytes; }
        catch(Exception e) { throw new TemplateFeedException(503,"封面暂不可用"); }
    }
}
