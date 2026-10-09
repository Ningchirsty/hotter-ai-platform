package org.dromara.ai.video.cloud;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.*;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;
/** 云端视频接口；复用现有视频权限，参考读取采用短期不透明票据。 */
@RestController
@RequestMapping("/video/cloud")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="video",name="enabled",havingValue="true")
public class VideoCloudController extends BaseController {
    private final VideoCloudService cloud;
    private final VideoTaskRepository repository;
    private final AssetStorage storage;
    private final MediaProbe probe;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @GetMapping("/models") @SaCheckPermission("video:creation:view")
    public R<Map<String,Object>> models(){return R.ok(cloud.models(LoginHelper.getUserId()));}
    @PostMapping("/tasks") @SaCheckPermission("video:creation:submit")
    public R<Map<String,Object>> create(@RequestBody CloudVideoRequest request){return R.ok(cloud.create(request,tenant(),LoginHelper.getUserId(),LoginHelper.getDeptId()));}
    @PostMapping("/assets") @SaCheckPermission("video:creation:submit")
    public R<Map<String,Object>> upload(@RequestParam("file")MultipartFile file)throws java.io.IOException {
        String mime=file.getContentType();
        if(file.isEmpty()||file.getSize()>20L*1024*1024||mime==null||!List.of("image/png","image/jpeg","image/webp","video/mp4","audio/mpeg","audio/wav","audio/x-wav").contains(mime))throw VideoTaskException.invalidContract("请选择支持的图片、MP4 或音频文件，单个不超过 20 MB");
        String tenant=tenant();long user=LoginHelper.getUserId();byte[] bytes=file.getBytes();
        String kind=mime.startsWith("video")?"VIDEO":mime.startsWith("audio")?"AUDIO":"IMAGE";
        Integer width=null,height=null;Long duration=null;
        if(kind.equals("IMAGE")&&!mime.equals("image/webp")){
            try(var imageInput=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
                var readers=javax.imageio.ImageIO.getImageReaders(imageInput);
                if(!readers.hasNext())throw VideoTaskException.invalidContract("参考图片无法识别");
                var reader=readers.next();
                try{reader.setInput(imageInput);width=reader.getWidth(0);height=reader.getHeight(0);}
                finally{reader.dispose();}
            }
        }
        String key=storage.storeUpload(tenant,user,file.getOriginalFilename(),bytes,mime);
        if(kind.equals("IMAGE")&&mime.equals("image/webp")){var measured=probe.probe(storage.localPath(key));width=measured.width();height=measured.height();}
        if(kind.equals("IMAGE")&&(width==null||height==null||width<=0||height<=0||(long)width*height>32L*1024*1024))throw VideoTaskException.invalidContract("参考图片无法识别或像素过大");
        if(!kind.equals("IMAGE")){var p=probe.probe(storage.localPath(key));if(!p.measured()||p.durationMillis()==null||p.durationMillis()>30000)throw VideoTaskException.invalidContract("视频／音频需能探测且不超过 30 秒");duration=p.durationMillis();width=p.width();height=p.height();}
        long id=IdGeneratorUtil.nextLongId();repository.insertAsset(new VideoTaskRepository.AssetRow(id,tenant,user,null,kind,"UPLOAD",file.getOriginalFilename(),key,mime,file.getSize(),null,width,height,duration,LoginHelper.getDeptId()));return R.ok(Map.of("assetId",id));
    }
    @SaIgnore @GetMapping("/reference/{token}")
    public ResponseEntity<byte[]> reference(@PathVariable String token){var t=cloud.ticket(token);return ResponseEntity.ok().contentType(MediaType.parseMediaType(t.mime())).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(cloud.referenceContent(t));}
    @ExceptionHandler(VideoTaskException.class)
    public R<Void> invalid(VideoTaskException e){return R.fail(400,e.getMessage());}
    private String tenant(){
        Long user=LoginHelper.getUserId();if(user==null)throw VideoTaskException.invalidContract("当前未登录");
        var tenants=jdbc.queryForList("SELECT tenant_id FROM sys_user WHERE user_id = ?",String.class,user);
        if(tenants.isEmpty()||tenants.getFirst()==null||tenants.getFirst().isBlank())throw VideoTaskException.invalidContract("无法确认租户归属");return tenants.getFirst();
    }
}
