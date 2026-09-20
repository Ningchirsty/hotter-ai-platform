package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.domain.vo.AigRoutePolicyVo;
import org.dromara.aigov.service.IAigRoutePolicyService;
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
 * AI 路由策略 控制层
 * <p>按「能力 × 数据等级」唯一确定策略；<b>无策略即拒绝</b>，
 * 因此新增能力后必须补策略，否则该能力的调用会被路由引擎判为 DENIED。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/route")
public class AigRoutePolicyController {

    private final IAigRoutePolicyService routePolicyService;

    /**
     * 分页查询路由策略。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 策略分页结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_LIST)
    @GetMapping("/list")
    public R<PageResult<AigRoutePolicyVo>> list(@Validated({Default.class, QueryGroup.class}) AigRoutePolicyBo bo,
                                                PageQuery pageQuery) {
        return R.ok(routePolicyService.queryPage(bo, pageQuery));
    }

    /**
     * 获取策略详情。
     *
     * @param policyId 策略ID
     * @return 策略详情
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_QUERY)
    @GetMapping("/{policyId}")
    public R<AigRoutePolicyVo> getInfo(@NotNull(message = "主键不能为空")
                                       @PathVariable("policyId") Long policyId) {
        return R.ok(routePolicyService.getDetail(policyId));
    }

    /**
     * 新增路由策略。
     *
     * @param bo 策略参数
     * @return 新增的策略ID
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_ADD)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody AigRoutePolicyBo bo) {
        return R.ok(routePolicyService.create(bo));
    }

    /**
     * 修改路由策略。
     *
     * @param bo 策略参数
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_EDIT)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody AigRoutePolicyBo bo) {
        routePolicyService.update(bo);
        return R.ok();
    }

    /**
     * 删除路由策略（逻辑删除）。
     *
     * @param policyId 策略ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_ROUTE_REMOVE)
    @RepeatSubmit
    @DeleteMapping("/{policyId}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("policyId") Long policyId) {
        routePolicyService.remove(policyId);
        return R.ok();
    }

}
