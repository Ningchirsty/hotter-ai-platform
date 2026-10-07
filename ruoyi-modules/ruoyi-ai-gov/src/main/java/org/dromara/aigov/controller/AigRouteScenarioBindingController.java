package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigRouteScenarioBindingBo;
import org.dromara.aigov.domain.vo.AigRouteScenarioBindingVo;
import org.dromara.aigov.service.IAigRouteScenarioBindingService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 场景强制绑定 控制层
 *
 * <p>对应设计 §4.4 路由算法第 4 步「若场景强制绑定 Provider，则仅保留指定 Provider」。
 * 绑定<b>只能收窄候选</b>：它筛掉不属于指定供应商的候选，不会让被治理策略排除的模型
 * 变得可用。因此本接口的权限独立于 {@code aig:route:*} 的治理口径权限——
 * 能改外发禁令与能钉住首选供应商，是两件事。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/route/binding")
public class AigRouteScenarioBindingController {

    private final IAigRouteScenarioBindingService bindingService;

    /**
     * 分页查询场景强制绑定。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 绑定分页结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_LIST)
    @GetMapping("/list")
    public R<PageResult<AigRouteScenarioBindingVo>> list(
        @Validated({Default.class, QueryGroup.class}) AigRouteScenarioBindingBo bo, PageQuery pageQuery) {
        return R.ok(bindingService.queryPage(bo, pageQuery));
    }

    /**
     * 获取绑定详情。
     *
     * @param bindId 绑定ID
     * @return 绑定详情
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_QUERY)
    @GetMapping("/{bindId}")
    public R<AigRouteScenarioBindingVo> getInfo(@NotNull(message = "主键不能为空")
                                               @PathVariable("bindId") Long bindId) {
        return R.ok(bindingService.getDetail(bindId));
    }

    /**
     * 新增绑定。
     *
     * @param bo 绑定参数
     * @return 新增的绑定ID
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_BINDING)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody AigRouteScenarioBindingBo bo) {
        return R.ok(bindingService.create(bo));
    }

    /**
     * 修改绑定。
     *
     * @param bo 绑定参数
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_BINDING)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody AigRouteScenarioBindingBo bo) {
        bindingService.update(bo);
        return R.ok();
    }

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param bindId 绑定ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_BINDING)
    @RepeatSubmit
    @DeleteMapping("/{bindId}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("bindId") Long bindId) {
        bindingService.remove(bindId);
        return R.ok();
    }

}
