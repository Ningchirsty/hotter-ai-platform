package org.dromara.ai.video.cloud;
import com.fasterxml.jackson.databind.*;
import org.dromara.ai.video.exception.VideoTaskException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Offline validation only. No API key and no provider request. */
class CloudVideoContractTest {
 final ObjectMapper mapper=new ObjectMapper();
 JsonNode profiles()throws Exception{try(var in=getClass().getResourceAsStream("/video/bluocto-catalog.json")){return mapper.readTree(in).path("profiles");}}
 JsonNode profile(String id)throws Exception{for(var p:profiles())if(p.path("id").asText().equals(id))return p;throw new AssertionError(id);}
 CloudVideoRequest draft(JsonNode p,List<CloudVideoRequest.Reference> refs,String capability){return new CloudVideoRequest(p.path("id").asText(),capability,"a camera move",p.path("durations").get(0).asInt(),p.path("resolutions").get(0).asText(),"16:9",refs,null,null,null,"offline-test");}
 @Test void all16ModelsValidateOnlyTheirOwnMatrix()throws Exception{
  assertEquals(16,profiles().size());
  for(var p:profiles()){
   assertFalse(p.path("id").asText().toLowerCase().contains("happyhorse"));
   String cap=p.path("capabilities").get(0).asText();
   var refs=cap.equals("I2V")?List.of(new CloudVideoRequest.Reference(1,"first_frame")):cap.equals("R2V")?List.of(new CloudVideoRequest.Reference(1,"reference_image")):List.<CloudVideoRequest.Reference>of();
   draft(p,refs,cap).validate(p);
   var bad=new CloudVideoRequest(p.path("id").asText(),cap,"scene",999,"4k","bad",refs,null,null,null,"key");
   assertThrows(VideoTaskException.class,()->bad.validate(p));
  }
 }
 @Test void frameAndReferenceConstraintsAreEnforced()throws Exception{
  var p=profile("MiniMax-H3");
  assertThrows(VideoTaskException.class,()->draft(p,List.of(),"I2V").validate(p));
  assertThrows(VideoTaskException.class,()->draft(p,List.of(new CloudVideoRequest.Reference(1,"first_frame"),new CloudVideoRequest.Reference(2,"reference_video")),"R2V").validate(p));
  draft(p,List.of(new CloudVideoRequest.Reference(1,"first_frame"),new CloudVideoRequest.Reference(2,"last_frame")),"FL2V").validate(p);
 }
 @Test void minimaxAdaptiveRequiresVisualInput()throws Exception{
  var p=profile("MiniMax-H3");var d=draft(p,List.of(),"T2V");
  assertThrows(VideoTaskException.class,()->new CloudVideoRequest(d.model(),d.capability(),d.prompt(),d.seconds(),d.resolution(),"adaptive",d.references(),null,null,null,"key").validate(p));
 }
 @Test void wanNativePayloadUsesRoleMediaAndExplicitOutputParameters()throws Exception{
  var d=draft(profile("wan2.7-i2v"),List.of(new CloudVideoRequest.Reference(1,"first_frame")),"I2V");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Wan",r->"https://example.org/ref"));
  assertEquals("first_frame",payload.at("/metadata/input/media/0/type").asText());assertEquals("720P",payload.at("/metadata/parameters/resolution").asText());assertEquals(d.seconds(),payload.at("/metadata/parameters/duration").asInt());
 }
 @Test void seedancePayloadRetainsRolesAndAudioFlag()throws Exception{
  var d=draft(profile("doubao-seedance-2-5-260628"),List.of(new CloudVideoRequest.Reference(1,"reference_video")),"R2V");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Seedance",r->"https://example.org/video"));
  assertEquals("reference_video",payload.at("/metadata/content/1/role").asText());assertEquals("video_url",payload.at("/metadata/content/1/type").asText());
 }
 @Test void klingTailAndRatioAreNotLost()throws Exception{
  var d=draft(profile("kling-3.0"),List.of(new CloudVideoRequest.Reference(2,"last_frame"),new CloudVideoRequest.Reference(1,"first_frame")),"FL2V");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Kling",r->"https://example.org/"+r.assetId()));
  assertEquals("https://example.org/1",payload.path("input_reference").asText());assertEquals("https://example.org/2",payload.at("/metadata/image_tail").asText());assertEquals("16:9",payload.at("/metadata/aspect_ratio").asText());
 }
 @Test void unknownStatusIsNotReportedAsRunning()throws Exception{
  assertEquals("SUCCEEDED",BluOctoVideoClient.state(mapper.readTree("{\"status\":\"succeeded\"}")));
  assertEquals("RUNNING",BluOctoVideoClient.state(mapper.readTree("{\"output\":{\"task_status\":\"PENDING\"}}")));
  assertEquals("FAILED",BluOctoVideoClient.state(mapper.readTree("{\"status\":\"expired\"}")));
  assertThrows(VideoTaskException.class,()->BluOctoVideoClient.state(mapper.createObjectNode()));
 }
 @Test void wrongOutputResolutionOrRatioCannotBeMarkedVerified()throws Exception{
  var d=draft(profile("wan2.7-t2v"),List.of(),"T2V");
  VideoCloudService.validateOutput(d,1280,720);
  assertThrows(VideoTaskException.class,()->VideoCloudService.validateOutput(d,1920,1080));
  assertThrows(VideoTaskException.class,()->VideoCloudService.validateOutput(d,720,1280));
 }
 @Test void actualArtifactKeysAndNotStartAreRecognized()throws Exception{
  assertEquals("RUNNING",BluOctoVideoClient.state(mapper.readTree("{\"status\":\"NOT_START\"}")));
  assertEquals("result_mp4",BluOctoVideoClient.artifactKey(mapper.readTree("{\"artifacts\":[{\"key\":\"poster\",\"mime_type\":\"image/png\"},{\"key\":\"result_mp4\",\"mime_type\":\"video/mp4\"}]}")));
  assertThrows(VideoTaskException.class,()->BluOctoVideoClient.artifactKey(mapper.readTree("{\"artifacts\":[{\"key\":\"../bad\",\"type\":\"video\"}]}")));
 }
}
