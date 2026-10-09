package org.dromara.ai.video.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.exception.VideoTaskException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 固定蓝章鱼主机；提交不自动重试，查询和下载不跟随重定向。 */
public class BluOctoVideoClient {
    public record RemoteTask(String id, String protocol) { }
    private final VideoCloudProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    public BluOctoVideoClient(VideoCloudProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    BluOctoVideoClient(VideoCloudProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    /** 按型号契约构造供应商参数，避免将前端字段原样透传。 */
    public static Map<String,Object> payload(CloudVideoRequest request, String family,
                                            Function<CloudVideoRequest.Reference,String> source) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model",request.model()); body.put("prompt",request.prompt());
        body.put("seconds",request.seconds()); body.put("resolution",request.resolution()); body.put("ratio",request.ratio());
        List<Map<String,Object>> content = new ArrayList<>();
        content.add(Map.of("type","text","text",request.prompt()));
        for (var ref : request.references()) {
            String type = ref.role().equals("reference_video") ? "video_url" : ref.role().equals("reference_audio") ? "audio_url" : "image_url";
            content.add(Map.of("type",type,type,Map.of("url",source.apply(ref)),"role",ref.role()));
        }
        if (family.equals("Seedance") || family.equals("MiniMax")) {
            Map<String,Object> metadata = new LinkedHashMap<>();
            metadata.put("content",content);metadata.put("resolution",request.resolution());metadata.put("ratio",request.ratio());
            if (family.equals("Seedance")) metadata.put("generate_audio",Boolean.TRUE.equals(request.generateAudio()));
            if(request.seed()!=null) metadata.put("seed",request.seed());
            body.put("metadata",metadata);
        } else if (family.equals("Wan")) {
            Map<String,Object> input = new LinkedHashMap<>(); input.put("prompt",request.prompt());
            List<Map<String,Object>> media=new ArrayList<>();
            for(var ref:request.references()) {
                String url=source.apply(ref);
                if (ref.role().equals("first_frame")) { media.add(Map.of("type","first_frame","url",url)); }
                else if(ref.role().equals("last_frame")) media.add(Map.of("type","last_frame","url",url));
                else media.add(Map.of("type",ref.role(),"url",url));
            }
            if(!media.isEmpty()) input.put("media",media);
            if(request.negativePrompt()!=null && !request.negativePrompt().isBlank()) input.put("negative_prompt",request.negativePrompt());
            Map<String,Object> params = new LinkedHashMap<>();
            params.put("resolution",request.resolution().toUpperCase(java.util.Locale.ROOT));params.put("ratio",request.ratio());params.put("duration",request.seconds());params.put("audio",Boolean.TRUE.equals(request.generateAudio()));
            if(request.seed()!=null)params.put("seed",request.seed());
            body.put("metadata",Map.of("input",input,"parameters",params));
        } else {
            Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("resolution",request.resolution());metadata.put("aspect_ratio",request.ratio());
            request.references().stream().filter(r->"first_frame".equals(r.role())).findFirst().ifPresent(r->body.put("input_reference",source.apply(r)));
            request.references().stream().filter(r->"last_frame".equals(r.role())).findFirst().ifPresent(r->metadata.put("image_tail",source.apply(r)));
            body.put("metadata",metadata);
        }
        return body;
    }

    public RemoteTask submit(CloudVideoRequest request, JsonNode profile, Function<CloudVideoRequest.Reference,String> source) {
        String protocol=profile.path("protocol").asText();
        Map<String,Object> body=payload(request,profile.path("family").asText(),source);
        String path="/v1/videos";
        if(protocol.equals("doubao")) {
            path="/doubao/api/v3/contents/generations/tasks";
            var metadata=(Map<?,?>)body.get("metadata");
            body=new LinkedHashMap<>(Map.of("model",request.model(),"content",metadata.get("content"),"duration",request.seconds(),"resolution",request.resolution(),"ratio",request.ratio(),"generate_audio",Boolean.TRUE.equals(request.generateAudio())));
            if(request.seed()!=null)body.put("seed",request.seed());
        } else if(protocol.equals("alibaba")) {
            path="/ali/api/v1/services/aigc/video-generation/video-synthesis";
            var metadata=(Map<?,?>)body.get("metadata");body=new LinkedHashMap<>(Map.of("model",request.model(),"input",metadata.get("input"),"parameters",metadata.get("parameters")));
        }
        JsonNode result=json("POST",path,body);
        String id=result.path("id").asText(result.path("output").path("task_id").asText());
        if (!id.matches("[A-Za-z0-9_-]{1,160}")) throw error("CLOUD_RESULT_UNKNOWN","供应商未返回合法视频任务 ID，请核对记录后再操作");
        return new RemoteTask(id,protocol);
    }
    public JsonNode query(RemoteTask remote) { return json("GET",queryPath(remote),null); }
    private String queryPath(RemoteTask r) {
        if(!r.id().matches("[A-Za-z0-9_-]{1,160}"))throw error("CLOUD_RESULT_UNKNOWN","任务 ID 不合法");
        return r.protocol().equals("doubao")?"/doubao/api/v3/contents/generations/tasks/"+r.id():r.protocol().equals("alibaba")?"/ali/api/v1/tasks/"+r.id():"/v1/videos/"+r.id();
    }
    public static String state(JsonNode value) {
        String state=value.path("status").asText(value.path("output").path("task_status").asText()).toLowerCase(java.util.Locale.ROOT);
        if (List.of("completed","succeeded","success").contains(state)) return "SUCCEEDED";
        if (List.of("failed","failure","canceled","cancelled","expired").contains(state)) return "FAILED";
        if(List.of("queued","pending","running","processing","in_progress","submitted","not_start").contains(state))return "RUNNING";
        throw error("CLOUD_RESULT_UNKNOWN","供应商返回未知视频状态，请核对任务记录");
    }
    public byte[] download(RemoteTask remote) {
        if (!remote.id().matches("[A-Za-z0-9_-]{1,160}")) throw error("CLOUD_RESULT_UNKNOWN", "任务 ID 不合法");
        // 兼容视频别名失效时，只读取同一任务的实际产物；绝不重新生成或转发密钥到媒体域名。
        if (remote.protocol().equals("openai")) {
            try { return exchange("GET", "/v1/videos/"+remote.id()+"/content", null, 128*1024*1024); }
            catch (VideoTaskException e) {
                if (!List.of("CLOUD_ARTIFACT_EXPIRED", "CLOUD_ARTIFACT_MISSING").contains(e.getErrorCode())) throw e;
            }
        }
        String key=artifactKey(json("GET", "/v1/tasks/"+remote.id()+"/artifacts", null));
        return exchange("GET", "/v1/tasks/"+remote.id()+"/artifacts/"+key+"/content", null, 128*1024*1024);
    }
    static String artifactKey(JsonNode response) {
        JsonNode artifacts=response.isArray()?response:response.path("artifacts");
        if(!artifacts.isArray())artifacts=response.path("data");
        if(artifacts.isArray())for(JsonNode item:artifacts){
            String key=item.path("key").asText();
            String mime=item.path("content_type").asText(item.path("mime_type").asText());
            if(key.matches("[A-Za-z0-9_-]{1,160}")&&(mime.startsWith("video/")||item.path("type").asText().equals("video")))return key;
        }
        throw error("CLOUD_ARTIFACT_MISSING","供应商没有返回可下载的视频产物，未自动重新生成");
    }
    protected JsonNode json(String method,String path,Map<String,Object> body) {
        try { return mapper.readTree(exchange(method,path,body==null?null:mapper.writeValueAsString(body),2*1024*1024)); }
        catch(VideoTaskException e){throw e;}catch(Exception e){throw error("CLOUD_RESULT_UNKNOWN","云端视频响应无法识别，请核对供应商记录");}
    }
    private byte[] exchange(String method,String path,String body,int maxBytes) {
        String key=properties.readKey();if(key.isEmpty())throw error("CLOUD_NOT_CONFIGURED","云端视频服务尚未配置");
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("https://bluocto.com"+path)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+key);
            if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else b.GET();
            HttpResponse<java.io.InputStream> r=http.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var in=r.body()) {
                if(r.statusCode()<200||r.statusCode()>299)throw providerError(method,path,r.statusCode(),in.readNBytes(8192));
                byte[] bytes=in.readNBytes(maxBytes+1);if(bytes.length>maxBytes)throw error("CLOUD_RESULT_UNKNOWN","云端视频响应超过大小限制");return bytes;
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw error("CLOUD_RESULT_UNKNOWN","云端视频等待中断，请核对供应商记录");}
        catch(VideoTaskException e){throw e;}catch(Exception e){throw error(method.equals("POST")?"CLOUD_RESULT_UNKNOWN":"CLOUD_QUERY_FAILED","云端视频网络异常，请核对供应商记录，不自动重新生成");}
    }
    /** 只返回预定义诊断信息，不向任务详情暴露供应商原文、签名 URL 或凭据。 */
    private VideoTaskException providerError(String method,String path,int status,byte[] body) {
        String code="", message="";
        try {
            JsonNode value=mapper.readTree(body), detail=value.path("error");
            code=detail.path("code").asText(value.path("code").asText());
            message=detail.path("message").asText(value.path("message").asText());
        } catch(Exception ignored) { /* 非 JSON 错误仍保留 HTTP 状态。 */ }
        String suffix="（HTTP "+status+"）";
        if(method.equals("POST") && status==400 && message.contains("is not served by this plugin"))
            return error("CLOUD_PLUGIN_UNSUPPORTED","蓝章鱼当前插件未声明支持该型号，请核对实例插件版本和模型路由；未自动切换接口重新生成"+suffix);
        if(path.endsWith("/content")) {
            if(code.equals("artifact_upstream_auth_failed"))
                return error("CLOUD_ARTIFACT_AUTH_FAILED","视频已生成，但蓝章鱼读取上游成片的鉴权失败；保留原任务等待恢复，无需重新生成"+suffix);
            if(code.equals("artifact_plugin_error"))
                return error("CLOUD_ARTIFACT_PLUGIN_FAILED","视频已生成，但蓝章鱼插件无法读取产物；请修复网关插件后恢复原任务"+suffix);
            if(status==410 || code.equals("artifact_gone"))
                return error("CLOUD_ARTIFACT_EXPIRED","蓝章鱼成片内容接口失效，请恢复原任务产物"+suffix);
            if(status==404 || code.equals("artifact_not_found"))
                return error("CLOUD_ARTIFACT_MISSING","蓝章鱼未找到该任务的视频产物，请核对原任务"+suffix);
        }
        return error(method.equals("POST")?"CLOUD_RESULT_UNKNOWN":"CLOUD_QUERY_FAILED","蓝章鱼视频接口返回 HTTP "+status);
    }
    private static VideoTaskException error(String code,String message){return new VideoTaskException(code,message);}
}
