package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigAuditQueryBo;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.aigov.service.IAigAuditService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用审计 控制层（只读）。
 * <p>时间范围沿用平台惯例：{@code params[beginTime]} / {@code params[endTime]}。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/audit")
public class AigAuditController {

    private final IAigAuditService auditService;

    /**
     * 分页查询逐次调用审计。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 审计分页结果
     */
    @SaCheckPermission(AigConstants.PERM_AUDIT_LIST)
    @GetMapping("/list")
    public R<PageResult<AigInvocationAuditVo>> list(@Validated({Default.class, QueryGroup.class}) AigAuditQueryBo bo,
                                                    PageQuery pageQuery) {
        return R.ok(auditService.queryPage(bo, pageQuery));
    }

}
