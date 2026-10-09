package org.dromara.ai.video.cloud;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CloudVideoServiceTest {
 final VideoTaskRepository repo=mock(VideoTaskRepository.class);
 final AssetStorage storage=mock(AssetStorage.class);final MediaProbe probe=mock(MediaProbe.class);
 final BluOctoVideoClient client=mock(BluOctoVideoClient.class);
 final VideoCloudProperties props=mock(VideoCloudProperties.class);
 VideoCloudService service;
 CloudVideoRequest request(){return new CloudVideoRequest("wan2.7-t2v","T2V","scene",5,"720p","16:9",List.of(),true,null,null,"idem");}
 @BeforeEach void setup(){when(props.configured()).thenReturn(true);when(props.getVerifiedVariants()).thenReturn(List.of("wan2.7-t2v|T2V|5|720p|16:9|true"));service=new VideoCloudService(props,repo,storage,probe,client);}
 @AfterEach void cleanup(){service.shutdown();verifyNoInteractions(client);}
 @Test void unverifiedModelNeverInsertsOrSubmits(){when(props.getVerifiedVariants()).thenReturn(List.of());assertThrows(VideoTaskException.class,()->service.create(request(),"tenant",7,1L));verify(repo,never()).insertTask(any());}
 @Test void noKeyNeverInsertsOrSubmits(){when(props.configured()).thenReturn(false);assertThrows(VideoTaskException.class,()->service.create(request(),"tenant",7,1L));verify(repo,never()).insertTask(any());}
 @Test void requestUnknownCannotBeRetried()throws Exception{
  var task=new HashMap<String,Object>();task.put("workflow_code",VideoCloudService.WORKFLOW);task.put("status","FAILED");task.put("error_code","CLOUD_RESULT_UNKNOWN");task.put("input_json",new ObjectMapper().writeValueAsString(Map.of("request",request())));
  when(repo.requireOwnedTask(1,"tenant",7)).thenReturn(task);
  assertThrows(VideoTaskException.class,()->service.execute(1,"tenant",7,true));verify(repo,never()).reopen(anyLong(),any());
 }
 @Test void atomicClaimStopsConcurrentDoubleSubmission()throws Exception{
  var task=new HashMap<String,Object>();task.put("workflow_code",VideoCloudService.WORKFLOW);task.put("status","RUNNING");task.put("input_json",new ObjectMapper().writeValueAsString(Map.of("request",request())));
  when(repo.requireOwnedTask(1,"tenant",7)).thenReturn(task);
  assertEquals(false,service.execute(1,"tenant",7,false).get("accepted"));
  verify(repo,never()).transition(anyLong(),any(),any(),any(),any());
 }
 @Test void externalReferenceNeedsReadAddress(){var r=new CloudVideoRequest("wan2.7-i2v","I2V","scene",5,"720p","16:9",List.of(new CloudVideoRequest.Reference(1,"first_frame")),true,null,null,"key");when(props.getVerifiedVariants()).thenReturn(List.of(VideoCloudVerification.variant(r)));assertThrows(VideoTaskException.class,()->service.create(r,"tenant",7,1L));verify(repo,never()).insertTask(any());}
}
