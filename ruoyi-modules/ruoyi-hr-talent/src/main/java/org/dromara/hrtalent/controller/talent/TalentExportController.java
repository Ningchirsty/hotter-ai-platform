package org.dromara.hrtalent.controller.talent;

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
import org.dromara.hrtalent.domain.bo.talent.TalentExportCreateBo;
import org.dromara.hrtalent.domain.bo.talent.TalentExportQueryBo;
import org.dromara.hrtalent.domain.vo.talent.TalentExportTaskVo;
import org.dromara.hrtalent.service.talent.ITalentExportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人才导出任务 控制层（SPEC-P4 §2.6 F 线、设计文档 §8.20）。
 *
 * <p><b>路径契约</b>（不得自创）：{@code POST /talent/profiles/export}、
 * {@code GET /talent/exports}、{@code GET /talent/exports/{id}/download}。
 * 权限串一律取 {@link HrTalentConstants}。</p>
 *
 * <p><b>下载说明</b>：受控下载是<b>流式响应</b>，不返回任何对象存储地址或预签名地址（§11.1），
 * 因此该接口以 {@code void} 直接写响应体，不包装 {@link R}；鉴权、用途校验与过期校验都在写出
 * 任何字节之前完成，失败时仍由全局异常处理返回统一 JSON 错误。用途（{@code purpose}）为空时
 * 服务端拒绝并写 {@code denied} 审计，故不在参数校验层拦截用途。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent")
public class TalentExportController {

    /**
     * 人才导出任务服务。
     */
    private final ITalentExportService talentExportService;

    /**
     * 创建人才导出（普通台账 / 敏感台账）。
     *
     * <p>敏感台账的用途（{@code purpose}）与服务层独立权限校验一并完成，失败会写 {@code denied} 审计。</p>
     *
     * @param bo 导出入参
     * @return 导出任务ID
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EXPORT)
    @Log(title = "人才导出", businessType = BusinessType.EXPORT)
    @RepeatSubmit
    @PostMapping("/profiles/export")
    public R<Long> export(@Validated @RequestBody TalentExportCreateBo bo) {
        return R.ok(talentExportService.createExport(bo));
    }

    /**
     * 导出任务分页列表。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 导出任务分页结果（只含系统内受控下载地址，不含对象存储标识）
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EXPORT)
    @GetMapping("/exports")
    public R<PageResult<TalentExportTaskVo>> list(TalentExportQueryBo bo, PageQuery pageQuery) {
        return R.ok(talentExportService.queryPage(bo, pageQuery));
    }

    /**
     * 受控下载导出结果文件（<b>用途必填</b>，过期拒绝）。
     *
     * @param id       导出任务ID
     * @param purpose  下载用途
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_PROFILE_EXPORT)
    @GetMapping("/exports/{id}/download")
    public void download(@NotNull(message = "导出任务ID不能为空")
                         @PathVariable("id") Long id,
                         @RequestParam(value = "purpose", required = false) String purpose,
                         HttpServletResponse response) {
        talentExportService.download(id, purpose, response);
    }

}
