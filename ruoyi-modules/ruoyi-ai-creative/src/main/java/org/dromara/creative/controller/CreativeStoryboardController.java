package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.CreativeDirectionBo;
import org.dromara.creative.domain.bo.CreativeScreenBo;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;
import org.dromara.creative.domain.vo.DpStoryboardVo;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;
import org.dromara.creative.service.ICreativeDirectionService;
import org.dromara.creative.service.ICreativeStoryboardService;
import org.springframework.validation.annotation.Validated;
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
 * 视觉方向与分镜 控制层。
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}")
public class CreativeStoryboardController {

    private final ICreativeDirectionService directionService;
    private final ICreativeStoryboardService storyboardService;

    // ---------------- 视觉方向 ----------------

    /**
     * 生成 A/B/C 视觉方向（须已有锁定基因）。
     *
     * @param taskId 项目ID
     * @return 方向列表
     */
    @SaCheckPermission(CreativeConstants.PERM_DIRECTION_GENERATE)
    @Log(title = "视觉方向生成", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/directions/generate")
    public R<List<DpVisualDirectionVo>> generateDirections(@NotNull(message = "项目ID不能为空")
                                                           @PathVariable("taskId") Long taskId) {
        return R.ok(directionService.generate(taskId));
    }

    /**
     * 方向列表。
     *
     * @param taskId 项目ID
     * @return 方向列表
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_LIST)
    @GetMapping("/directions")
    public R<List<DpVisualDirectionVo>> directions(@NotNull(message = "项目ID不能为空")
                                                   @PathVariable("taskId") Long taskId) {
        return R.ok(directionService.list(taskId));
    }

    /**
     * 选定方向。
     *
     * @param taskId      项目ID
     * @param directionId 方向ID
     * @return 选定后的方向
     */
    @SaCheckPermission(CreativeConstants.PERM_DIRECTION_SELECT)
    @Log(title = "视觉方向选定", businessType = BusinessType.UPDATE)
    @PostMapping("/directions/{directionId}/select")
    public R<DpVisualDirectionVo> selectDirection(@NotNull(message = "项目ID不能为空")
                                                  @PathVariable("taskId") Long taskId,
                                                  @NotNull(message = "方向ID不能为空")
                                                  @PathVariable("directionId") Long directionId) {
        return R.ok(directionService.select(taskId, directionId));
    }

    /**
     * 编辑方向文案。
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 编辑后的方向
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "视觉方向编辑", businessType = BusinessType.UPDATE)
    @PutMapping("/directions")
    public R<DpVisualDirectionVo> updateDirection(@NotNull(message = "项目ID不能为空")
                                                  @PathVariable("taskId") Long taskId,
                                                  @RequestBody CreativeDirectionBo bo) {
        return R.ok(directionService.update(taskId, bo));
    }

    // ---------------- 分镜 ----------------

    /**
     * 生成一版分镜。
     *
     * @param taskId 项目ID
     * @return 新分镜（含屏）
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_GENERATE)
    @Log(title = "分镜生成", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/storyboard/generate")
    public R<DpStoryboardVo> generateStoryboard(@NotNull(message = "项目ID不能为空")
                                                @PathVariable("taskId") Long taskId) {
        return R.ok(storyboardService.generate(taskId));
    }

    /**
     * 最新一版分镜（含屏）。
     *
     * @param taskId 项目ID
     * @return 分镜；未生成过返回 null
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_LIST)
    @GetMapping("/storyboard")
    public R<DpStoryboardVo> storyboard(@NotNull(message = "项目ID不能为空")
                                        @PathVariable("taskId") Long taskId) {
        return R.ok(storyboardService.latest(taskId));
    }

    /**
     * 分镜版本列表。
     *
     * @param taskId 项目ID
     * @return 版本列表
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_LIST)
    @GetMapping("/storyboard/versions")
    public R<List<DpStoryboardVo>> storyboardVersions(@NotNull(message = "项目ID不能为空")
                                                      @PathVariable("taskId") Long taskId) {
        return R.ok(storyboardService.versions(taskId));
    }

    /**
     * 编辑一屏。
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 编辑后的屏
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "分镜编辑", businessType = BusinessType.UPDATE)
    @PutMapping("/storyboard/screen")
    public R<DpStoryboardScreenVo> updateScreen(@NotNull(message = "项目ID不能为空")
                                                @PathVariable("taskId") Long taskId,
                                                @RequestBody CreativeScreenBo bo) {
        return R.ok(storyboardService.updateScreen(taskId, bo));
    }

    /**
     * 锁定分镜。
     *
     * @param taskId       项目ID
     * @param storyboardId 分镜ID（可空＝最新一版）
     * @return 锁定后的分镜
     */
    @SaCheckPermission(CreativeConstants.PERM_STORYBOARD_EDIT)
    @Log(title = "分镜锁定", businessType = BusinessType.UPDATE)
    @PostMapping("/storyboard/lock")
    public R<DpStoryboardVo> lockStoryboard(@NotNull(message = "项目ID不能为空")
                                            @PathVariable("taskId") Long taskId,
                                            @RequestParam(value = "storyboardId", required = false)
                                            Long storyboardId) {
        return R.ok(storyboardService.lock(taskId, storyboardId));
    }

}
