package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudImageRequestTest {
    byte[] png(int width, int height, boolean alpha) throws Exception {
        var image=new BufferedImage(width,height,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
        var bytes=new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes); return bytes.toByteArray();
    }
    ImageTaskRepository.AssetRow asset(long id) {
        return new ImageTaskRepository.AssetRow(id,"tenant",2,null,"IMAGE","UPLOAD","input.png","key-"+id,"image/png",50,null,null,null,null,null);
    }
    @Test void rejectsDuplicateReferencesAndWrongMaterialContract() {
        assertThrows(ImageTaskException.class,()->new CloudImageRequest(null,"x","T2I",List.of(),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("gpt-image-2.5-flare","x","MULTI",List.of(1L,1L),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("gpt-image-2.5-flare","x","T2I",List.of(1L),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("gpt-image-2.5-flare","x","MASK",List.of(1L),null).validateShape());
    }
    @Test void foreignReferenceIsDeniedBeforeStorageRead() {
        var repo=mock(ImageTaskRepository.class); var store=mock(ImageAssetStore.class);
        when(repo.requireOwnedAsset(1,"tenant",2)).thenThrow(new ImageTaskException("ASSET_NOT_FOUND","无权访问"));
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("gpt-image-2.5-flare","x","EDIT",List.of(1L),null).inputs(repo,store,"tenant",2));
        verifyNoInteractions(store);
    }
    @Test void maskMustMatchReferenceAndContainFullyTransparentRegion() throws Exception {
        var repo=mock(ImageTaskRepository.class); var store=mock(ImageAssetStore.class);
        when(repo.requireOwnedAsset(1,"tenant",2)).thenReturn(asset(1)); when(repo.requireOwnedAsset(2,"tenant",2)).thenReturn(asset(2));
        when(store.read("key-1")).thenReturn(png(12,18,false));
        var request=new CloudImageRequest("gpt-image-2.5-flare","x","MASK",List.of(1L),2L);
        when(store.read("key-2")).thenReturn(png(18,12,true));
        assertThrows(ImageTaskException.class,()->request.inputs(repo,store,"tenant",2));
        when(store.read("key-2")).thenReturn(png(12,18,false));
        assertThrows(ImageTaskException.class,()->request.inputs(repo,store,"tenant",2));
        when(store.read("key-2")).thenReturn(png(12,18,true));
        var inputs=request.inputs(repo,store,"tenant",2); assertEquals(2,inputs.size()); assertTrue(inputs.get(1).mask());
    }
    @Test void malformedImageIsRejected() {
        var repo=mock(ImageTaskRepository.class); var store=mock(ImageAssetStore.class);
        when(repo.requireOwnedAsset(1,"tenant",2)).thenReturn(asset(1)); when(store.read("key-1")).thenReturn(new byte[]{1,2,3});
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("gpt-image-2.5-flare","x","EDIT",List.of(1L),null).inputs(repo,store,"tenant",2));
    }
    @Test void referenceLimitsFollowTheExactModelAndMasksAreNotAssumedForOtherFamilies() {
        assertDoesNotThrow(()->new CloudImageRequest("qwen-image-3.0","x","MULTI",List.of(1L,2L,3L),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("qwen-image-3.0","x","MULTI",List.of(1L,2L,3L,4L),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("qwen-image-3.0","x","MASK",List.of(1L),2L).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("flux-2-pro","x","EDIT",List.of(1L),null).validateShape());
        assertThrows(ImageTaskException.class,()->new CloudImageRequest("wan2.7-image-pro","x","MULTI",List.of(1L,2L,3L,4L,5L,6L,7L,8L,9L,10L),null).validateShape());
    }

    @Test void flareVerifiedAbilitiesAcceptTheirMaterialsButUnverifiedOutputStillCannotSubmit() {
        var output = new CloudImageOutput("1024x1536",1,null,null);
        for (String mode : List.of("T2I","MULTI","MASK","OUTPAINT","TRANSPARENT")) {
            var refs = List.of("T2I","TRANSPARENT").contains(mode) ? List.<Long>of()
                : "MULTI".equals(mode) ? List.of(1L,2L) : List.of(1L);
            Long mask = List.of("MASK","OUTPAINT").contains(mode) ? 3L : null;
            var size = "OUTPAINT".equals(mode) ? "1536x1024" : output.size();
            var request = new CloudImageRequest("gpt-image-2.5-flare","flower",mode,refs,mask,
                new CloudImageOutput(size,1,null,null));
            assertDoesNotThrow(request::requireVerified);
            assertDoesNotThrow(() -> CloudImageOutputValidation.requireVerified(request));
        }
        assertEquals("CLOUD_OUTPUT_PARAMETERS_UNVERIFIED",assertThrows(ImageTaskException.class,() ->
            CloudImageOutputValidation.requireVerified(new CloudImageRequest("gpt-image-2.5-flare","flower","T2I",List.of(),null,
                new CloudImageOutput("1024x1536",1,"high",null)))).getErrorCode());
    }

}
