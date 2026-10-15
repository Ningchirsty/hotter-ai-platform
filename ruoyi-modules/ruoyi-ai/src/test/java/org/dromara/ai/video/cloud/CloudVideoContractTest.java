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
 @Test void wan3UsesInstanceAdvertisedVideoCompatibilityWithoutEnablingUnverifiedVariants()throws Exception{
  assertEquals("openai",profile("wan3.0-video").path("protocol").asText());
  assertEquals("openai",profile("wan3.0-video-prime").path("protocol").asText());
  assertEquals("alibaba",profile("wan2.7-t2v").path("protocol").asText());
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
 @Test void wanAdvancedPayloadUsesRoleMediaAndExplicitOutputParameters()throws Exception{
  var d=new CloudVideoRequest("wan2.7-i2v","I2V","scene",5,"720p","16:9",List.of(new CloudVideoRequest.Reference(1,"first_frame")),true,null,null,"offline");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Wan",r->"https://example.org/ref"));
  assertEquals("first_frame",payload.at("/metadata/input/media/0/type").asText());assertEquals("720P",payload.at("/metadata/parameters/resolution").asText());assertEquals(d.seconds(),payload.at("/metadata/parameters/duration").asInt());
 }
 @Test void seedancePayloadRetainsRolesAndAudioFlag()throws Exception{
  var d=draft(profile("doubao-seedance-2-5-260628"),List.of(new CloudVideoRequest.Reference(1,"reference_video")),"R2V");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Seedance",r->"https://example.org/video"));
  assertEquals("reference_video",payload.at("/metadata/content/1/role").asText());assertEquals("video_url",payload.at("/metadata/content/1/type").asText());
 }
 @Test void correctedGatewayRoutesAndWanTopLevelFieldsMatchProviderContract()throws Exception{
  var calls=new ArrayList<String>();var bodies=new ArrayList<JsonNode>();
  var client=new BluOctoVideoClient(new VideoCloudProperties()) {
   @Override protected JsonNode json(String method,String path,Map<String,Object> body) {
    calls.add(method+" "+path);bodies.add(CloudVideoContractTest.this.mapper.valueToTree(body));
    return CloudVideoContractTest.this.mapper.createObjectNode().put(path.equals("/v1/video/generations")?"task_id":"id","same-task").put("status","completed");
   }
  };
  var r2v=draft(profile("wan2.7-r2v"),List.of(new CloudVideoRequest.Reference(7,"reference_image")),"R2V");
  var remote=client.submit(r2v,profile(r2v.model()),r->"https://example.org/product.png");
  assertEquals("POST /v1/videos",calls.getLast());assertEquals("openai",remote.protocol());
  assertTrue(bodies.getLast().path("seconds").isTextual());assertEquals("720P",bodies.getLast().path("resolution").asText());
  assertEquals("https://example.org/product.png",bodies.getLast().path("images").get(0).asText());
  assertFalse(bodies.getLast().has("metadata"));
  var wan3=draft(profile("wan3.0-video"),List.of(new CloudVideoRequest.Reference(7,"first_frame")),"I2V");
  client.submit(wan3,profile(wan3.model()),r->"https://example.org/product.png");
  assertEquals("POST /v1/videos",calls.getLast());assertEquals("https://example.org/product.png",bodies.getLast().path("image").asText());
  assertFalse(bodies.getLast().has("metadata"));
  var seedance=draft(profile("doubao-seedance-2-5-260628"),List.of(),"T2V");
  remote=client.submit(seedance,profile(seedance.model()),r->"unused");
  assertEquals("POST /v1/video/generations",calls.getLast());assertTrue(bodies.getLast().at("/metadata/content").isArray());
  client.query(remote);assertEquals("GET /v1/tasks/same-task",calls.getLast());
  client.query(new BluOctoVideoClient.RemoteTask("legacy","doubao"));assertEquals("GET /doubao/api/v3/contents/generations/tasks/legacy",calls.getLast());
 }
 @Test void seedanceCompatibilitySubmissionPreservesTemplatePromptAndEveryReferenceRole()throws Exception{
  var bodies=new ArrayList<JsonNode>();var paths=new ArrayList<String>();
  var client=new BluOctoVideoClient(new VideoCloudProperties()) {
   @Override protected JsonNode json(String method,String path,Map<String,Object> body) {
    paths.add(path);bodies.add(CloudVideoContractTest.this.mapper.valueToTree(body));
    return CloudVideoContractTest.this.mapper.createObjectNode().put("task_id","offline-seedance");
   }
  };
  var refs=List.of(new CloudVideoRequest.Reference(1,"first_frame"),new CloudVideoRequest.Reference(2,"last_frame"),
    new CloudVideoRequest.Reference(3,"reference_image"),new CloudVideoRequest.Reference(4,"reference_video"),new CloudVideoRequest.Reference(5,"reference_audio"));
  for(var p:profiles())if(p.path("family").asText().equals("Seedance")) {
   for(var reference:List.of(List.<CloudVideoRequest.Reference>of(),refs)) {
    var request=new CloudVideoRequest(p.path("id").asText(),reference.isEmpty()?"T2V":"R2V","中文模板提示词".repeat(200),10,"480p","16:9",reference,true,null,42L,"offline");
    var remote=client.submit(request,p,r->"https://example.org/"+r.assetId());var body=bodies.getLast();
    assertEquals("/v1/video/generations",paths.getLast());assertEquals("generations",remote.protocol());
    assertEquals(request.prompt(),body.path("prompt").asText());assertTrue(body.path("seconds").isTextual());assertEquals("10",body.path("seconds").asText());
    assertFalse(body.has("duration"));assertFalse(body.has("content"));assertFalse(body.has("generate_audio"));
    assertEquals("480p",body.at("/metadata/resolution").asText());assertEquals("16:9",body.at("/metadata/ratio").asText());assertEquals(42,body.at("/metadata/seed").asInt());
    assertEquals(p.path("hasAudioOutput").asBoolean(),body.path("metadata").has("generate_audio"));
    if(p.path("hasAudioOutput").asBoolean())assertTrue(body.at("/metadata/generate_audio").asBoolean());
    assertEquals(reference.size()+1,body.at("/metadata/content").size());
    for(int i=0;i<reference.size();i++) {
     var item=body.at("/metadata/content").get(i+1);var ref=reference.get(i);assertEquals(ref.role(),item.path("role").asText());
     String type=ref.role().equals("reference_video")?"video_url":ref.role().equals("reference_audio")?"audio_url":"image_url";
     assertEquals("https://example.org/"+ref.assetId(),item.path(type).path("url").asText());
    }
   }
  }
 }
 @Test void klingTailAndRatioAreNotLost()throws Exception{
  var d=draft(profile("kling-3.0"),List.of(new CloudVideoRequest.Reference(2,"last_frame"),new CloudVideoRequest.Reference(1,"first_frame")),"FL2V");
  JsonNode payload=mapper.valueToTree(BluOctoVideoClient.payload(d,"Kling",r->"https://example.org/"+r.assetId()));
  assertEquals("https://example.org/1",payload.path("input_reference").asText());assertEquals("https://example.org/2",payload.at("/metadata/image_tail").asText());assertEquals("16:9",payload.at("/metadata/aspect_ratio").asText());
 }
 @Test void compatibilityFieldsReachKlingAndMiniMaxWithoutLosingOutputControls()throws Exception{
  var d=new CloudVideoRequest("kling-2.6","FL2V","scene",10,"720p","9:16",List.of(new CloudVideoRequest.Reference(1,"first_frame"),new CloudVideoRequest.Reference(2,"last_frame")),false,null,null,"offline");
  var body=mapper.valueToTree(BluOctoVideoClient.payload(d,"Kling",r->"https://example.org/"+r.assetId()));
  assertEquals("10",body.path("seconds").asText());assertTrue(body.path("seconds").isTextual());
  assertEquals("std",body.at("/metadata/mode").asText());assertEquals("10",body.at("/metadata/duration").asText());
  assertEquals("9:16",body.path("aspect_ratio").asText());assertEquals("https://example.org/1",body.path("start_frame_url").asText());assertEquals("https://example.org/2",body.path("end_frame_url").asText());
  var h3=new CloudVideoRequest("MiniMax-H3","T2V","scene",4,"768p","16:9",List.of(),false,null,null,"offline");
  var mini=mapper.valueToTree(BluOctoVideoClient.payload(h3,"MiniMax",r->"unused"));
  assertEquals("768P",mini.at("/metadata/resolution").asText());assertEquals(4,mini.at("/metadata/duration").asInt());assertEquals("16:9",mini.at("/metadata/ratio").asText());
 }
 @Test void legacyWanModelsUseAdvertisedCompatibilitySubmissionAndKeepNativeRecovery()throws Exception{
  var paths=new ArrayList<String>();var bodies=new ArrayList<JsonNode>();
  var client=new BluOctoVideoClient(new VideoCloudProperties()) {
   @Override protected JsonNode json(String method,String path,Map<String,Object> body) {
    paths.add(method+" "+path);bodies.add(CloudVideoContractTest.this.mapper.valueToTree(body));
    return CloudVideoContractTest.this.mapper.createObjectNode().put("task_id","existing");
   }
  };
  for(String model:List.of("wan2.7-t2v","wan2.7-i2v")) {
   var refs=model.endsWith("i2v")?List.of(new CloudVideoRequest.Reference(1,"first_frame")):List.<CloudVideoRequest.Reference>of();
   var req=new CloudVideoRequest(model,refs.isEmpty()?"T2V":"I2V","scene",5,"720p","16:9",refs,false,null,null,"offline");
   var task=client.submit(req,profile(model),r->"https://example.org/image.png");assertEquals("generations",task.protocol());
   assertEquals("POST /v1/video/generations",paths.getLast());assertEquals("scene",bodies.getLast().path("prompt").asText());assertTrue(bodies.getLast().path("seconds").isTextual());
   if(!refs.isEmpty())assertEquals("https://example.org/image.png",bodies.getLast().path("image").asText());
   client.query(task);assertEquals("GET /v1/tasks/existing",paths.getLast());
   client.query(new BluOctoVideoClient.RemoteTask("old","alibaba"));assertEquals("GET /ali/api/v1/tasks/old",paths.getLast());
  }
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
  @Test void seedancePixelTablesAcceptActualMini480AndRejectWrongModelGeometry() {
   var mini=new CloudVideoRequest("doubao-seedance-2-0-mini-260615","T2V","scene",4,"480p","16:9",List.of(),true,null,null,"receipt");
   assertDoesNotThrow(()->VideoCloudService.validateOutput(mini,864,496));
   assertThrows(VideoTaskException.class,()->VideoCloudService.validateOutput(mini,854,480));
   assertThrows(VideoTaskException.class,()->VideoCloudService.validateOutput(mini,1280,720));
   var square=new CloudVideoRequest(mini.model(),"T2V","scene",4,"720p","1:1",List.of(),true,null,null,"square");
   assertDoesNotThrow(()->VideoCloudService.validateOutput(square,960,960));
   var wide=new CloudVideoRequest(mini.model(),"T2V","scene",4,"720p","21:9",List.of(),true,null,null,"wide");
   assertDoesNotThrow(()->VideoCloudService.validateOutput(wide,1470,630));
   var adaptive=new CloudVideoRequest(mini.model(),"T2V","scene",4,"480p","adaptive",List.of(),true,null,null,"adaptive");
   assertDoesNotThrow(()->VideoCloudService.validateOutput(adaptive,864,496));
   assertThrows(VideoTaskException.class,()->VideoCloudService.validateOutput(adaptive,1920,1080));
  }
 @Test void actualArtifactKeysAndNotStartAreRecognized()throws Exception{
  assertEquals("RUNNING",BluOctoVideoClient.state(mapper.readTree("{\"status\":\"NOT_START\"}")));
  assertEquals("result_mp4",BluOctoVideoClient.artifactKey(mapper.readTree("{\"artifacts\":[{\"key\":\"poster\",\"mime_type\":\"image/png\"},{\"key\":\"result_mp4\",\"mime_type\":\"video/mp4\"}]}")));
  assertThrows(VideoTaskException.class,()->BluOctoVideoClient.artifactKey(mapper.readTree("{\"artifacts\":[{\"key\":\"../bad\",\"type\":\"video\"}]}")));
 }
}
