package org.dromara.hrtalent.controller.talent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.talent.ResumeDownloadBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeQueryBo;
import org.dromara.hrtalent.domain.bo.talent.TalentResumeUploadBo;
import org.dromara.hrtalent.domain.vo.talent.TalentResumeVo;
import org.dromara.hrtalent.service.talent.ITalentParseService;
import org.dromara.hrtalent.service.talent.ITalentResumeService;
import org.springframework.http.MediaType;
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
 * 人才简历版本 控制层（SPEC-P4 §2.1）。
 *
 * <p>路径固定为 {@code /talent/profiles/{id}/resumes} 与 {@code /talent/resumes/{id}/...}，
 * 与前端菜单契约一致；权限串一律取 {@link HrTalentConstants}。
 * 版本计算、资源级鉴权、审计与流式输出全部由 {@link ITalentResumeService} 负责。</p>
 *
 * <p><b>下载说明</b>：简历下载是<b>流式响应</b>，不返回长期匿名地址与对象存储地址（§8.8、§11.1），
 * 因此该接口以 {@code void} 直接写响应体，不包装 {@link R}；鉴权与审计在写出任何字节之前完成，
 * 失败时仍由全局异常处理返回统一 JSON 错误。用途（{@code purpose}）为空时服务端拒绝并写 {@code denied} 审计，
 * 故不在参数校验层拦截用途。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent")
public class TalentResumeController {

    /**
     * 人才简历版本服务。
     */
    private final ITalentResumeService talentResumeService;

    /**
     * 简历解析任务服务（解析任务创建入口）。
     */
    private final ITalentParseService talentParseService;

    /**
     * 查询指定人才的简历版本列表。
     *
     * @param id 人才主档ID
     * @param bo 可选过滤条件
     * @return 简历版本列表（按版本号倒序）
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_LIST)
    @GetMapping("/profiles/{id}/resumes")
    public R<List<TalentResumeVo>> list(@NotNull(message = "人才ID不能为空")
                                        @PathVariable("id") Long id,
                                        TalentResumeQueryBo bo) {
        return R.ok(talentResumeService.listByTalent(id, bo));
    }

    /**
     * 上传简历新版本（<b>不覆盖旧文件</b>，新版本自动成为当前版本）。
     *
     * @param id   人才主档ID
     * @param bo   上传业务对象（来源与备注）
     * @param file 上传文件
     * @return 新建的简历版本（含重复文件提示）
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_UPLOAD)
    @Log(title = "人才简历", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping(value = "/profiles/{id}/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<TalentResumeVo> upload(@NotNull(message = "人才ID不能为空")
                                    @PathVariable("id") Long id,
                                    @Validated TalentResumeUploadBo bo,
                                    @RequestPart("file") MultipartFile file) {
        return R.ok(talentResumeService.upload(id, bo, file));
    }

    /**
     * 指定某简历版本为当前简历。
     *
     * @param id 简历版本ID
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_VERSION)
    @Log(title = "人才简历版本", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/resumes/{id}/current")
    public R<Void> setCurrent(@NotNull(message = "简历ID不能为空")
                              @PathVariable("id") Long id) {
        talentResumeService.setCurrent(id);
        return R.ok();
    }

    /**
     * 查询某简历版本所属人才的全部版本（用于版本对比与历史追溯）。
     *
     * @param id 简历版本ID
     * @return 简历版本列表（按版本号倒序）
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_LIST)
    @GetMapping("/resumes/{id}/versions")
    public R<List<TalentResumeVo>> versions(@NotNull(message = "简历ID不能为空")
                                            @PathVariable("id") Long id) {
        return R.ok(talentResumeService.versions(id));
    }

    /**
     * 鉴权并审计后受控下载简历（用途必填）。
     *
     * @param id       简历版本ID
     * @param bo       用途入参
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_DOWNLOAD)
    @GetMapping("/resumes/{id}/download")
    public void download(@NotNull(message = "简历ID不能为空")
                         @PathVariable("id") Long id,
                         ResumeDownloadBo bo,
                         HttpServletResponse response) {
        talentResumeService.download(id, bo, response);
    }

    /**
     * 创建异步解析任务（<b>不在请求内执行</b> OCR 或大模型调用，§11.1）。
     *
     * @param id 简历版本ID
     * @return 解析任务ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_RESUME_PARSE)
    @Log(title = "简历解析任务", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/resumes/{id}/parse")
    public R<Long> parse(@NotNull(message = "简历ID不能为空")
                         @PathVariable("id") Long id) {
        return R.ok(talentParseService.createTask(id));
    }

}
