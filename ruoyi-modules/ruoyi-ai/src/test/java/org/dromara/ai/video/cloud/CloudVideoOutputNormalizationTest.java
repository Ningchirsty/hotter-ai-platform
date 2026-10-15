package org.dromara.ai.video.cloud;

import org.dromara.ai.video.service.MediaProbe;
import org.dromara.ai.video.exception.VideoTaskException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CloudVideoOutputNormalizationTest {
 @TempDir Path temp;
 CloudVideoRequest request(String model,String ratio){return new CloudVideoRequest(model,"T2V","scene",5,"720p",ratio,List.of(),false,null,null,"test");}
 @Test void onlyMatchingHigherKlingOutputMayBeReduced() {
  assertArrayEquals(new int[]{1280,720},VideoCloudService.downscaleTarget(request("kling-2.6","16:9"),new MediaProbe.Probe(1920,1080,24d,5000L,true)));
  assertArrayEquals(new int[]{720,1280},VideoCloudService.downscaleTarget(request("kling-2.5-turbo","9:16"),new MediaProbe.Probe(1080,1920,24d,5000L,true)));
  assertNull(VideoCloudService.downscaleTarget(request("wan3.0-video","16:9"),new MediaProbe.Probe(1920,1080,24d,5000L,true)));
  assertNull(VideoCloudService.downscaleTarget(request("kling-2.6","16:9"),new MediaProbe.Probe(1280,720,24d,5000L,true)));
  assertNull(VideoCloudService.downscaleTarget(request("kling-2.6","16:9"),new MediaProbe.Probe(854,480,24d,5000L,true)));
  assertThrows(VideoTaskException.class,()->VideoCloudService.downscaleTarget(request("kling-2.6","16:9"),new MediaProbe.Probe(1080,1920,24d,5000L,true)));
 }
 @Test void realDownscalePreservesSourceAudioAndDurationAndUsesRequestedDimensions()throws Exception {
  var probe=new MediaProbe("ffprobe","ffmpeg",30);
  Assumptions.assumeTrue(probe.executableAvailable("ffmpeg")&&probe.executableAvailable("ffprobe"));
  Path source=temp.resolve("original.mp4");
  var p=new ProcessBuilder("ffmpeg","-nostdin","-y","-v","error","-f","lavfi","-i","color=c=blue:s=1920x1080:r=24:d=1","-f","lavfi","-i","sine=frequency=500:duration=1","-c:v","libx264","-preset","ultrafast","-threads","1","-c:a","aac","-t","1",source.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
  assertTrue(p.waitFor(30,TimeUnit.SECONDS));assertEquals(0,p.exitValue());byte[] original=Files.readAllBytes(source);
  Path result=probe.downscale(source,1280,720);assertNotEquals(source,result);assertArrayEquals(original,Files.readAllBytes(source));
  var measured=probe.probe(result);assertTrue(measured.measured());assertEquals(1280,measured.width());assertEquals(720,measured.height());assertTrue(probe.hasAudio(result));
  assertTrue(Math.abs(probe.probe(source).durationMillis()-measured.durationMillis())<500);
 }
 @Test void unavailableEncoderDoesNotReplaceOriginalOrLeaveTemporaryOutput()throws Exception {
  Path source=temp.resolve("original.mp4");Files.write(source,new byte[]{1,2,3});var probe=new MediaProbe("ffprobe","nonexistent-ffmpeg-for-offline-test",1);
  assertEquals(source,probe.downscale(source,1280,720));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(source));
  try(var files=Files.list(temp)){assertEquals(1,files.count());}
 }
}
