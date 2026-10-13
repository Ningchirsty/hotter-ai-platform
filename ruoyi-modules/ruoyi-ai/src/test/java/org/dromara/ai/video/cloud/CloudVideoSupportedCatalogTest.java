package org.dromara.ai.video.cloud;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CloudVideoSupportedCatalogTest {
 @Test void everyAdvertisedSpecificationHasAValidServerRequest()throws Exception {
  var catalog=new ObjectMapper().readTree(getClass().getResourceAsStream("/video/bluocto-catalog.json"));int checked=0;
  for(var p:catalog.path("profiles")) for(var cap:p.path("capabilities")) for(var seconds:p.path("durations")) for(var resolution:p.path("resolutions")) for(var ratio:p.path("ratios")) {
   if(p.path("family").asText().equals("MiniMax")&&cap.asText().equals("T2V")&&ratio.asText().equals("adaptive"))continue;
   List<CloudVideoRequest.Reference> refs=switch(cap.asText()){case "I2V"->List.of(new CloudVideoRequest.Reference(1,"first_frame"));case "FL2V"->List.of(new CloudVideoRequest.Reference(1,"first_frame"),new CloudVideoRequest.Reference(2,"last_frame"));case "R2V"->List.of(new CloudVideoRequest.Reference(1,"reference_image"));default->List.of();};
   var r=new CloudVideoRequest(p.path("id").asText(),cap.asText(),"scene",seconds.asInt(),resolution.asText(),ratio.asText(),refs,p.path("hasAudioOutput").asBoolean(),null,p.path("hasSeed").asBoolean()?12L:null,"catalog-spec");
   assertDoesNotThrow(()->r.validate(p));checked++;
  }
  assertTrue(checked>1000);
 }
 @Test void wanReferenceAudioIsAttachedToVisualSubjectInTheProviderContract()throws Exception {
  var r=new CloudVideoRequest("wan2.7-r2v","R2V","Image 1 walks",2,"720p","16:9",List.of(new CloudVideoRequest.Reference(1,"reference_audio"),new CloudVideoRequest.Reference(2,"reference_image")),true,null,null,"voice-test");
  var payload=BluOctoVideoClient.payload(r,"Wan",ref->"https://media.invalid/"+ref.assetId());
  var metadata=(Map<?,?>)payload.get("metadata");var input=(Map<?,?>)metadata.get("input");var media=(List<?>)input.get("media");
  assertEquals(1,media.size());assertEquals("reference_image",((Map<?,?>)media.get(0)).get("type"));assertEquals("https://media.invalid/1",((Map<?,?>)media.get(0)).get("reference_voice"));
 }
}
