package org.dromara.talent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.talent.constant.TalentConstants;
import org.dromara.talent.domain.bo.TlDuplicateConfirmBo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;
import org.dromara.talent.service.ITalentDuplicateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人才重复核对 控制层
 *
 * @author talent
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/talent/duplicate")
public class TalentDuplicateController {

    private final ITalentDuplicateService talentDuplicateService;

    /**
     * 分页查询重复核对清单。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 重复核对分页结果
     */
    @SaCheckPermission(TalentConstants.PERM_DUP_VIEW)
    @GetMapping("/list")
    public R<PageResult<TlTalentDuplicateVo>> list(@Validated({Default.class, QueryGroup.class}) TlTalentDuplicateVo query,
                                                   PageQuery pageQuery) {
        return R.ok(talentDuplicateService.queryPage(query, pageQuery));
    }

    /**
     * 确认重复核对结论（禁止自动合并）。
     *
     * @param bo 确认参数
     * @return 操作结果
     */
    @SaCheckPermission(TalentConstants.PERM_DUP_CONFIRM)
    @RepeatSubmit
    @PostMapping("/confirm")
    public R<Void> confirm(@Validated({Default.class, AddGroup.class}) @RequestBody TlDuplicateConfirmBo bo) {
        talentDuplicateService.confirm(bo);
        return R.ok();
    }
}
