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
 * <p><b>权限（内测 C1 起独立编码）</b>：读取 {@code content:task:query}、录入
 * {@code content:brief:edit}、确认 {@code content:brief:confirm}。
 * C1 之前三者都只要 {@code content:task:edit}，而该权限在设计账号手里（视觉工厂菜单曾
 * 整体授予内容角色），于是"设计部能自己写一版品牌要求、再自己批一版"在权限层成立，
 * 审计里 {@code confirmed_by} 记的还是设计师——职责边界只剩口头约定。</p>
 *
 * <p><b>上线顺序</b>：{@code script/sql/cp_content_brief_perm.sql} 必须先于（或同时于）
 * 本类上线，否则品牌角色会因为库里没有新权限码而点不了「保存 / 品牌方确认」。</p>
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
     * <p>权限用 {@link ContentConstants#PERM_BRIEF_EDIT} 而不是 {@code content:task:edit}：
     * 后者的持有者包含设计账号，会让"设计部改品牌要求"变成权限层允许的事。</p>
     *
     * @param taskId 任务ID
     * @param bo     表单
     * @return 保存后的视图
     */
    @SaCheckPermission(ContentConstants.PERM_BRIEF_EDIT)
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
     * <p>权限用 {@link ContentConstants#PERM_BRIEF_CONFIRM}：这是品牌方的**批准**动作，
     * 与"把要求写下来"分开授权——否则设计侧可以自己写一版、自己批一版，
     * 而审计只会记下"某人确认过"。</p>
     *
     * @param taskId 任务ID
     * @return 确认后的视图
     */
    @SaCheckPermission(ContentConstants.PERM_BRIEF_CONFIRM)
    @RepeatSubmit
    @Log(title = "品牌要求确认", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm")
    public R<CpBrandBriefVo> confirmBrandBrief(@NotNull(message = "任务ID不能为空")
                                               @PathVariable("taskId") Long taskId) {
        return R.ok(briefService.confirm(taskId));
    }

}
