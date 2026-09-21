package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.AttachmentDownloadBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitAttachmentQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitAttachmentVo;
import org.dromara.hrtalent.service.recruitment.IRecruitAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 招聘业务附件 控制层（SPEC-P3 §2.5）。
 *
 * <p>路径固定为 {@code /recruit/attachments}；权限串一律取 {@link HrTalentConstants} 常量。
 * 扩展名/MIME/大小/数量校验、版本切换、业务记录资源级鉴权、敏感操作审计与流式输出
 * 全部由 {@link IRecruitAttachmentService} 负责。</p>
 *
 * <p><b>下载说明</b>：附件下载与预览是<b>流式响应</b>，不返回长期匿名地址（§11.1），
 * 因此这两个接口以 {@code void} 直接写响应体，不包装 {@link R}；
 * 鉴权与审计在写出任何字节之前完成，失败时仍由全局异常处理返回统一 JSON 错误。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/attachments")
public class RecruitAttachmentController {

    /**
     * 招聘业务附件服务。
     */
    private final IRecruitAttachmentService recruitAttachmentService;

    /**
     * 上传业务附件（作品集/面试材料/背调材料/录用资料/其他；简历请走人才简历版本表）。
     *
     * @param bo   业务定位与附件元数据
     * @param file 上传文件
     * @return 新增的附件ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_ATTACHMENT_UPLOAD)
    @Log(title = "招聘业务附件", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> upload(@Validated RecruitAttachmentBo bo,
                          @RequestPart("file") MultipartFile file) {
        return R.ok(recruitAttachmentService.upload(bo, file));
    }

    /**
     * 分页查询某业务对象的附件列表（按业务类型 + 业务对象ID + 当前版本标识过滤）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 附件分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_ATTACHMENT_PREVIEW)
    @GetMapping
    public R<PageResult<RecruitAttachmentVo>> list(RecruitAttachmentQueryBo bo, PageQuery pageQuery) {
        return R.ok(recruitAttachmentService.queryPage(bo, pageQuery));
    }

    /**
     * 鉴权并审计后预览附件（内联展示）。
     *
     * <p>{@code purpose} 为空时服务端拒绝并写入一条 {@code denied} 审计，
     * 因此不在参数校验层拦截用途。</p>
     *
     * @param id       附件ID
     * @param bo       用途入参
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_ATTACHMENT_PREVIEW)
    @GetMapping("/{id}/preview")
    public void preview(@NotNull(message = "附件ID不能为空")
                        @PathVariable("id") Long id,
                        AttachmentDownloadBo bo,
                        HttpServletResponse response) {
        recruitAttachmentService.preview(id, bo, response);
    }

    /**
     * 鉴权并审计后下载附件（附件方式下载）。
     *
     * @param id       附件ID
     * @param bo       用途入参
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_ATTACHMENT_DOWNLOAD)
    @GetMapping("/{id}/download")
    public void download(@NotNull(message = "附件ID不能为空")
                         @PathVariable("id") Long id,
                         AttachmentDownloadBo bo,
                         HttpServletResponse response) {
        recruitAttachmentService.download(id, bo, response);
    }

    /**
     * 逻辑删除附件（保留数据行与对象存储文件，仅置删除标志）。
     *
     * @param ids 附件ID数组
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_ATTACHMENT_DELETE)
    @Log(title = "招聘业务附件", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotNull(message = "附件ID不能为空")
                          @PathVariable("ids") Long[] ids) {
        recruitAttachmentService.remove(ids);
        return R.ok();
    }

}
