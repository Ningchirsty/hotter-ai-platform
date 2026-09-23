package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.CreativeHeroBo;
import org.dromara.creative.domain.vo.DpGenerationVo;
import org.dromara.creative.service.ICreativeGenerationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 生产中心 控制层（出图候选的提交、跟踪、预览）。
 *
 * <p>两条前端硬约定：</p>
 * <ul>
 *     <li>列表接口在返回前会向图像内核拉一次未结束候选的真实状态，因此页面看到的状态
 *     就是内核里的状态，不是我们缓存的猜测值；</li>
 *     <li>图片一律走后端代理（{@code preview}/{@code thumbnail}），不接受前端直连对象存储——
 *     私有桶对浏览器不可达，也不该可达。</li>
 * </ul>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative")
public class CreativeProductionController {

    private final ICreativeGenerationService generationService;

    /**
     * 提交一次 HERO 主图出图。
     *
     * @param taskId 项目ID
     * @param bo     出图参数
     * @return 生成记录
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_START)
    @Log(title = "视觉出图", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/projects/{taskId}/generations")
    public R<DpGenerationVo> submitHero(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                        @RequestBody CreativeHeroBo bo) {
        return R.ok(generationService.submitHero(taskId, bo));
    }

    /**
     * 项目的出图候选列表（返回前刷新内核状态）。
     *
     * @param taskId 项目ID
     * @return 候选列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_LIST)
    @GetMapping("/projects/{taskId}/generations")
    public R<List<DpGenerationVo>> listByProject(@NotNull(message = "项目ID不能为空")
                                                 @PathVariable("taskId") Long taskId) {
        return R.ok(generationService.listByProject(taskId));
    }

    /**
     * 重试失败/超时的候选（新建一次候选，不复活原内核任务）。
     *
     * @param generationId 生成记录ID
     * @return 新的生成记录
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_RETRY)
    @Log(title = "视觉出图重试", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/generations/{generationId}/retry")
    public R<DpGenerationVo> retry(@NotNull(message = "候选ID不能为空")
                                   @PathVariable("generationId") Long generationId) {
        return R.ok(generationService.retry(generationId));
    }

    /**
     * 候选原图（后端代理，鉴权后返回字节）。
     *
     * @param generationId 生成记录ID
     * @return 图片字节
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_LIST)
    @GetMapping("/generations/{generationId}/preview")
    public ResponseEntity<byte[]> preview(@NotNull(message = "候选ID不能为空")
                                          @PathVariable("generationId") Long generationId) {
        byte[] bytes = generationService.preview(generationId);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(bytes);
    }

    /**
     * 候选缩略图（列表用；缩略图生成不出时回落原图）。
     *
     * @param generationId 生成记录ID
     * @return JPEG 字节
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_LIST)
    @GetMapping("/generations/{generationId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@NotNull(message = "候选ID不能为空")
                                            @PathVariable("generationId") Long generationId) {
        byte[] bytes = generationService.thumbnail(generationId);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(bytes);
    }

    /**
     * 跨项目候选分页（AI 生产中心列表）。
     *
     * @param status    状态过滤（可空）
     * @param pageQuery 分页参数
     * @return 分页结果（未结束候选会先刷新内核状态）
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_LIST)
    @GetMapping("/productions")
    public R<PageResult<DpGenerationVo>> productions(@RequestParam(value = "status", required = false) String status,
                                                     PageQuery pageQuery) {
        return R.ok(generationService.queryPage(status, pageQuery));
    }

    /**
     * 可用的出图工作流（页面据此列出可选能力，不硬编码能力清单）。
     *
     * @return 工作流列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PRODUCTION_LIST)
    @GetMapping("/workflows")
    public R<List<Map<String, Object>>> workflows() {
        return R.ok(generationService.availableWorkflows());
    }

}
