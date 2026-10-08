package org.dromara.ai.image.cloud;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** 本机桥接的访问边界、真实任务入库/取消及幂等检查；不访问供应商。 */
class CloudImageLocalServerTest {
    @TempDir Path temp;
    @Test void onlySameOriginWithServerSessionCanCreateOwnedVerifiedTask() throws Exception {
        var properties=new ImageCloudProperties();properties.setEnabled(true);
        Path key=temp.resolve("key");Files.writeString(key,"offline-test-key");properties.setApiKeyFile(key.toString());
        String secret="a-local-session-secret-with-at-least-32-characters";
        try(var app=new CloudImageLocalServer(temp.resolve("data"),Path.of("../../script/sql/ry_image_task.sql"),secret,properties,List.of("gpt-image-2.5-sunburst","gpt-image-2.5-flare"),0)) {
            String base="http://127.0.0.1:"+app.port();var http=HttpClient.newHttpClient();var mapper=new ObjectMapper();
            assertEquals(403,http.send(HttpRequest.newBuilder(URI.create(base+"/image/cloud/models")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var models=send(http,base,secret,"GET","/image/cloud/models",null,null);assertEquals(200,models.statusCode());assertTrue(mapper.readTree(models.body()).path("configured").asBoolean());
            String valid="{\"model\":\"gpt-image-2.5-sunburst\",\"prompt\":\"test\",\"idempotencyKey\":\"local-test-once\"}";
            assertEquals(403,send(http,base,secret,"POST","/image/cloud/tasks",valid,null).statusCode());
            assertEquals(403,send(http,base,secret,"POST","/image/cloud/tasks",valid,"https://external.invalid").statusCode());
            var blocked=send(http,base,secret,"POST","/image/cloud/tasks",valid.replace("sunburst","flare"),"http://127.0.0.1:5179");assertEquals(400,blocked.statusCode());assertEquals("CLOUD_CAPABILITY_UNVERIFIED",mapper.readTree(blocked.body()).path("errorCode").asText());
            var created=send(http,base,secret,"POST","/image/cloud/tasks",valid,"http://127.0.0.1:5179");assertEquals(200,created.statusCode());String id=mapper.readTree(created.body()).path("taskId").asText();
            var duplicate=send(http,base,secret,"POST","/image/cloud/tasks",valid,"http://127.0.0.1:5179");assertEquals(id,mapper.readTree(duplicate.body()).path("taskId").asText());assertTrue(mapper.readTree(duplicate.body()).path("idempotent").asBoolean());
            var cancelled=send(http,base,secret,"POST","/image/tasks/"+id+"/cancel",null,"http://127.0.0.1:5179");assertEquals("CANCELED",mapper.readTree(cancelled.body()).path("status").asText());
            var tasks=send(http,base,secret,"GET","/image/tasks",null,null);assertEquals(1,mapper.readTree(tasks.body()).path("total").asInt());assertEquals(id,mapper.readTree(tasks.body()).path("rows").get(0).path("id").asText());
        }
    }
    private HttpResponse<String> send(HttpClient http,String base,String secret,String method,String path,String body,String origin)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("X-Hotter-Local-Session",secret);
        if(origin!=null)request.header("Origin",origin);
        if(body!=null)request.header("Content-Type","application/json");
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
