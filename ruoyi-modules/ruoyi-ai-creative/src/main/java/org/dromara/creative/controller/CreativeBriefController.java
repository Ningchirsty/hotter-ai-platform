package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.BrandBriefBo;
import org.dromara.creative.domain.bo.CopyBlockBo;
import org.dromara.creative.domain.bo.CopyBlockReorderBo;
import org.dromara.creative.domain.vo.DpBrandBriefVo;
import org.dromara.creative.domain.vo.DpCopyBlockVo;
import org.dromara.creative.service.ICreativeBriefService;
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
 * 品牌 Brief 与文案要点 控制层。
 *
 * <p><b>为什么与分镜控制器分开</b>：分镜是「一屏配什么字」，这里放的是「整页要说的话」与
 * 「品牌方的要求」。两者生命周期不同（分镜会锁版，Brief 会随需求变更），
 * 混在一个控制器里以后必然出现「改 Brief 顺手改了分镜」的权限错配。</p>
 *
 * <p>权限沿用既有编码（Brief＝项目权限，文案块＝分镜权限），<b>不新增权限码</b>：
 * 新权限码需要同步菜单 SQL，否则非超管角色一律 403。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}")
public class CreativeBriefController {

    private final ICreativeBriefService briefService;
    private final ICreativeCopyService copyService;

    // ---------------- 品牌 Brief ----------------

    /**
     * 读取品牌 Brief（没有记录时返回 configured=false 的空视图，不返回 null data）。
     *
     * @param taskId 项目ID
     * @return Brief 视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_QUERY)
    @GetMapping("/brand-brief")
    public R<DpBrandBriefVo> brandBrief(@NotNull(message = "项目ID不能为空")
                                        @PathVariable("taskId") Long taskId) {
        return R.ok(briefService.get(taskId));
    }

    /**
     * 保存品牌 Brief（upsert）。
     *
     * @param taskId 项目ID
     * @param bo     表单
     * @return 保存后的视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "品牌Brief保存", businessType = BusinessType.UPDATE)
    @PutMapping("/brand-brief")
    public R<DpBrandBriefVo> saveBrandBrief(@NotNull(message = "项目ID不能为空")
                                            @PathVariable("taskId") Long taskId,
                                            @RequestBody BrandBriefBo bo) {
        return R.ok(briefService.save(taskId, bo));
    }

    /**
     * 品牌方确认（唯一能把状态推进到 CONFIRMED 的入口，闸门依据它判定）。
     *
     * @param taskId 项目ID
     * @return 确认后的视图
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_EDIT)
    @Log(title = "品牌Brief确认", businessType = BusinessType.UPDATE)
    @PostMapping("/brand-brief/confirm")
    public R<DpBrandBriefVo> confirmBrandBrief(@NotNull(message = "项目ID不能为空")
                                               @PathVariable("taskId") Long taskId) {
        return R.ok(briefService.confirm(taskId));
    }

    // ---------------- 文案与要点块 ----------------

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
                                @RequestBody CopyBlockBo bo) {
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
                                     @RequestBody CopyBlockReorderBo bo) {
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
    @RepeatSubmit()
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
                                   @RequestBody CopyBlockBo bo) {
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
