package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.*;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CloudImageOutputTest {
    @org.junit.jupiter.api.io.TempDir Path temp;
    @Test void explicitMeasuredSizeAlsoSendsTheConcreteSingleImageCount() {
        assertEquals(Map.of("size","1024x1024","n",1),new CloudImageOutput("1024x1024",1,null,null).vendorFields());
        assertTrue(CloudImageOutput.DEFAULT.vendorFields().isEmpty());
    }
    @Test void invalidAndCrossModelParametersAreRejected() {
        for(int n : List.of(0,3)) assertThrows(ImageTaskException.class,()->new CloudImageOutput(null,n,null,null).validate("gpt-image-2.5-sunburst","T2I"));
        assertThrows(ImageTaskException.class,()->new CloudImageOutput(null,1,"high",null).validate("wan2.7-image","T2I"));
        assertThrows(ImageTaskException.class,()->new CloudImageOutput("4096x4096",1,null,null).validate("qwen-image-3.0","T2I"));
        assertThrows(ImageTaskException.class,()->new CloudImageOutput(null,2,null,"jpeg").validate("gpt-image-2.5-sunburst","TRANSPARENT"));
        assertEquals(CloudImageOutput.DEFAULT,new ObjectMapper().convertValue(Map.of(),CloudImageOutput.class));
    }
    @Test void customParametersAndTwoOutputsArePreservedInOnePaidRequest() throws Exception {
        var props=new ImageCloudProperties();props.setEnabled(true);Path key=temp.resolve("key");Files.writeString(key,"test-only-key");props.setApiKeyFile(key.toString());
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/v1/images/generations",ex->{
            calls.incrementAndGet();var json=new ObjectMapper().readTree(ex.getRequestBody());
            assertEquals("1536x1024",json.path("size").asText());assertEquals(2,json.path("n").asInt());
            assertEquals("medium",json.path("quality").asText());assertEquals("jpeg",json.path("output_format").asText());
            byte[] response="{\"data\":[{\"b64_json\":\"AQID\"},{\"b64_json\":\"BAUG\"}]}".getBytes();ex.sendResponseHeaders(200,response.length);ex.getResponseBody().write(response);ex.close();
        });server.start();
        try {
            var client=new BluOctoImageClient(props,URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            var input=new CloudImageRequest("gpt-image-2.5-sunburst","flower","T2I",List.of(),null,new CloudImageOutput("1536x1024",2,"medium","jpeg"));
            var outputs=client.generateBatch(input,List.of());assertEquals(2,outputs.size());assertArrayEquals(new byte[]{4,5,6},outputs.get(1));assertEquals(1,calls.get());
        } finally {server.stop(0);}
    }
    @Test void batchArchiveStoresEveryOutputAndKeepsFirstCover() throws Exception {
        var repo=mock(ImageTaskRepository.class);var assets=mock(ImageAssetStore.class);var client=mock(BluOctoImageClient.class);
        var input=new CloudImageRequest("gpt-image-2.5-sunburst","flower","T2I",List.of(),null,new CloudImageOutput(null,2,null,null));
        var task=Map.<String,Object>of("workflow_code",ImageCloudService.WORKFLOW,"status","QUEUED","input_json",new ObjectMapper().writeValueAsString(Map.of("request",input)));
        when(repo.requireOwnedTask(1,"tenant",2)).thenReturn(task);when(repo.listEvents(1,"tenant")).thenReturn(List.of());
        when(repo.transition(1,ImageTaskStatus.QUEUED,ImageTaskStatus.RUNNING,null,null)).thenReturn(1);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(12,18,1),"png",bytes);
        when(client.generateBatch(eq(input),anyList())).thenReturn(List.of(bytes.toByteArray(),bytes.toByteArray()));
        when(assets.storeOutput(eq("tenant"),eq(2L),eq(1L),anyString(),any(),eq("image/png"))).thenReturn("first","second");
        var service=new ImageCloudService(new ImageCloudProperties(),client,repo,assets,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet,(m,c)->true,request->{});
        try {
            assertEquals("ACCEPTED",service.execute(1,"tenant",2));
            verify(repo,timeout(3000).times(2)).insertAsset(any());
            var captures=org.mockito.ArgumentCaptor.forClass(ImageTaskRepository.AssetRow.class);verify(repo,times(2)).insertAsset(captures.capture());
            assertEquals("tenant",captures.getAllValues().get(1).tenantId());assertEquals(2,captures.getAllValues().get(1).userId());
            verify(repo,timeout(3000)).markSucceeded(eq(1L),eq(captures.getAllValues().get(0).id()),eq(12),eq(18),eq(false),anyLong());
            verify(client,times(1)).generateBatch(eq(input),anyList());
        } finally {service.shutdown();}
    }
}
