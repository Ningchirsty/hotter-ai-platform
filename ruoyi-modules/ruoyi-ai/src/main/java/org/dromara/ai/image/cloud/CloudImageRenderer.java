package org.dromara.ai.image.cloud;

import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetProbe;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Archive the requested dimensions and encoding without another paid model request. */
public final class CloudImageRenderer {
    record Result(byte[] content) { }
    private static final int MAX_PIXELS = 16 * 1024 * 1024, MAX_BYTES = 20 * 1024 * 1024;
    private CloudImageRenderer() { }
    static Result render(byte[] content, CloudImageOutput output, String capability) {
        var head = probe(content);
        if (!head.measured() || head.exceedsPixels(MAX_PIXELS)) throw invalid("云端图像无法识别或超过像素上限");
        String format = output.outputFormat() == null ? head.format().toLowerCase(Locale.ROOT) : output.outputFormat();
        int width = head.width(), height = head.height();
        if (output.size() != null) { var size = output.size().split("x"); width=Integer.parseInt(size[0]);height=Integer.parseInt(size[1]); }
        if (width==head.width() && height==head.height() && format.equalsIgnoreCase(head.format())) return new Result(content);
        var image = decode(content);
        if (width!=image.getWidth() || height!=image.getHeight()) {
            var scaled = new BufferedImage(width, height, image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            var graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.max((double)width/image.getWidth(), (double)height/image.getHeight());
                int w=(int)Math.ceil(image.getWidth()*scale), h=(int)Math.ceil(image.getHeight()*scale);
                graphics.drawImage(image,(width-w)/2,(height-h)/2,w,h,null);
            } finally { graphics.dispose(); }
            image=scaled;
        }
        if ("jpeg".equals(format) && image.getColorModel().hasAlpha()) {
            var opaque = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=opaque.createGraphics();
            try { graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);graphics.drawImage(image,0,0,null); }
            finally { graphics.dispose(); }
            image=opaque;
        }
        try {
            var bytes=new ByteArrayOutputStream();
            if (!ImageIO.write(image,"webp".equals(format)?"png":format,bytes)) throw invalid("目标图片格式无法编码");
            byte[] rendered="webp".equals(format)?transcode(bytes.toByteArray(),true):bytes.toByteArray();
            var measured=probe(rendered);
            if (!measured.measured() || measured.width()!=width || measured.height()!=height || !format.equalsIgnoreCase(measured.format())) throw invalid("成品尺寸或格式转换未通过校验");
            if (rendered.length>MAX_BYTES) throw invalid("成品超过 20MB 大小上限");
            return new Result(rendered);
        } catch(IOException e) { throw invalid("成品图片无法编码"); }
    }
    public static BufferedImage decode(byte[] content) {
        var head=probe(content);
        if (!head.measured() || head.exceedsPixels(MAX_PIXELS)) throw invalid("图片像素大小无效");
        try {
            var image=ImageIO.read(new ByteArrayInputStream("webp".equalsIgnoreCase(head.format())?transcode(content,false):content));
            if(image==null)throw invalid("云端图片无法解码");
            return image;
        } catch(IOException e) { throw invalid("云端图片无法解码"); }
    }
    public static ImageAssetProbe.Probe probe(byte[] content) {
        if(content==null || content.length==0 || content.length>MAX_BYTES)return ImageAssetProbe.Probe.unmeasured(0);
        // Read WebP's bounded RIFF canvas before invoking the installed codec.
        if(content.length>=12 && ascii(content,0,"RIFF") && ascii(content,8,"WEBP")) {
            if(content.length<30)return ImageAssetProbe.Probe.unmeasured(content.length);
            for(int offset=12;offset+8<=content.length;) {
                long size=u32(content,offset+4);int data=offset+8;
                if(size>content.length-data)break;
                int w=0,h=0;boolean alpha=false;
                if(ascii(content,offset,"VP8X") && size>=10) {w=1+u24(content,data+4);h=1+u24(content,data+7);alpha=(content[data]&16)!=0;}
                else if(ascii(content,offset,"VP8L") && size>=5 && (content[data]&255)==47) {long bits=u32(content,data+1);w=1+(int)(bits&16383);h=1+(int)((bits>>14)&16383);alpha=(bits&(1L<<28))!=0;}
                else if(ascii(content,offset,"VP8 ") && size>=10 && (content[data+3]&255)==157 && (content[data+4]&255)==1 && (content[data+5]&255)==42) {w=((content[data+6]&255)|((content[data+7]&255)<<8))&16383;h=((content[data+8]&255)|((content[data+9]&255)<<8))&16383;}
                if(w>0&&h>0)return new ImageAssetProbe.Probe(w,h,"webp",alpha,content.length,true);
                offset=(int)(data+size+(size&1));
            }
            return ImageAssetProbe.Probe.unmeasured(content.length);
        }
        return ImageAssetProbe.probeBytes(content);
    }
    private static boolean ascii(byte[] b,int offset,String text) {for(int i=0;i<text.length();i++)if(b[offset+i]!=text.charAt(i))return false;return true;}
    private static int u24(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16);}
    private static long u32(byte[] b,int o){return (b[o]&255L)|((b[o+1]&255L)<<8)|((b[o+2]&255L)<<16)|((b[o+3]&255L)<<24);}
    private static byte[] transcode(byte[] content,boolean webp) {
        Path directory=null;Process process=null;
        try {
            directory=Files.createTempDirectory("cloud-image-format-");Path input=directory.resolve("input"),output=directory.resolve(webp?"output.webp":"output.png");Files.write(input,content);
            var command=new ArrayList<>(java.util.List.of("ffmpeg","-nostdin","-y","-v","error","-threads","2","-protocol_whitelist","file,pipe"));
            if(!webp)command.addAll(java.util.List.of("-f","webp_pipe"));
            command.addAll(java.util.List.of("-i",input.toString(),"-frames:v","1","-threads","2"));
            if(webp)command.addAll(java.util.List.of("-c:v","libwebp","-lossless","1"));
            command.add(output.toString());
            process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(15,TimeUnit.SECONDS)||process.exitValue()!=0||!Files.isRegularFile(output)||Files.size(output)>MAX_BYTES)throw invalid("图片格式转换失败，请检查生成服务编码器");
            return Files.readAllBytes(output);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw invalid("图片格式转换已中断");}
        catch(IOException e){throw invalid("图片格式转换服务不可用");}
        finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();try{process.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            if(directory!=null){try(var files=Files.list(directory)){for(Path file:files.toList())Files.deleteIfExists(file);}catch(IOException ignored){}try{Files.deleteIfExists(directory);}catch(IOException ignored){}}
        }
    }
    private static ImageTaskException invalid(String message){return new ImageTaskException("CLOUD_OUTPUT_INVALID",message);}
}
