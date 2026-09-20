package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.ContentGateRuleBo;
import org.dromara.content.domain.vo.CpGateRuleVo;
import org.dromara.content.service.IContentGateRuleService;
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
 * 闸门规则 控制层。
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/gateRule")
public class ContentGateRuleController {

    /**
     * 闸门规则服务
     */
    private final IContentGateRuleService gateRuleService;

    /**
     * 规则分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(ContentConstants.PERM_GATE_LIST)
    @GetMapping("/list")
    public R<PageResult<CpGateRuleVo>> list(@Validated({Default.class, QueryGroup.class}) ContentGateRuleBo bo,
                                            PageQuery pageQuery) {
        return R.ok(gateRuleService.queryPage(bo, pageQuery));
    }

    /**
     * 规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则
     */
    @SaCheckPermission(ContentConstants.PERM_GATE_QUERY)
    @GetMapping("/{ruleId}")
    public R<CpGateRuleVo> getInfo(@NotNull(message = "规则ID不能为空") @PathVariable("ruleId") Long ruleId) {
        return R.ok(gateRuleService.getDetail(ruleId));
    }

    /**
     * 新增规则。
     *
     * @param bo 规则参数
     * @return 规则ID
     */
    @SaCheckPermission(ContentConstants.PERM_GATE_ADD)
    @RepeatSubmit
    @Log(title = "闸门规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody ContentGateRuleBo bo) {
        return R.ok(gateRuleService.create(bo));
    }

    /**
     * 修改规则。
     *
     * @param bo 规则参数
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_GATE_EDIT)
    @RepeatSubmit
    @Log(title = "闸门规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated({Default.class, EditGroup.class}) @RequestBody ContentGateRuleBo bo) {
        gateRuleService.update(bo);
        return R.ok();
    }

    /**
     * 删除规则。
     *
     * @param ruleId 规则ID
     * @return 操作结果
     */
    @SaCheckPermission(ContentConstants.PERM_GATE_REMOVE)
    @Log(title = "闸门规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleId}")
    public R<Void> remove(@NotNull(message = "规则ID不能为空") @PathVariable("ruleId") Long ruleId) {
        gateRuleService.remove(ruleId);
        return R.ok();
    }

}
