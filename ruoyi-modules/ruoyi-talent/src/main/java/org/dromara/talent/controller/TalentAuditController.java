package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.vo.TlSensitiveAuditVo;
import org.dromara.talent.service.ITalentAuditService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感操作审计 控制层
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/audit")
public class TalentAuditController {

    private final ITalentAuditService talentAuditService;

    /**
     * 分页查询敏感操作审计日志。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 审计日志分页结果
     */
    @SaCheckPermission(TalentConstants.PERM_AUDIT_LIST)
    @GetMapping("/list")
    public R<PageResult<TlSensitiveAuditVo>> list(@Validated({Default.class, QueryGroup.class}) TlSensitiveAuditVo query,
                                                 PageQuery pageQuery) {
        return R.ok(talentAuditService.queryPage(query, pageQuery));
    }
}
