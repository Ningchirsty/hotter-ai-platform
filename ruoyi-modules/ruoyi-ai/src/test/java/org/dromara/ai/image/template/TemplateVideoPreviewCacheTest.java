package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TemplateVideoPreviewCacheTest {
    @TempDir Path directory;
    JsonNode template(String type,String source,int revision) throws Exception {
        return TemplateFeedCache.JSON.valueToTree(Map.of("id","sample","type",type,"revision",revision,"video_meta",Map.of("reference_video",source)));
    }
    JsonNode sample(int revision) throws Exception { return template("video","https://media.goodcase.ai/media/video/sample.mp4",revision); }
    @Test void rejectsUntrustedOriginsPathsAndImageTemplatesBeforeFetching() throws Exception {
        for(String url:List.of("http://media.goodcase.ai/media/video/a.mp4","https://localhost/media/video/a.mp4",
            "https://media.goodcase.ai.evil.test/media/video/a.mp4","https://media.goodcase.ai:8443/media/video/a.mp4",
            "https://user@media.goodcase.ai/media/video/a.mp4","https://media.goodcase.ai/media/video/../a.mp4",
            "https://media.goodcase.ai/media/video/%2e%2e/a.mp4","https://media.goodcase.ai/media/video/a.mp4?key=secret",
            "https://media.goodcase.ai/media/video/a.mp4#fragment","https://media.goodcase.ai/private/a.mp4")) {
            assertFalse(TemplateVideoPreviewCache.available(template("video",url,1)),url);
        }
        assertFalse(TemplateVideoPreviewCache.available(template("image","https://media.goodcase.ai/media/video/a.mp4",1)));
        assertTrue(TemplateVideoPreviewCache.available(sample(1)));
    }
    @Test void concurrentReadersShareOneBuildButARevisionBuildsSeparateMedia() throws Exception {
        var count = new AtomicInteger();
        var cache = new TemplateVideoPreviewCache(directory,(source,target)-> { count.incrementAndGet(); Files.write(target,new byte[]{1,2,3}); });
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            JsonNode template=sample(1);var futures=new ArrayList<Future<Path>>();
            for(int i=0;i<8;i++)futures.add(executor.submit(()->cache.get(template)));
            Path first=futures.getFirst().get(5,TimeUnit.SECONDS);
            for(var future:futures)assertEquals(first,future.get(5,TimeUnit.SECONDS));
            assertEquals(1,count.get());assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(first));
            assertNotEquals(first,cache.get(sample(2)));assertEquals(2,count.get());
        }
    }
    @Test void failedOrEmptyBuildsAreNeverPublishedAndCanBeRetried() throws Exception {
        var count=new AtomicInteger();
        var cache=new TemplateVideoPreviewCache(directory,(source,target)-> {
            if(count.incrementAndGet()==1){Files.write(target,new byte[]{1});throw new IllegalStateException("interrupted");}
            if(count.get()==2)return;
            Files.write(target,new byte[]{2,3});
        });
        assertThrows(TemplateFeedException.class,()->cache.get(sample(1)));
        assertThrows(TemplateFeedException.class,()->cache.get(sample(1)));
        assertArrayEquals(new byte[]{2,3},Files.readAllBytes(cache.get(sample(1))));
        try(var files=Files.list(directory.resolve("video-previews-v1"))) { assertEquals(1,files.count()); }
    }
    @Test void feedChecksMediaAndRevisionBeforePreparingAPreview() throws Exception {
        var fixture=new MixedTemplateFeedTest();fixture.directory=directory;var cache=fixture.cache(false);
        assertThrows(TemplateFeedException.class,()->cache.videoPreview("image-example",1));
        var stale=assertThrows(TemplateFeedException.class,()->cache.videoPreview("video-example",2));assertEquals(409,stale.status);
        var projected=VideoTemplateDrafts.project(cache,cache.require("video-example"));
        assertFalse(projected.containsKey("video_meta"));assertFalse(projected.containsKey("binding"));
    }
    @Test void createsAPlayableH264PreviewWithAudioAndRejectsInvalidMedia() throws Exception {
        Path original=directory.resolve("original.mp4"),preview=directory.resolve("preview.mp4");
        Process fixture=new ProcessBuilder("ffmpeg","-nostdin","-y","-v","error","-f","lavfi","-i","testsrc2=size=96x64:rate=12",
            "-f","lavfi","-i","sine=frequency=440:sample_rate=32000","-t","1","-c:v","libx264","-threads","1","-pix_fmt","yuv420p","-c:a","aac",original.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(fixture.waitFor(15,TimeUnit.SECONDS));assertEquals(0,fixture.exitValue());
        TemplateVideoPreviewCache.transcode(original,preview);assertTrue(Files.size(preview)>0);
        Process probe=new ProcessBuilder("ffprobe","-v","error","-show_entries","stream=codec_name,width,height","-of","json",preview.toString()).start();
        assertTrue(probe.waitFor(10,TimeUnit.SECONDS));var result=TemplateFeedCache.JSON.readTree(probe.getInputStream().readAllBytes());assertEquals(0,probe.exitValue());
        assertEquals("h264",result.path("streams").path(0).path("codec_name").asText());assertEquals("aac",result.path("streams").path(1).path("codec_name").asText());
        assertTrue(result.path("streams").path(0).path("width").asInt()<=640);assertTrue(result.path("streams").path(0).path("height").asInt()<=640);
        Process decode=new ProcessBuilder("ffmpeg","-nostdin","-v","error","-xerror","-i",preview.toString(),"-f","null","-")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(decode.waitFor(15,TimeUnit.SECONDS));assertEquals(0,decode.exitValue());
        Files.writeString(original,"invalid mp4");assertThrows(TemplateFeedException.class,()->TemplateVideoPreviewCache.transcode(original,preview));
    }
}
