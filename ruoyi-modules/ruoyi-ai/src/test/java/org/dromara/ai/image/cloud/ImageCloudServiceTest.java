package org.dromara.ai.image.cloud;

import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageCloudServiceTest {
    ImageTaskRepository repo = mock(ImageTaskRepository.class);
    ImageAssetStore assets = mock(ImageAssetStore.class);
    BluOctoImageClient client = mock(BluOctoImageClient.class);
    ImageCloudService service = new ImageCloudService(new ImageCloudProperties(), client, repo, assets, new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet, (model,cap) -> CloudImageRequest.testedModel(model) && "T2I".equals(cap));

    Map<String, Object> task(String status) {
        return Map.of("workflow_code", ImageCloudService.WORKFLOW, "status", status,
            "model_code", "gpt-image-2.5-flare", "prompt", "flower", "task_no", "IMAGE-1");
    }

    @Test void duplicateExecutionIsClaimedOnce() throws Exception {
        try {
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(task("QUEUED"));
            when(repo.listEvents(1, "tenant")).thenReturn(List.of(Map.of("sequence", 1)));
            when(repo.transition(1, ImageTaskStatus.QUEUED, ImageTaskStatus.RUNNING, null, null)).thenReturn(1, 0);
            when(client.generate("gpt-image-2.5-flare", "flower")).thenThrow(new ImageTaskException("CLOUD_AUTH_FAILED", "无权限"));
            assertEquals("ACCEPTED", service.execute(1, "tenant", 2));
            assertEquals("ALREADY_CLAIMED", service.execute(1, "tenant", 2));
            verify(client, timeout(3000).times(1)).generate("gpt-image-2.5-flare", "flower");
            verify(repo, timeout(3000)).markFailedIfActive(1, "CLOUD_AUTH_FAILED", "无权限");
        } finally { service.shutdown(); }
    }

    @Test void realImageIsArchivedWithMeasuredDimensionsAndTenantOwnership() throws Exception {
        try {
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(task("QUEUED"));
            when(repo.listEvents(1, "tenant")).thenReturn(List.of(Map.of("sequence", 1)));
            when(repo.transition(1, ImageTaskStatus.QUEUED, ImageTaskStatus.RUNNING, null, null)).thenReturn(1);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(12, 18, BufferedImage.TYPE_INT_ARGB), "png", bytes);
            when(client.generate("gpt-image-2.5-flare", "flower")).thenReturn(bytes.toByteArray());
            when(assets.storeOutput(eq("tenant"), eq(2L), eq(1L), anyString(), any(), eq("image/png"))).thenReturn("owned-key");
            assertEquals("ACCEPTED", service.execute(1, "tenant", 2));
            verify(repo, timeout(3000)).markSucceeded(eq(1L), anyLong(), eq(12), eq(18), eq(true), eq((long) bytes.size()));
            var capture = org.mockito.ArgumentCaptor.forClass(ImageTaskRepository.AssetRow.class);
            verify(repo).insertAsset(capture.capture());
            assertEquals("tenant", capture.getValue().tenantId());
            assertEquals(2, capture.getValue().userId());
            assertEquals("OUTPUT", capture.getValue().sourceKind());
        } finally { service.shutdown(); }
    }

    @Test void supplier503NeverMarksTaskSucceededAndBlocksPaidResubmission() throws Exception {
        try {
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(task("QUEUED"));
            when(repo.listEvents(1, "tenant")).thenReturn(List.of(Map.of("sequence", 1)));
            when(repo.transition(1, ImageTaskStatus.QUEUED, ImageTaskStatus.RUNNING, null, null)).thenReturn(1);
            when(client.generate("gpt-image-2.5-flare", "flower"))
                .thenThrow(new ImageTaskException("CLOUD_HTTP_ERROR", "云端接口返回 HTTP 503"));
            assertEquals("ACCEPTED", service.execute(1, "tenant", 2));
            verify(repo, timeout(3000)).markFailedIfActive(1, "CLOUD_RESULT_UNKNOWN", "云端接口返回 HTTP 503");
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(Map.of("workflow_code", ImageCloudService.WORKFLOW,
                "status", "FAILED", "error_code", "CLOUD_RESULT_UNKNOWN"));
            assertThrows(ImageTaskException.class, () -> service.retry(1, "tenant", 2));
            verify(client, times(1)).generate("gpt-image-2.5-flare", "flower");
            verifyNoInteractions(assets);
            verify(repo, never()).markSucceeded(anyLong(), anyLong(), anyInt(), anyInt(), anyBoolean(), anyLong());
        } finally { service.shutdown(); }
    }

    @Test void unknownResultCannotBeResubmittedByRetry() {
        try {
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(Map.of("workflow_code", ImageCloudService.WORKFLOW,
                "status", "FAILED", "error_code", "CLOUD_RESULT_UNKNOWN"));
            assertThrows(ImageTaskException.class, () -> service.retry(1, "tenant", 2));
            verifyNoInteractions(client);
            verify(repo, never()).reopen(anyLong(), anyString(), anyLong(), any());
        } finally { service.shutdown(); }
    }

    @Test void foreignTaskIsRejectedBeforeCloudRequest() {
        try {
            when(repo.requireOwnedTask(1, "tenant", 2)).thenThrow(new ImageTaskException("TASK_NOT_FOUND", "无权访问"));
            assertThrows(ImageTaskException.class, () -> service.execute(1, "tenant", 2));
            verifyNoInteractions(client);
        } finally { service.shutdown(); }
    }

    @Test void creationIdempotencyCannotReuseLocalTaskOrDifferentModel() {
        try {
            when(repo.findByIdempotencyKey("tenant", 2, "key-123456")).thenReturn(1L);
            when(repo.requireOwnedTask(1, "tenant", 2)).thenReturn(task("QUEUED"));
            assertThrows(ImageTaskException.class,
                () -> service.create("tenant", 2, null, "gpt-image-2.5-sunburst", "flower", "", "key-123456"));
            verify(repo, never()).insertTask(any());
        } finally { service.shutdown(); }
    }
    @Test void unverifiedAbilitiesAreRejectedBeforeTaskInsertOrPaidRequest() {
        try {
            for (String mode : List.of("EDIT", "MULTI", "MASK", "OUTPAINT", "TRANSPARENT")) {
                List<Long> refs = "MULTI".equals(mode) ? List.of(5L,6L) : "TRANSPARENT".equals(mode) ? List.of() : List.of(5L);
                Long mask = List.of("MASK", "OUTPAINT").contains(mode) ? 7L : null;
                var error = assertThrows(ImageTaskException.class, () -> service.create("tenant",2,null,
                    new CloudImageRequest("gpt-image-2.5-flare","flower",mode,refs,mask),"", "new-unique-key"));
                assertEquals("CLOUD_CAPABILITY_UNVERIFIED", error.getErrorCode());
            }
            verify(repo, never()).insertTask(any());
            verify(client, never()).generate(anyString(), anyString());
            verify(client, never()).generate(any(CloudImageRequest.class), anyList());
            verifyNoInteractions(assets);
        } finally { service.shutdown(); }
    }

    @Test void failedModelIsRejectedByProductionGateWithoutPaidCall() {
        var productionGate = new ImageCloudService(new ImageCloudProperties(),client,repo,assets,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet);
        try {
            for (String model : List.of("flux-2-pro")) {
                assertEquals("CLOUD_CAPABILITY_UNVERIFIED",assertThrows(ImageTaskException.class,
                    () -> productionGate.create("tenant",2,null,model,"flower","","new-key-123")).getErrorCode());
            }
            verify(repo,never()).insertTask(any()); verifyNoInteractions(assets);
            verify(client,never()).generate(anyString(),anyString());
        } finally { productionGate.shutdown(); service.shutdown(); }
    }

    @Test void verifiedReferenceEditUsesOwnedMaterialsAndArchivesTheResult() throws Exception {
        var verifiedService = new ImageCloudService(new ImageCloudProperties(),client,repo,assets,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet);
        try {
            var request = new CloudImageRequest("gpt-image-2.5-sunburst","blue vase","EDIT",List.of(5L),null);
            var source = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(12,18,BufferedImage.TYPE_INT_RGB),"png",source);
            when(repo.requireOwnedAsset(5,"tenant",2)).thenReturn(new ImageTaskRepository.AssetRow(5,"tenant",2,null,"IMAGE","UPLOAD","ref.png","ref-key","image/png",source.size(),null,12,18,false,null));
            when(assets.read("ref-key")).thenReturn(source.toByteArray());
            when(repo.requireOwnedTask(1,"tenant",2)).thenReturn(Map.of("workflow_code",ImageCloudService.WORKFLOW,"status","QUEUED","model_code",request.model(),"prompt",request.prompt(),"input_json",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("request",request))));
            when(repo.transition(1,ImageTaskStatus.QUEUED,ImageTaskStatus.RUNNING,null,null)).thenReturn(1);
            when(repo.listEvents(1,"tenant")).thenReturn(List.of());
            when(client.generate(eq(request),anyList())).thenReturn(source.toByteArray());
            when(assets.storeOutput(eq("tenant"),eq(2L),eq(1L),anyString(),any(),eq("image/png"))).thenReturn("result-key");
            assertEquals("ACCEPTED",verifiedService.execute(1,"tenant",2));
            verify(repo,timeout(3000)).markSucceeded(eq(1L),anyLong(),eq(12),eq(18),eq(false),eq((long)source.size()));
            var capture = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(client).generate(eq(request),capture.capture());
            assertEquals(1,capture.getValue().size());
            verify(client,never()).generate(anyString(),anyString());
            verify(repo).requireOwnedAsset(5,"tenant",2);
        } finally { verifiedService.shutdown(); service.shutdown(); }
    }
    @Test void transparentCapabilityRejectsAnOpaqueOutputRatherThanReportingSuccess() throws Exception {
        var verifiedService = new ImageCloudService(new ImageCloudProperties(),client,repo,assets,new java.util.concurrent.atomic.AtomicLong(100)::incrementAndGet);
        try {
            var request = new CloudImageRequest("gpt-image-2.5-sunburst","cutout","TRANSPARENT",List.of(),null);
            when(repo.requireOwnedTask(1,"tenant",2)).thenReturn(Map.of("workflow_code",ImageCloudService.WORKFLOW,"status","QUEUED","model_code",request.model(),"prompt",request.prompt(),"input_json",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("request",request))));
            when(repo.transition(1,ImageTaskStatus.QUEUED,ImageTaskStatus.RUNNING,null,null)).thenReturn(1);
            when(repo.listEvents(1,"tenant")).thenReturn(List.of());
            var opaque = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(12,18,BufferedImage.TYPE_INT_RGB),"png",opaque);
            when(client.generate(eq(request),anyList())).thenReturn(opaque.toByteArray());
            assertEquals("ACCEPTED",verifiedService.execute(1,"tenant",2));
            verify(repo,timeout(3000)).markFailedIfActive(1,"CLOUD_RESULT_UNKNOWN","透明背景输出未包含有效透明区域");
            verify(assets,never()).storeOutput(anyString(),anyLong(),anyLong(),anyString(),any(),anyString());
            verify(repo,never()).markSucceeded(anyLong(),anyLong(),anyInt(),anyInt(),anyBoolean(),anyLong());
        } finally { verifiedService.shutdown(); service.shutdown(); }
    }

}
