package org.dromara.hrtalent.controller.recruitment;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitSensitiveAuditQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitSensitiveAuditVo;
import org.dromara.hrtalent.service.recruitment.ISensitiveAuditService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感操作审计 控制层（SPEC-P3 §2.5 / §3.6）。
 *
 * <p>路径固定为 {@code /recruit/audits}，权限串取 {@link HrTalentConstants} 常量。
 * 审计表是<b>追加型</b>表：本控制层<b>只提供</b>查询与导出，
 * <b>不提供</b>新增、修改、删除接口；写入统一由 {@code support/SensitiveAuditRecorder} 完成。</p>
 *
 * @author hr-talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/recruit/audits")
public class SensitiveAuditController {

    /**
     * 敏感操作审计查询服务。
     */
    private final ISensitiveAuditService sensitiveAuditService;

    /**
     * 分页查询敏感操作审计。
     *
     * @param bo        查询条件（事件类型/业务类型/操作人/事件时间区间等）
     * @param pageQuery 分页参数
     * @return 审计分页结果
     */
    @SaCheckPermission(HrTalentConstants.PERM_AUDIT_LIST)
    @GetMapping
    public R<PageResult<RecruitSensitiveAuditVo>> list(RecruitSensitiveAuditQueryBo bo, PageQuery pageQuery) {
        return R.ok(sensitiveAuditService.queryPage(bo, pageQuery));
    }

    /**
     * 按当前筛选条件导出审计记录。
     *
     * <p>导出为文件流响应，不包装 {@link R}；导出动作本身会写入一条 {@code export} 审计。</p>
     *
     * @param bo       查询条件
     * @param response HTTP 响应
     */
    @SaCheckPermission(HrTalentConstants.PERM_AUDIT_EXPORT)
    @Log(title = "敏感操作审计导出", businessType = BusinessType.EXPORT)
    @RepeatSubmit
    @GetMapping("/export")
    public void export(RecruitSensitiveAuditQueryBo bo, HttpServletResponse response) {
        sensitiveAuditService.export(bo, response);
    }

}
