package org.dromara.ai.image.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageInspirationService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 图像灵感只读取当前登录账号的真实云端产出。 */
@RestController
@RequestMapping("/image/inspirations")
@ConditionalOnProperty(prefix = "image", name = "enabled", havingValue = "true")
public class ImageInspirationController extends BaseController {
    private final ImageInspirationService service;
    private final JdbcTemplate jdbc;
    public ImageInspirationController(ImageInspirationService service, JdbcTemplate jdbc) {
        this.service = service; this.jdbc = jdbc;
    }

    /** 复用图像查看权限；账号与租户均由登录态解析，客户端不能指定。 */
    @GetMapping
    @SaCheckPermission("image:creation:view")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "6") int pageSize,
                                                  @RequestParam(required = false) String model,
                                                  @RequestParam(required = false) String capability,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String savedIds) {
        Long user = LoginHelper.getUserId();
        if (user == null) throw new ImageTaskException("UNAUTHENTICATED", "当前未登录");
        String tenant = jdbc.queryForObject("SELECT tenant_id FROM sys_user WHERE user_id = ?", String.class, user);
        if (tenant == null || tenant.isBlank()) throw new ImageTaskException("UNAUTHENTICATED", "无法确认当前账号归属");
        java.util.List<String> ids = savedIds == null ? null : java.util.Arrays.asList(savedIds.split(",", 201));
        return R.ok(service.list(tenant, user, pageNum, pageSize, model, capability, keyword, category, ids));
    }
}
