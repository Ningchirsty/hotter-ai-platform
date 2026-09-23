package org.dromara.creative.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.content.domain.bo.ContentTaskBo;
import org.dromara.content.domain.vo.CpTaskFileVo;
import org.dromara.creative.constant.CreativeConstants;
import org.dromara.creative.domain.bo.CreativeProjectBo;
import org.dromara.creative.domain.vo.CreativeProjectVo;
import org.dromara.creative.domain.vo.DpStageEventVo;
import org.dromara.creative.service.ICreativeProjectService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视觉项目 控制层。
 *
 * <p>项目本体是内容协同的 {@code cp_task}（交付类型固定 ECOM_DETAIL）。
 * 查询条件直接复用内容模块的 {@code ContentTaskBo}，但交付类型由服务端强制覆盖，
 * 前端传什么都不会把别的交付类型混进视觉工厂。</p>
 *
 * @author creative
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/creative/projects")
public class CreativeProjectController {

    private final ICreativeProjectService projectService;

    /**
     * 视觉项目分页。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果（含视觉阶段）
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_LIST)
    @GetMapping("/list")
    public R<PageResult<CreativeProjectVo>> list(@Validated({Default.class, QueryGroup.class}) ContentTaskBo bo,
                                                 PageQuery pageQuery) {
        return R.ok(projectService.queryPage(bo, pageQuery));
    }

    /**
     * 视觉项目详情。
     *
     * @param taskId 项目ID
     * @return 项目详情（含视觉阶段、参考图数）
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_QUERY)
    @GetMapping("/{taskId}")
    public R<CreativeProjectVo> getInfo(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(projectService.getProject(taskId));
    }

    /**
     * 新建视觉项目。
     *
     * @param bo 项目参数
     * @return 项目ID
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_ADD)
    @Log(title = "视觉项目", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody CreativeProjectBo bo) {
        return R.ok(projectService.createProject(bo));
    }

    /**
     * 上传项目参考图（产品图）。
     *
     * @param taskId 项目ID
     * @param file   图片文件
     * @return 附件ID
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_UPLOAD)
    @Log(title = "视觉项目参考图", businessType = BusinessType.INSERT)
    @PostMapping("/{taskId}/reference")
    public R<Long> uploadReference(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                   @RequestPart("file") MultipartFile file) {
        return R.ok(projectService.uploadReference(taskId, file));
    }

    /**
     * 项目附件列表（含参考图）。
     *
     * @param taskId 项目ID
     * @return 附件列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_QUERY)
    @GetMapping("/{taskId}/files")
    public R<List<CpTaskFileVo>> files(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(projectService.listFiles(taskId));
    }

    /**
     * 附件内容（参考图预览的后端代理；对象存储是私有桶，前端不能直连）。
     *
     * @param taskId 项目ID
     * @param fileId 附件ID
     * @return 图片/文件字节
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_QUERY)
    @GetMapping("/{taskId}/files/{fileId}/content")
    public ResponseEntity<byte[]> fileContent(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId,
                                              @NotNull(message = "附件ID不能为空") @PathVariable("fileId") Long fileId) {
        ICreativeProjectService.FileContent content = projectService.readFileContent(taskId, fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header("X-Content-Type-Options", "nosniff")
            .header("Vary", "Authorization")
            .body(content.bytes());
    }

    /**
     * 项目阶段事件时间线（全链路可追溯）。
     *
     * @param taskId 项目ID
     * @return 事件列表
     */
    @SaCheckPermission(CreativeConstants.PERM_PROJECT_QUERY)
    @GetMapping("/{taskId}/timeline")
    public R<List<DpStageEventVo>> timeline(@NotNull(message = "项目ID不能为空") @PathVariable("taskId") Long taskId) {
        return R.ok(projectService.timeline(taskId));
    }

}
