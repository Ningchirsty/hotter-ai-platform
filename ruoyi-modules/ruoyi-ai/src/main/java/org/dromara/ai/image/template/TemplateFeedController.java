package org.dromara.ai.image.template;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.image.service.ImageInspirationTenantResolver;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 登录态模板 API，返回若依 R 包装；Feed 和原始 binding 从不下发。 */
@RestController
@RequestMapping("/image/templates")
@ConditionalOnProperty(prefix="image",name={"enabled","template-feed.enabled"},havingValue="true")
public class TemplateFeedController {
    private final TemplateFeedCache cache;
    private final TemplateGenerationService generation;
    private final ImageInspirationTenantResolver tenants;
    public TemplateFeedController(TemplateFeedCache cache,TemplateGenerationService generation,ImageInspirationTenantResolver tenants) { this.cache=cache; this.generation=generation; this.tenants=tenants; }
    private long user() { Long id=LoginHelper.getUserId(); if(id==null) throw new TemplateFeedException(401,"请先登录"); return id; }
    private String tenant(long user) { String t=tenants.resolve(user); if(t==null || t.isBlank()) throw new TemplateFeedException(401,"无法确认账号归属"); return t; }
    @GetMapping @SaCheckPermission("image:creation:view")
    public R<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6",name="page_size") int size,
                                      @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String keyword,
                                      @RequestParam(defaultValue="updated_at") String sort) {
        long user=user(); String tenant=tenant(user); cache.refresh();
        if(page<1 || page>100000 || size<1 || size>100 || keyword.length()>200 || !"updated_at".equals(sort)) throw new TemplateFeedException(422,"分页或筛选参数无效");
        var all=cache.browsable()?new ArrayList<>(cache.current().templates().values()):new ArrayList<JsonNode>();
        all.removeIf(t->!"published".equals(t.path("status").asText()));
        var categories=all.stream().map(t->t.path("category").asText()).distinct().sorted().map(c->Map.of("id",c,"zh",c,"en",c)).toList();
        all.removeIf(t->(!category.isBlank() && !category.equals(t.path("category").asText())) || (!keyword.isBlank() && !(t.path("title").toString()+t.path("tags")).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))));
        all.sort(Comparator.<JsonNode,String>comparing(t->t.path("updated_at").asText()).reversed().thenComparing(t->t.path("id").asText()));
        boolean validation=generation.canValidate(tenant,user);
        return R.ok(Map.of("items",all.stream().skip((long)(page-1)*size).limit(size).map(t->project(t,validation)).toList(),"total",all.size(),"categories",categories,"feed",cache.state()));
    }
    @GetMapping("/{id}") @SaCheckPermission("image:creation:view")
    public R<Map<String,Object>> detail(@PathVariable String id) { long user=user(); String tenant=tenant(user); cache.refresh(); if(!cache.browsable()) throw new TemplateFeedException(503,"模板缓存已过期"); return R.ok(project(cache.require(id),generation.canValidate(tenant,user))); }
    private Map<String,Object> project(JsonNode t,boolean validation) { var data=cache.project(t); if(validation && cache.published(t)) data.put("canGenerate",true); return data; }
    @GetMapping("/{id}/cover") @SaCheckPermission("image:creation:view")
    public ResponseEntity<byte[]> cover(@PathVariable String id) {
        tenant(user()); JsonNode t=cache.require(id); String name=t.path("cover").path("url").asText();
        String type=name.endsWith(".webp")?"image/webp":name.endsWith(".jpg")?"image/jpeg":"image/png";
        return ResponseEntity.ok().header("Cache-Control","private, max-age=600").header("X-Content-Type-Options","nosniff").header("Content-Type",type).body(cache.cover(id));
    }
    // HTTP 使用平台 Jackson 3 可读取的 Map，服务层继续使用独立 Jackson 2 的树校验。
    @PostMapping("/prepare") @SaCheckPermission("image:creation:view")
    public R<Map<String,Object>> prepare(@RequestBody Map<String,Object> body) {
        tenant(user()); return R.ok(generation.prepare(TemplateFeedCache.JSON.valueToTree(body)));
    }
    @PostMapping("/generate") @SaCheckPermission("image:creation:submit")
    public ResponseEntity<R<Map<String,Object>>> generate(@RequestBody Map<String,Object> body) { long user=user(); return ResponseEntity.status(202).body(R.ok(generation.submit(tenant(user),user,LoginHelper.getDeptId(),TemplateFeedCache.JSON.valueToTree(body)))); }
    @GetMapping("/generate/{id}") @SaCheckPermission("image:creation:view")
    public R<Map<String,Object>> status(@PathVariable String id) { long user=user(); return R.ok(generation.status(tenant(user),user,id)); }
    @PostMapping("/generate/{id}/retry") @SaCheckPermission("image:creation:submit")
    public ResponseEntity<R<Map<String,Object>>> retry(@PathVariable String id) { long user=user(); return ResponseEntity.status(202).body(R.ok(generation.retry(tenant(user),user,id))); }
    @ExceptionHandler(TemplateFeedException.class)
    public ResponseEntity<R<Map<String,Object>>> error(TemplateFeedException e) { var r=R.fail(e.getMessage(),Map.<String,Object>of("errors",e.errors)); r.setCode(e.status); return ResponseEntity.status(e.status).body(r); }
}
