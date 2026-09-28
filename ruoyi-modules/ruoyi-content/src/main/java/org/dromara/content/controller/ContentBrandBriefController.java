package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.constant.ContentConstants;
import org.dromara.content.domain.bo.BrandBriefBo;
import org.dromara.content.domain.vo.CpBrandBriefVo;
import org.dromara.content.service.IContentBrandBriefService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 品牌要求（Brief）控制层 —— **品牌部在内容生产协同里录入与确认**。
 *
 * <p><b>为什么在这里而不是视觉工厂</b>：Brief 的作者是品牌方，写的是"委托要求"；
 * 平面设计部（AI 视觉工厂）只是**读取并按它创作**。谁录入谁拥有——放在视觉模块里，
 * 就会出现"品牌部想写要求得先有视觉权限"的反向依赖。R7 之后视觉工厂项目页上的
 * 品牌要求是**只读展示**（由创作域通过 {@code IContentBrandBriefService} 读取）。</p>
 *
 * <p>权限沿用内容任务的既有编码（查询 {@code content:task:query} / 编辑 {@code content:task:edit}），
 * 不新增权限码：新增权限码要同步菜单 SQL，否则非超管角色一律 403。</p>
 *
 * @author content
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/task/{taskId}/brand-brief")
public class ContentBrandBriefController {

    private final IContentBrandBriefService briefService;

    /**
     * 读取品牌要求（没有记录时返回 configured=false 的空视图，不返回 null data）。
     *
     * @param taskId 任务ID
     * @return Brief 视图
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_QUERY)
    @GetMapping
    public R<CpBrandBriefVo> brandBrief(@NotNull(message = "任务ID不能为空")
                                        @PathVariable("taskId") Long taskId) {
        return R.ok(briefService.get(taskId));
    }

    /**
     * 保存品牌要求（upsert，草稿态；不会改「已确认」状态）。
     *
     * @param taskId 任务ID
     * @param bo     表单
     * @return 保存后的视图
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @Log(title = "品牌要求", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<CpBrandBriefVo> saveBrandBrief(@NotNull(message = "任务ID不能为空")
                                            @PathVariable("taskId") Long taskId,
                                            @Validated @RequestBody BrandBriefBo bo) {
        return R.ok(briefService.save(taskId, bo));
    }

    /**
     * 品牌方确认：确认后视觉门的「品牌要求已填写并确认」闸门项才会通过。
     *
     * @param taskId 任务ID
     * @return 确认后的视图
     */
    @SaCheckPermission(ContentConstants.PERM_TASK_EDIT)
    @RepeatSubmit
    @Log(title = "品牌要求确认", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm")
    public R<CpBrandBriefVo> confirmBrandBrief(@NotNull(message = "任务ID不能为空")
                                               @PathVariable("taskId") Long taskId) {
        return R.ok(briefService.confirm(taskId));
    }

}
