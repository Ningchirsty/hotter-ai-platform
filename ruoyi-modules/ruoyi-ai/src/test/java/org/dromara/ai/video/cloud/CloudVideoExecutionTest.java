package org.dromara.ai.video.cloud;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 任务完整链路离线复现，供应商替身不会发出网络请求。 */
class CloudVideoExecutionTest {
 @TempDir Path temp;
 @Test void measuredSuccessArchivesAndOpensOnlyItsExactCombination()throws Exception{run(true);}
 @Test void missingArtifactFailsWithoutPromotingOrResubmitting()throws Exception{run(false);}
 @Test void recoverExistingRemoteAfterUnknownQueryNeverResubmitsOrConsumesBudget()throws Exception{
  var p=new VideoCloudProperties();p.setEnabled(true);Path key=temp.resolve("key");Files.writeString(key,"offline-key");p.setApiKeyFile(key.toString());p.setVerificationFile(temp.resolve("ledger.json").toString());p.setValidationLimit(4);p.setValidationUserIds(List.of(7L));p.setValidationVariants(List.of("wan2.7-t2v|T2V|5|720p|16:9|true"));
  var request=new CloudVideoRequest("wan2.7-t2v","T2V","scene",5,"720p","16:9",List.of(),true,null,null,"once");
  var registry=new VideoCloudVerification(p);registry.reserve(request,7,11);
  assertThrows(VideoTaskException.class,()->registry.requireRecovery(request,8,11));
  assertThrows(VideoTaskException.class,()->registry.requireRecovery(request,7,12));
  var repo=mock(VideoTaskRepository.class);var storage=mock(AssetStorage.class);var probe=mock(MediaProbe.class);var client=mock(BluOctoVideoClient.class);
  var task=Map.<String,Object>of("workflow_code",VideoCloudService.WORKFLOW,"status","FAILED","comfy_prompt_id","existing-remote","input_json",new ObjectMapper().writeValueAsString(Map.of("request",request)));
  when(repo.requireOwnedTask(11,"tenant",7)).thenReturn(task);when(repo.transition(11,VideoTaskStatus.QUEUED,VideoTaskStatus.RUNNING,null,null)).thenReturn(1);when(repo.listEvents(11,"tenant")).thenReturn(List.of());
  var remote=new BluOctoVideoClient.RemoteTask("existing-remote","alibaba");when(client.query(remote)).thenReturn(new ObjectMapper().readTree("{}"),new ObjectMapper().readTree("{\"status\":\"SUCCESS\"}"));
  when(client.download(remote)).thenReturn(new byte[]{1});when(storage.storeOutput(anyString(),anyLong(),anyLong(),anyString(),any(),anyString())).thenReturn("output");when(storage.localPath("output")).thenReturn(temp.resolve("result.mp4"));when(probe.probe(any())).thenReturn(new MediaProbe.Probe(1280,720,24.0,5000L,true));
  when(repo.markSucceeded(eq(11L),anyLong(),anyLong(),anyInt(),anyInt(),anyDouble(),anyLong(),anyBoolean())).thenReturn(1);
  var service=new VideoCloudService(p,repo,storage,probe,client,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet);
  try{
   assertEquals(true,service.execute(11,"tenant",7,true).get("accepted"));
   verify(repo,timeout(10000)).appendEvent(anyLong(),eq(11L),eq("tenant"),anyInt(),eq("SUCCEEDED"),anyString());
   verify(client,never()).submit(any(),any(),any());verify(client,times(2)).query(remote);verify(client,times(1)).download(remote);
   var finalRegistry=new VideoCloudVerification(p);assertEquals(3,finalRegistry.remaining());assertTrue(finalRegistry.verified(request));
  }finally{service.shutdown();}
 }
 void run(boolean success)throws Exception{
  var p=new VideoCloudProperties();p.setEnabled(true);Path key=temp.resolve("key");Files.writeString(key,"offline-key");p.setApiKeyFile(key.toString());p.setVerificationFile(temp.resolve("ledger.json").toString());p.setValidationLimit(4);p.setValidationUserIds(List.of(7L));p.setValidationVariants(List.of("wan2.7-t2v|T2V|5|720p|16:9|true"));
  var request=new CloudVideoRequest("wan2.7-t2v","T2V","scene",5,"720p","16:9",List.of(),true,null,null,"once");
  var repo=mock(VideoTaskRepository.class);var storage=mock(AssetStorage.class);var probe=mock(MediaProbe.class);var client=mock(BluOctoVideoClient.class);
  var task=Map.<String,Object>of("workflow_code",VideoCloudService.WORKFLOW,"status","QUEUED","input_json",new ObjectMapper().writeValueAsString(Map.of("request",request)));
  when(repo.requireOwnedTask(11,"tenant",7)).thenReturn(task);when(repo.transition(11,VideoTaskStatus.QUEUED,VideoTaskStatus.RUNNING,null,null)).thenReturn(1);when(repo.listEvents(11,"tenant")).thenReturn(List.of());
  var remote=new BluOctoVideoClient.RemoteTask("remote-offline","alibaba");when(client.submit(eq(request),any(),any())).thenReturn(remote);when(client.query(remote)).thenReturn(new ObjectMapper().readTree("{\"status\":\"SUCCESS\"}"));
  when(repo.markSucceeded(eq(11L),anyLong(),anyLong(),anyInt(),anyInt(),anyDouble(),anyLong(),anyBoolean())).thenReturn(1);
  if(success){when(client.download(remote)).thenReturn(new byte[]{1});when(storage.storeOutput(anyString(),anyLong(),anyLong(),anyString(),any(),anyString())).thenReturn("output");when(storage.localPath("output")).thenReturn(temp.resolve("result.mp4"));when(probe.probe(any())).thenReturn(new MediaProbe.Probe(1280,720,24.0,5000L,true));}
  else when(client.download(remote)).thenThrow(new VideoTaskException("CLOUD_ARTIFACT_MISSING","no artifact"));
  var service=new VideoCloudService(p,repo,storage,probe,client,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet);
  try{
   assertEquals(true,service.execute(11,"tenant",7,false).get("accepted"));
   if(success){verify(repo,timeout(5000)).markSucceeded(eq(11L),anyLong(),anyLong(),eq(1280),eq(720),eq(24.0),eq(5000L),eq(false));verify(repo,timeout(5000)).appendEvent(anyLong(),eq(11L),eq("tenant"),anyInt(),eq("SUCCEEDED"),anyString());}
   else verify(repo,timeout(5000)).markFailedIfActive(eq(11L),eq("CLOUD_ARTIFACT_MISSING"),anyString());
   verify(client,times(1)).submit(eq(request),any(),any());verify(client,times(1)).download(remote);
   var registry=new VideoCloudVerification(p);assertEquals(3,registry.remaining());assertEquals(success,registry.verified(request));
   assertFalse(registry.verified(new CloudVideoRequest(request.model(),request.capability(),"scene",10,"720p","16:9",List.of(),true,null,null,"other")));
  }finally{service.shutdown();}
 }
}
