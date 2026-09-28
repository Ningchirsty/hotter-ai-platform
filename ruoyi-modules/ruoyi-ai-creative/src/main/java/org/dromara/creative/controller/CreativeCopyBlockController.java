package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.CopyBlockBo;
import org.dromara.creative.domain.bo.CopyBlockReorderBo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.service.ICreativeCopyService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文案与要点（整页要说的话）控制层。
 *
 * <p><b>R7 起品牌要求（Brief）不在这里</b>：品牌要求由品牌部在**内容生产协同**的任务里录入与确认
 * （{@code /content/task/{taskId}/brand-brief}），因为它是"委托要求"而不是"设计产出"；
 * 本控制器只剩平面设计自己的产出物——卖点、详情页正文分段、参数行。</p>
 *
 * <p>权限沿用分镜的既有编码（读 {@code creative:storyboard:list}，写 {@code creative:storyboard:edit}），
 * 不新增权限码。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}")
public class CreativeCopyBlockController {

    private final ICreativeCopyService copyService;

    /**
     * 块列表（可按类型过滤，按类型 + 排序返回）。
     *
     * @param taskId    项目ID
     * @param blockType 块类型（可空＝全部）
     * @return 块列表
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_LIST)
    @GetMapping("/copy-blocks")
    public R<List<DpCopyBlockVo>> copyBlocks(@NotNull(message = "项目ID不能为空")
                                             @PathVariable("taskId") Long taskId,
                                             @RequestParam(value = "blockType", required = false)
                                             String blockType) {
        return R.ok(copyService.list(taskId, blockType));
    }

    /**
     * 新增一块。
     *
     * @param taskId 项目ID
     * @param bo     表单
     * @return 新块ID
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "文案块新增", businessType = BusinessType.INSERT)
    @PostMapping("/copy-blocks")
    public R<Long> addCopyBlock(@NotNull(message = "项目ID不能为空")
                                @PathVariable("taskId") Long taskId,
                                @Validated @RequestBody CopyBlockBo bo) {
        return R.ok(copyService.add(taskId, bo));
    }

    /**
     * 按给定顺序重排同一类型下的块。
     *
     * @param taskId 项目ID
     * @param bo     重排表单
     * @return 空
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "文案块重排", businessType = BusinessType.UPDATE)
    @PostMapping("/copy-blocks/reorder")
    public R<Void> reorderCopyBlocks(@NotNull(message = "项目ID不能为空")
                                     @PathVariable("taskId") Long taskId,
                                     @Validated @RequestBody CopyBlockReorderBo bo) {
        copyService.reorder(taskId, bo);
        return R.ok();
    }

    /**
     * 从已确认事实派生参数行（幂等）。
     *
     * @param taskId 项目ID
     * @return 本次新增条数
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "文案块事实派生", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/copy-blocks/seed-from-facts")
    public R<Integer> seedCopyBlocks(@NotNull(message = "项目ID不能为空")
                                     @PathVariable("taskId") Long taskId) {
        return R.ok(copyService.seedFromFacts(taskId));
    }

    /**
     * 编辑一块。
     *
     * @param taskId  项目ID
     * @param blockId 块ID
     * @param bo      表单
     * @return 空
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "文案块编辑", businessType = BusinessType.UPDATE)
    @PutMapping("/copy-blocks/{blockId}")
    public R<Void> updateCopyBlock(@NotNull(message = "项目ID不能为空")
                                   @PathVariable("taskId") Long taskId,
                                   @NotNull(message = "块ID不能为空")
                                   @PathVariable("blockId") Long blockId,
                                   @Validated @RequestBody CopyBlockBo bo) {
        copyService.update(taskId, blockId, bo);
        return R.ok();
    }

    /**
     * 删除一块（逻辑删除）。
     *
     * @param taskId  项目ID
     * @param blockId 块ID
     * @return 空
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "文案块删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/copy-blocks/{blockId}")
    public R<Void> removeCopyBlock(@NotNull(message = "项目ID不能为空")
                                   @PathVariable("taskId") Long taskId,
                                   @NotNull(message = "块ID不能为空")
                                   @PathVariable("blockId") Long blockId) {
        copyService.remove(taskId, blockId);
        return R.ok();
    }

}
