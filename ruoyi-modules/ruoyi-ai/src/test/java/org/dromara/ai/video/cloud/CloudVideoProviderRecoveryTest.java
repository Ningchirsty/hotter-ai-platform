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
    @Test void invalidArtifactKeyCannotEscapeControlledEndpoint()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(404,"{}"),response(200,"{\"artifacts\":[{\"key\":\"../escape\",\"type\":\"video\"}]}"));
        var e=assertThrows(VideoTaskException.class,()->client(http).download(new BluOctoVideoClient.RemoteTask("existing","openai")));
        assertEquals("CLOUD_ARTIFACT_MISSING",e.getErrorCode());verify(http,times(2)).send(any(HttpRequest.class),any());
    }
    @Test void unknownErrorBodiesAreNotExposedToUsers()throws Exception {
        var http=mock(HttpClient.class);
        stub(http,response(503,"test-secret upstream internal details"));
        var e=assertThrows(VideoTaskException.class,()->client(http).query(new BluOctoVideoClient.RemoteTask("existing","openai")));
        assertEquals("CLOUD_QUERY_FAILED",e.getErrorCode());assertFalse(e.getMessage().contains("test-secret"));verify(http,times(1)).send(any(HttpRequest.class),any());
    }
}
