package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.vo.DpLayoutTemplateVo;
import org.dromara.creative.service.ICreativeTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 视觉模板库 控制层（R3.2 模板发布门）。
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/templates")
public class CreativeTemplateController {

    private final ICreativeTemplateService templateService;

    /**
     * 模板列表（含与渲染服务的实时校验和对账）。
     *
     * @return 模板列表
     */
    @SaCheckPermission(CreativeConstants.PERM_TEMPLATE_LIST)
    @GetMapping
    public R<List<DpLayoutTemplateVo>> list() {
        return R.ok(templateService.list());
    }

    /**
     * 与渲染服务对账（登记新模板；校验和变化则退回草稿）。
     *
     * @return 同步后的模板列表
     */
    @SaCheckPermission(CreativeConstants.PERM_TEMPLATE_ADD)
    @Log(title = "视觉模板对账", businessType = BusinessType.INSERT)
    @PostMapping("/sync")
    public R<List<DpLayoutTemplateVo>> sync() {
        return R.ok(templateService.sync());
    }

    /**
     * 发布模板（要求校验和与渲染服务一致）。
     *
     * @param templateId 模板ID
     * @return 发布后的模板
     */
    @SaCheckPermission(CreativeConstants.PERM_TEMPLATE_EDIT)
    @Log(title = "视觉模板发布", businessType = BusinessType.UPDATE)
    @PostMapping("/{templateId}/publish")
    public R<DpLayoutTemplateVo> publish(@NotNull(message = "模板ID不能为空")
                                         @PathVariable("templateId") Long templateId) {
        return R.ok(templateService.publish(templateId));
    }

    /**
     * 退役模板。
     *
     * @param templateId 模板ID
     * @return 退役后的模板
     */
    @SaCheckPermission(CreativeConstants.PERM_TEMPLATE_EDIT)
    @Log(title = "视觉模板退役", businessType = BusinessType.UPDATE)
    @PostMapping("/{templateId}/retire")
    public R<DpLayoutTemplateVo> retire(@NotNull(message = "模板ID不能为空")
                                        @PathVariable("templateId") Long templateId) {
        return R.ok(templateService.retire(templateId));
    }

}
