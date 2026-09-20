package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.bo.ResumeImportConfirmBo;
import org.dromara.talent.domain.vo.ResumeImportPreviewVo;
import org.dromara.talent.service.ITalentResumeImportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导入简历直接建档 控制层。
 * <p>
 * 本层只做参数接收与结果组装；开关、扩展名、大小、区域可写、手机号规范化与重复预检
 * 均由 Service 及其复用的 {@code ITalentProfileService} / {@code ITalentAttachmentService} 负责。
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/profile/import")
public class TalentResumeImportController {

    private final ITalentResumeImportService talentResumeImportService;

    /**
     * 上传简历并返回本地抽取的候选字段（不建档）。
     *
     * @param file 简历文件（pdf/doc/docx；图片型仅做文件名解析）
     * @return 预览结果（含 importToken、候选字段、来源与置信度、提示）
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_IMPORT)
    @RepeatSubmit
    @PostMapping("/preview")
    public R<ResumeImportPreviewVo> preview(@RequestPart("file") MultipartFile file) {
        return R.ok(talentResumeImportService.preview(file));
    }

    /**
     * 确认候选值并直接建档（同时保存简历附件与解析留痕）。
     *
     * @param bo 确认入参（importToken + 人工确认后的人才信息）
     * @return [talentId, attachmentId]
     */
    @SaCheckPermission(TalentConstants.PERM_PROFILE_ADD)
    @RepeatSubmit
    @PostMapping("/confirm")
    public R<Long[]> confirm(@Validated({Default.class, AddGroup.class})
                             @RequestBody ResumeImportConfirmBo bo) {
        return R.ok(talentResumeImportService.confirm(bo));
    }

}
