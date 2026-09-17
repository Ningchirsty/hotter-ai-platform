package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.vo.TlTalentAttachmentVo;
import org.dromara.talent.service.ITalentAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 人才附件 控制层
 *
 * <p>下载为受控流式下载：Controller 只把 {@link HttpServletResponse} 透传给 Service，
 * 由 Service 完成单条授权、扫描状态校验、响应头设置与审计，Controller 不接触对象键与 OSS。</p>
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/attachment")
public class TalentAttachmentController {

    private final ITalentAttachmentService talentAttachmentService;

    /**
     * 查询指定人才的附件列表。
     *
     * @param talentId 人才ID
     * @return 附件列表（不含 object_key / 预签名 URL）
     */
    @SaCheckPermission(TalentConstants.PERM_ATTACH_MANAGE)
    @GetMapping("/list/{talentId}")
    public R<List<TlTalentAttachmentVo>> list(@NotNull(message = "主键不能为空")
                                              @PathVariable("talentId") Long talentId) {
        return R.ok(talentAttachmentService.listByTalent(talentId));
    }

    /**
     * 上传人才附件（新版本）。
     *
     * @param file           上传文件
     * @param talentId       人才ID（表单参数）
     * @param attachmentType 附件类型（表单参数）
     * @return 新增的附件ID
     */
    @SaCheckPermission(TalentConstants.PERM_ATTACH_UPLOAD)
    @RepeatSubmit
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> upload(@RequestPart("file") MultipartFile file,
                          @RequestParam("talentId") Long talentId,
                          @RequestParam("attachmentType") String attachmentType) {
        return R.ok(talentAttachmentService.upload(talentId, attachmentType, file));
    }

    /**
     * 受控下载人才附件。
     *
     * @param attachmentId 附件ID
     * @param response     HTTP 响应（由 Service 写入 Content-Type / Content-Disposition / 文件流）
     */
    @SaCheckPermission(TalentConstants.PERM_ATTACH_DOWNLOAD)
    @GetMapping("/download/{attachmentId}")
    public void download(@NotNull(message = "主键不能为空")
                         @PathVariable("attachmentId") Long attachmentId,
                         HttpServletResponse response) {
        talentAttachmentService.download(attachmentId, response);
    }

    /**
     * 逻辑删除附件（保留存储对象）。
     *
     * @param attachmentId 附件ID
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_ATTACH_MANAGE)
    @RepeatSubmit
    @DeleteMapping("/{attachmentId}")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable("attachmentId") Long attachmentId) {
        talentAttachmentService.remove(attachmentId);
        return R.ok();
    }
}
