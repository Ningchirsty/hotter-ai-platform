package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitStandardQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportBatchVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportPreviewVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitImportResultVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStandardVo;
import org.dromara.hrtalent.enums.RecruitImportTypeEnum;
import org.dromara.hrtalent.service.recruitment.IRecruitImportService;
import org.dromara.hrtalent.service.recruitment.IRecruitStandardService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 招聘期限标准 控制层。
 *
 * <p>除单条维护外，提供 <b>「下载模板 → 上传预检 → 确认导入」</b> 三段式批量录入：
 * 子公司各岗位的期限标准往往一次要给几十上百条，逐条录不现实。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/standards")
public class RecruitStandardController {

    /**
     * 标准服务
     */
    private final IRecruitStandardService standardService;

    /**
     * 导入服务
     */
    private final IRecruitImportService importService;

    /**
     * 分页查询招聘期限标准。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_STANDARD_LIST)
    @GetMapping
    public R<PageResult<RecruitStandardVo>> list(RecruitStandardQueryBo bo, PageQuery pageQuery) {
        return R.ok(standardService.queryPage(bo, pageQuery));
    }

    /**
     * 标准详情。
     *
     * @param id 标准ID
     * @return 详情
     */
    @SaCheckPermission(HrTalentConstants.PERM_STANDARD_QUERY)
    @GetMapping("/{id}")
    public R<RecruitStandardVo> getInfo(@NotNull(message = "标准ID不能为空") @PathVariable("id") Long id) {
        return R.ok(standardService.getDetail(id));
    }

    /**
     * 新增标准。
     *
     * @param bo 入参
     * @return 标准ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_STANDARD_ADD)
    @Log(title = "招聘期限标准", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Validated({Default.class, AddGroup.class}) @RequestBody RecruitStandardBo bo) {
        return R.ok(standardService.create(bo));
    }

    /**
     * 修改标准。
     *
     * @param id 标准ID
     * @param bo 入参
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_STANDARD_EDIT)
    @Log(title = "招聘期限标准", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}")
    public R<Void> edit(@NotNull(message = "标准ID不能为空") @PathVariable("id") Long id,
                        @Validated({Default.class, EditGroup.class}) @RequestBody RecruitStandardBo bo) {
        bo.setStandardId(id);
        standardService.update(bo);
        return R.ok();
    }

    /**
     * 删除标准（逻辑删除）。
     *
     * @param id 标准ID
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_STANDARD_EDIT)
    @Log(title = "招聘期限标准", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@NotNull(message = "标准ID不能为空") @PathVariable("id") Long id) {
        standardService.remove(id);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 批量导入 ------------------------------------------------------------------ */

    /**
     * 下载导入模板。
     *
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_IMPORT_TEMPLATE)
    @Log(title = "招聘期限标准导入", businessType = BusinessType.EXPORT)
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        importService.writeTemplate(RecruitImportTypeEnum.STANDARD, response);
    }

    /**
     * 上传并预检（不写业务数据）。
     *
     * @param file 文件
     * @return 预检结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_IMPORT_UPLOAD)
    @Log(title = "招聘期限标准导入", businessType = BusinessType.IMPORT)
    @PostMapping("/importPreview")
    public R<RecruitImportPreviewVo> importPreview(@RequestParam("file") MultipartFile file) {
        return R.ok(importService.preview(RecruitImportTypeEnum.STANDARD, file));
    }

    /**
     * 确认导入（仅导入无错误的行）。
     *
     * @param batchId 批次ID
     * @return 导入结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_IMPORT_CONFIRM)
    @Log(title = "招聘期限标准导入", businessType = BusinessType.IMPORT)
    @RepeatSubmit
    @PostMapping("/importConfirm")
    public R<RecruitImportResultVo> importConfirm(@NotNull(message = "批次ID不能为空")
                                                  @RequestParam("batchId") Long batchId) {
        return R.ok(importService.confirm(batchId));
    }

    /**
     * 取消批次。
     *
     * @param batchId 批次ID
     * @return 操作结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_IMPORT_CANCEL)
    @Log(title = "招聘期限标准导入", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/importCancel")
    public R<Void> importCancel(@NotNull(message = "批次ID不能为空")
                                @RequestParam("batchId") Long batchId) {
        importService.cancel(batchId);
        return R.ok();
    }

    /**
     * 导入批次分页（追溯「这批标准是哪次导入进来的」）。
     *
     * @param pageQuery 分页参数
     * @return 批次分页
     */
    @SaCheckPermission(HrTalentConstants.PERM_IMPORT_LIST)
    @GetMapping("/importBatches")
    public R<PageResult<RecruitImportBatchVo>> importBatches(PageQuery pageQuery) {
        return R.ok(importService.queryBatchPage(RecruitImportTypeEnum.STANDARD.getCode(), pageQuery));
    }

}
