package org.dromara.ai.video.cloud;

import org.dromara.ai.video.exception.VideoTaskException;
import java.util.Map;

/** Provider pixel tables: https://docs.volcengine.com/docs/ark/create-video-generation-task-api */
final class SeedanceOutputGeometry {
 private SeedanceOutputGeometry() { }
 private static final Map<String,Map<String,String>> PIXELS=Map.of(
  "480p",Map.of("16:9","864x496","9:16","496x864","4:3","752x560","3:4","560x752","1:1","640x640","21:9","992x432"),
  "720p",Map.of("16:9","1280x720","9:16","720x1280","4:3","1112x834","3:4","834x1112","1:1","960x960","21:9","1470x630"),
  "1080p",Map.of("16:9","1920x1080","9:16","1080x1920","4:3","1664x1248","3:4","1248x1664","1:1","1440x1440","21:9","2206x946"),
  "4k",Map.of("16:9","3840x2160","9:16","2160x3840","4:3","3326x2494","3:4","2494x3326","1:1","2880x2880","21:9","4398x1886"));
 static boolean applies(String model){return model!=null && model.startsWith("doubao-seedance-2-");}
 static void validate(CloudVideoRequest request,int width,int height) {
  var sizes=PIXELS.get(request.resolution());
  if(sizes==null)throw VideoTaskException.outputInvalid("Seedance 输出分辨率未匹配官方规格");
  if(request.ratio().equals("adaptive")) {
   var pixels=sizes.values().stream().mapToLong(value->{var p=value.split("x");return Long.parseLong(p[0])*Long.parseLong(p[1]);}).summaryStatistics();
   long actual=(long)width*height;double ratio=(double)width/height;
   if(width<300 || height<300 || width>6000 || height>6000 || ratio<.4 || ratio>2.5 || actual<pixels.getMin()*.95 || actual>pixels.getMax()*1.05)throw VideoTaskException.outputInvalid("Seedance 自适应成片像素预算与分辨率档位不符");
   return;
  }
  String expected=sizes.get(request.ratio());
  if(request.model().startsWith("doubao-seedance-2-5-") && request.resolution().equals("480p")) {
   if(request.ratio().equals("16:9"))expected="854x480";
   if(request.ratio().equals("9:16"))expected="480x854";
  }
  if(expected==null || !expected.equals(width+"x"+height))throw VideoTaskException.outputInvalid("Seedance 成片像素与此型号、分辨率和比例的官方规格不符");
 }
}
