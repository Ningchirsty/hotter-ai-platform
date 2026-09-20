package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigCapabilityModelBo;
import org.dromara.aigov.domain.vo.AigCapabilityModelVo;
import org.dromara.aigov.service.IAigModelBindingService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力-模型绑定 控制层
 * <p>绑定属于路由配置，权限沿用 {@code aig:route:*}（SPEC §6）。
 * 注意：阶段1 前端未提供绑定页面，本接口保留给配置与排障使用。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/binding")
public class AigModelBindingController {

    private final IAigModelBindingService modelBindingService;

    /**
     * 分页查询绑定关系。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 绑定分页结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_LIST)
    @GetMapping("/list")
    public R<PageResult<AigCapabilityModelVo>> list(@Validated({Default.class, QueryGroup.class}) AigCapabilityModelBo bo,
                                                    PageQuery pageQuery) {
        return R.ok(modelBindingService.queryPage(bo, pageQuery));
    }

    /**
     * 新增绑定。
     *
     * @param bo 绑定参数
     * @return 新增的绑定ID
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_ADD)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody AigCapabilityModelBo bo) {
        return R.ok(modelBindingService.create(bo));
    }

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param bindId 绑定ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_REMOVE)
    @RepeatSubmit
    @DeleteMapping("/{bindId}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("bindId") Long bindId) {
        modelBindingService.remove(bindId);
        return R.ok();
    }

}
