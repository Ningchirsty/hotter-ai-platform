package org.dromara.ai.video.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.exception.VideoTaskException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实错误响应的离线复现，不读取真实密钥、不调用供应商。 */
class CloudVideoProviderRecoveryTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked")
    HttpResponse<InputStream> response(int status,String body) {
        var r=(HttpResponse<InputStream>)mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return r;
    }
    @SafeVarargs
    final void stub(HttpClient http,HttpResponse<InputStream>... responses)throws Exception {
        var queue=new java.util.ArrayDeque<>(List.of(responses));
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
            .thenAnswer(invocation->queue.removeFirst());
    }
    BluOctoVideoClient client(HttpClient http)throws Exception {
        var p=new VideoCloudProperties();p.setEnabled(true);Path key=temp.resolve("offline-key");Files.writeString(key,"test-secret");p.setApiKeyFile(key.toString());
        return new BluOctoVideoClient(p,http);
    }
    @Test void expiredVideoAliasUsesActualArtifactKeyWithoutResubmissionOrExternalUrl()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(410,"{\"error\":{\"code\":\"artifact_gone\"}}"),
                response(200,"{\"artifacts\":[{\"key\":\"poster\",\"type\":\"image\"},{\"key\":\"result_mp4\",\"mime_type\":\"video/mp4\",\"content_url\":\"https://untrusted.invalid/video?secret=token\"}]}"),response(200,"video-bytes"));
        assertArrayEquals("video-bytes".getBytes(StandardCharsets.UTF_8),client(http).download(new BluOctoVideoClient.RemoteTask("task-existing","openai")));
        var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(3)).send(captured.capture(),any());
        assertEquals("/v1/tasks/task-existing/artifacts/result_mp4/content",captured.getAllValues().get(2).uri().getPath());
        for(var r:captured.getAllValues()) {assertEquals("GET",r.method());assertEquals("https",r.uri().getScheme());assertEquals("bluocto.com",r.uri().getHost());assertNull(r.uri().getQuery());}
    }
    @Test void fallbackDoesNotHidePluginErrorOrSendPaidRetry()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(410,"{}"),response(200,"{\"artifacts\":[{\"key\":\"video\",\"type\":\"video\"}]}"),
                response(500,"{\"error\":{\"code\":\"artifact_plugin_error\",\"message\":\"test-secret https://private.invalid/signed\"}}"));
        var e=assertThrows(VideoTaskException.class,()->client(http).download(new BluOctoVideoClient.RemoteTask("existing","openai")));
        assertEquals("CLOUD_ARTIFACT_PLUGIN_FAILED",e.getErrorCode());assertFalse(e.getMessage().contains("test-secret"));assertFalse(e.getMessage().contains("private.invalid"));
        var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(3)).send(captured.capture(),any());assertTrue(captured.getAllValues().stream().allMatch(r->r.method().equals("GET")));
    }
    @Test void upstreamAuthFailureStopsAtGatewayAndRetainsRecoverableCause()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(200,"{\"artifacts\":[{\"key\":\"video\",\"type\":\"video\",\"content_url\":\"https://rolldek.com/private\"}]}"),
                response(502,"{\"error\":{\"code\":\"artifact_upstream_auth_failed\"}}"));
        var e=assertThrows(VideoTaskException.class,()->client(http).download(new BluOctoVideoClient.RemoteTask("existing","doubao")));
        assertEquals("CLOUD_ARTIFACT_AUTH_FAILED",e.getErrorCode());verify(http,times(2)).send(any(HttpRequest.class),any());
    }
    @Test void unsupportedPluginIsExplicitAndNeverSwitchesSubmitRoutesAutomatically()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(400,"{\"error\":{\"code\":\"invalid_request\",\"message\":\"model wan3.0-video is not served by this plugin test-secret\"}}"));
        var request=new CloudVideoRequest("wan3.0-video","T2V","scene",2,"480p","16:9",List.of(),true,null,null,"once");
        var profile=new ObjectMapper().readTree("{\"protocol\":\"alibaba\",\"family\":\"Wan\"}");
        var e=assertThrows(VideoTaskException.class,()->client(http).submit(request,profile,r->"unused"));
        assertEquals("CLOUD_PLUGIN_UNSUPPORTED",e.getErrorCode());assertFalse(e.getMessage().contains("test-secret"));
        var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(1)).send(captured.capture(),any());assertEquals("POST",captured.getValue().method());
    }
    @Test void malformedTaskIdIsRejectedBeforeReadingKeyOrRequestingUrl()throws Exception {
        var http=mock(HttpClient.class);
        assertThrows(VideoTaskException.class,()->new BluOctoVideoClient(new VideoCloudProperties(),http).download(new BluOctoVideoClient.RemoteTask("../other?token=x","openai")));
        verifyNoInteractions(http);
    }
    @Test void unavailableChannelIsDistinguishedWithoutExposingCredentialsOrSwitchingRoutes()throws Exception {
        for(String diagnostic:List.of("no available channel test-secret https://private.invalid/signed", "无可用渠道 test-secret")) {
            var http=mock(HttpClient.class);var mapper=new ObjectMapper();
            var body=mapper.createObjectNode();body.putObject("error").put("message",diagnostic);
            stub(http,response(400,body.toString()));
            var request=new CloudVideoRequest("wan3.0-video","I2V","scene",2,"480p","16:9",List.of(new CloudVideoRequest.Reference(1,"first_frame")),false,null,null,"once");
            var profile=mapper.readTree("{\"protocol\":\"openai\",\"family\":\"Wan\"}");
            var e=assertThrows(VideoTaskException.class,()->client(http).submit(request,profile,r->"https://example.invalid/reference"));
            assertEquals("CLOUD_CHANNEL_UNAVAILABLE",e.getErrorCode());assertFalse(e.getMessage().contains("test-secret"));assertFalse(e.getMessage().contains("private.invalid"));
            var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(1)).send(captured.capture(),any());assertEquals("/v1/videos",captured.getValue().uri().getPath());
        }
    }
    @Test void invalidArtifactKeyCannotEscapeControlledEndpoint()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(404,"{}"),response(200,"{\"artifacts\":[{\"key\":\"../escape\",\"type\":\"video\"}]}"));
        var e=assertThrows(VideoTaskException.class,()->client(http).download(new BluOctoVideoClient.RemoteTask("existing","openai")));
        assertEquals("CLOUD_ARTIFACT_MISSING",e.getErrorCode());verify(http,times(2)).send(any(HttpRequest.class),any());
    }
    @Test void safeParameterDiagnosticsDoNotExposeProviderBodiesOrRetryPaidRequests()throws Exception {
        var errors=java.util.Map.of("seconds must be between 5 and 10","CLOUD_DURATION_UNSUPPORTED",
            "resolution must be 720p","CLOUD_RESOLUTION_UNSUPPORTED","prompt is required","CLOUD_PROMPT_REQUIRED",
            "json: cannot unmarshal number","CLOUD_REQUEST_FORMAT_INVALID");
        for(var entry:errors.entrySet()) {
            var http=mock(HttpClient.class);var body=new ObjectMapper().createObjectNode();body.putObject("error").put("message",entry.getKey()+" test-secret https://private.invalid/signed");
            stub(http,response(400,body.toString()));
            var request=new CloudVideoRequest("kling-2.6","T2V","scene",5,"720p","16:9",List.of(),false,null,null,"once");
            var profile=new ObjectMapper().readTree("{\"protocol\":\"openai\",\"family\":\"Kling\"}");
            var error=assertThrows(VideoTaskException.class,()->client(http).submit(request,profile,r->"unused"));
            assertEquals(entry.getValue(),error.getErrorCode());assertFalse(error.getMessage().contains("test-secret"));assertFalse(error.getMessage().contains("private.invalid"));
            verify(http,times(1)).send(any(HttpRequest.class),any());
        }
    }
    @Test void unknownErrorBodiesAreNotExposedToUsers()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(503,"test-secret upstream internal details"));
        var e=assertThrows(VideoTaskException.class,()->client(http).query(new BluOctoVideoClient.RemoteTask("existing","openai")));
        assertEquals("CLOUD_QUERY_FAILED",e.getErrorCode());assertFalse(e.getMessage().contains("test-secret"));verify(http,times(1)).send(any(HttpRequest.class),any());
    }
    @Test void failedLegacyNativeQueryReadsSameTaskThroughGenericEndpointWithoutSubmission()throws Exception {
        for(String protocol:List.of("alibaba","doubao")) {
            var http=mock(HttpClient.class);stub(http,response(502,"upstream internal detail"),response(200,"{\"status\":\"SUCCESS\"}"));
            assertEquals("SUCCESS",client(http).query(new BluOctoVideoClient.RemoteTask("existing",protocol)).path("status").asText());
            var captured=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(2)).send(captured.capture(),any());
            assertEquals("/v1/tasks/existing",captured.getAllValues().getLast().uri().getPath());
            assertTrue(captured.getAllValues().stream().allMatch(r->r.method().equals("GET")&&r.uri().getHost().equals("bluocto.com")));
        }
    }
}
