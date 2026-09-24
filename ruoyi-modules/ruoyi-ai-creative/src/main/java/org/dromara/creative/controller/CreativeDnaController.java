package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.CreativeDnaBo;
import org.dromara.creative.domain.vo.DnaRecommendationVo;
import org.dromara.creative.domain.vo.DpVisualDnaVo;
import org.dromara.creative.helper.DnaPromptBuilder;
import org.dromara.creative.service.ICreativeDnaService;
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
 * Visual DNA 控制层。
 *
 * <p>路径挂在项目下（`/creative/projects/{taskId}/dna`）：视觉基因天生属于某个项目，
 * 不可能脱离项目独立存在，路径上表达出来比在参数里传更不容易出错。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects/{taskId}/dna")
public class CreativeDnaController {

    private final ICreativeDnaService dnaService;

    /**
     * 生成一版视觉基因。
     *
     * @param taskId 项目ID
     * @return 新版本
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_ANALYZE)
    @Log(title = "视觉基因生成", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/generate")
    public R<DpVisualDnaVo> generate(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(dnaService.generate(taskId));
    }

    /**
     * 按参考图推荐规范内容（不落库：只把推荐值 + 逐字段依据返回给页面，由人确认后再保存）。
     *
     * @param taskId 项目ID
     * @return 推荐结果（含依据、可信度、测不出来的字段、与事实的冲突）
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_ANALYZE)
    @PostMapping("/recommend")
    public R<DnaRecommendationVo> recommend(@NotNull(message = "项目ID不能为空")
                                            @PathVariable("taskId") Long taskId) {
        return R.ok(dnaService.recommend(taskId));
    }

    /**
     * 最新一版视觉基因。
     *
     * @param taskId 项目ID
     * @return 最新版本；从未生成过返回 null（前端据此引导「先生成」）
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_LIST)
    @GetMapping
    public R<DpVisualDnaVo> latest(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(dnaService.latest(taskId));
    }

    /**
     * 版本列表（倒序）。
     *
     * @param taskId 项目ID
     * @return 版本列表
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_LIST)
    @GetMapping("/versions")
    public R<List<DpVisualDnaVo>> versions(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(dnaService.versions(taskId));
    }

    /**
     * 保存编辑（锁定版会自动新建版本）。
     *
     * @param taskId 项目ID
     * @param bo     编辑内容
     * @return 保存后的版本
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_EDIT)
    @Log(title = "视觉基因编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<DpVisualDnaVo> save(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                 @RequestBody CreativeDnaBo bo) {
        return R.ok(dnaService.save(taskId, bo));
    }

    /**
     * 锁定视觉基因（锁定前必须通过自洽校验）。
     *
     * @param taskId 项目ID
     * @param dnaId  版本ID（可空＝最新一版）
     * @return 锁定后的版本
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_LOCK)
    @Log(title = "视觉基因锁定", businessType = BusinessType.UPDATE)
    @PostMapping("/lock")
    public R<DpVisualDnaVo> lock(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                 @RequestParam(value = "dnaId", required = false) Long dnaId) {
        return R.ok(dnaService.lock(taskId, dnaId));
    }

    /**
     * 按当前生效基因派生提示词（页面预填，用户可改）。
     *
     * @param taskId     项目ID
     * @param screenHint 画面用途提示
     * @return 派生的提示词与用到的维度
     */
    @SaCheckPermission(CreativeConstants.PERM_DNA_LIST)
    @GetMapping("/prompt")
    public R<DnaPromptBuilder.Prompt> prompt(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                             @RequestParam(value = "screenHint", required = false) String screenHint) {
        return R.ok(dnaService.promptPreview(taskId, screenHint));
    }

}
