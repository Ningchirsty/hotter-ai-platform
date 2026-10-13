package org.dromara.ai.image.cloud;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
class CloudImageRendererTest {
    byte[] image(boolean alpha)throws Exception {
        var image=new BufferedImage(80,40,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);
        image.setRGB(40,20,0xffff0000);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    @Test void deliversEverySupportedSizeAndFormatWithMeasuredMetadata()throws Exception {
        for(String size:java.util.List.of("1024x1024","1536x1024","1024x1536","2048x2048"))
            for(String format:java.util.List.of("png","jpeg","webp")) {
                var content=CloudImageRenderer.render(image(false),new CloudImageOutput(size,1,null,format),"T2I").content();
                var probe=CloudImageRenderer.probe(content);assertEquals(size,probe.width()+"x"+probe.height());assertEquals(format,probe.format().toLowerCase(java.util.Locale.ROOT));
                assertNotNull(CloudImageRenderer.decode(content));
            }
    }
    @Test void transparentWebpKeepsActualAlphaAndExistingDefaultBytesAreUnchanged()throws Exception {
        byte[] original=image(true);assertSame(original,CloudImageRenderer.render(original,CloudImageOutput.DEFAULT,"T2I").content());
        byte[] webp=CloudImageRenderer.render(original,new CloudImageOutput(null,1,null,"webp"),"TRANSPARENT").content();
        assertEquals(0,CloudImageRenderer.decode(webp).getRGB(0,0)>>>24);
        assertEquals(0xffff0000,CloudImageRenderer.decode(webp).getRGB(40,20));
    }
    @Test void invalidAndOversizedInputNeverRunsAnEncoder() {
        assertThrows(Exception.class,()->CloudImageRenderer.render(new byte[0],CloudImageOutput.DEFAULT,"T2I"));
        assertThrows(Exception.class,()->CloudImageRenderer.render(new byte[20*1024*1024+1],CloudImageOutput.DEFAULT,"T2I"));
    }
}
