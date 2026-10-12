package org.dromara.ai.image.template;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.video.cloud.VideoCloudTenantResolver;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Separate video feed API; provider origin, key and raw binding stay server-side. */
@RestController
@RequestMapping("/video/templates")
@ConditionalOnProperty(name={"image.enabled","image.template-feed.enabled","video.enabled"},havingValue="true")
public class VideoTemplateController {
    private final TemplateFeedCache cache;
    private final TemplateFeedProperties properties;
    private final JdbcTemplate jdbc;
    public VideoTemplateController(TemplateFeedCache cache,TemplateFeedProperties properties,JdbcTemplate jdbc) { this.cache=cache;this.properties=properties;this.jdbc=jdbc; }
    private void authorize() { Long user=LoginHelper.getUserId();if(user==null) throw new TemplateFeedException(401,"请先登录");VideoCloudTenantResolver.resolve(jdbc,user); }
    private JsonNode requireVideo(String id) { var t=cache.require(id);if(!"video".equals(t.path("type").asText())) throw new TemplateFeedException(404,"视频模板不存在");return t; }
    @GetMapping @SaCheckPermission("video:creation:view")
    public R<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6",name="page_size") int size,
                                     @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String keyword) {
        authorize();cache.refresh();
        if(page<1 || page>100000 || size<1 || size>100 || keyword.length()>200) throw new TemplateFeedException(422,"分页或筛选参数无效");
        var templates=cache.browsable()?new ArrayList<>(cache.current().templates().values()):new ArrayList<JsonNode>();
        templates.removeIf(t->!"video".equals(t.path("type").asText()) || !"published".equals(t.path("status").asText()));
        var categories=templates.stream().map(t->t.path("category").asText()).distinct().sorted().map(c->Map.of("id",c,"zh",c,"en",c)).toList();
        templates.removeIf(t->(!category.isBlank() && !category.equals(t.path("category").asText())) || (!keyword.isBlank() && !(t.path("title").toString()+t.path("tags")).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))));
        templates.sort(Comparator.<JsonNode,String>comparing(t->t.path("updated_at").asText()).reversed().thenComparing(t->t.path("id").asText()));
        return R.ok(Map.of("items",templates.stream().skip((long)(page-1)*size).limit(size).map(t->VideoTemplateDrafts.project(cache,t)).toList(),"total",templates.size(),"categories",categories,"feed",cache.state()));
    }
    @GetMapping("/{id}") @SaCheckPermission("video:creation:view")
    public R<Map<String,Object>> detail(@PathVariable String id) { authorize();cache.refresh();if(!cache.browsable()) throw new TemplateFeedException(503,"模板缓存已过期");return R.ok(VideoTemplateDrafts.project(cache,requireVideo(id))); }
    @GetMapping("/{id}/cover") @SaCheckPermission("video:creation:view")
    public ResponseEntity<byte[]> cover(@PathVariable String id) { authorize();var t=requireVideo(id);String name=t.path("cover").path("url").asText();String type=name.endsWith(".webp")?"image/webp":name.endsWith(".jpg")?"image/jpeg":"image/png";return ResponseEntity.ok().header("Cache-Control","private, max-age=600").header("X-Content-Type-Options","nosniff").header("Content-Type",type).body(cache.cover(id)); }
    @PostMapping("/prepare") @SaCheckPermission("video:creation:view")
    public R<Map<String,Object>> prepare(@RequestBody Map<String,Object> body) { authorize();return R.ok(VideoTemplateDrafts.prepare(cache,properties,TemplateFeedCache.JSON.valueToTree(body))); }
    @ExceptionHandler(TemplateFeedException.class)
    public ResponseEntity<R<Map<String,Object>>> invalid(TemplateFeedException exception) { var result=R.fail(exception.getMessage(),Map.<String,Object>of("errors",exception.errors));result.setCode(exception.status);return ResponseEntity.status(exception.status).body(result); }
}
