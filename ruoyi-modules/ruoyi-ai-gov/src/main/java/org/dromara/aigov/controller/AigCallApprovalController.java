package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigCallApprovalBo;
import org.dromara.aigov.domain.bo.AigCallApprovalDecideBo;
import org.dromara.aigov.domain.vo.AigCallApprovalSweepVo;
import org.dromara.aigov.domain.vo.AigCallApprovalVo;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调用授权审批（C3：把 {@code aig_route_policy.require_approval} 做实）。
 *
 * <p><b>操作人一律取自登录态</b>（{@code LoginHelper}），请求体里的任何 id 都不作数——
 * 否则「谁申请的、谁批的」就成了客户端说了算，那张单子也就不成其为审批。</p>
 *
 * <p>权限分三档：看清单（list）、提交/撤回自己的申请（apply）、审批（approve）。
 * 「看自己的」单独一个接口且<b>不要权限点</b>——只看自己的申请与授权不需要治理权限
 * （与人均配额的 {@code /my-usage} 同一口径）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/approval")
public class AigCallApprovalController extends BaseController {

    private final IAigCallApprovalService approvalService;

    /**
     * 分页查询审批单/授权清单（治理台）。
     *
     * @param bo        查询条件（能力/数据等级/申请人/审批人/状态）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_APPROVAL_LIST)
    @GetMapping("/list")
    public R<PageResult<AigCallApprovalVo>> list(AigCallApprovalBo bo, PageQuery pageQuery) {
        return R.ok(approvalService.queryPage(bo, pageQuery));
    }

    /**
     * 查<b>自己</b>的申请与授权。
     *
     * <p>刻意不带权限点：只看自己提交过什么、哪张还在有效期内，不需要治理权限。
     * {@code requesterId} 由登录态<b>覆盖</b>，客户端传什么都不作数。</p>
     *
     * @param bo        查询条件（状态/能力等可选）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @GetMapping("/my")
    public R<PageResult<AigCallApprovalVo>> my(AigCallApprovalBo bo, PageQuery pageQuery) {
        AigCallApprovalBo query = bo == null ? new AigCallApprovalBo() : bo;
        query.setRequesterId(LoginHelper.getUserId());
        return R.ok(approvalService.queryPage(query, pageQuery));
    }

    /**
     * 提交一张调用授权申请单。
     *
     * <p>被拒的调用在审计与错误码里会给出 {@code APPROVAL_REQUIRED}，页面据此引导到这里。</p>
     *
     * @param bo 申请内容（能力、数据等级、理由）
     * @return 审批单ID
     */
    @SaCheckPermission(AigConstants.PERM_APPROVAL_APPLY)
    @RepeatSubmit
    @PostMapping
    public R<Long> apply(@Validated @RequestBody AigCallApprovalBo bo) {
        return R.ok(approvalService.apply(LoginHelper.getUserId(), LoginHelper.getUsername(), bo));
    }

    /**
     * 批准或驳回一张待审批单。
     *
     * @param approvalId 审批单ID
     * @param bo         决定（批准/驳回 + 意见）
     * @return 结果
     */
    @SaCheckPermission(AigConstants.PERM_APPROVAL_APPROVE)
    @RepeatSubmit
    @PostMapping("/{approvalId:\\d+}/decide")
    public R<Void> decide(@NotNull(message = "审批单ID不能为空") @PathVariable Long approvalId,
                          @Validated @RequestBody AigCallApprovalDecideBo bo) {
        approvalService.decide(approvalId, LoginHelper.getUserId(), LoginHelper.getUsername(), bo);
        return R.ok();
    }

    /**
     * 撤回自己提交的、尚未出结论的申请单。
     *
     * @param approvalId 审批单ID
     * @return 结果
     */
    @SaCheckPermission(AigConstants.PERM_APPROVAL_APPLY)
    @RepeatSubmit
    @PostMapping("/{approvalId:\\d+}/cancel")
    public R<Void> cancel(@NotNull(message = "审批单ID不能为空") @PathVariable Long approvalId) {
        approvalService.cancel(approvalId, LoginHelper.getUserId());
        return R.ok();
    }

    /**
     * 手动触发一次「超时扫描」（把超过审批时限的待审批单置为已超时）。
     *
     * <p>给集群/已有调度平台的部署方式用：内置定时扫描默认关闭，
     * 由外部 cron 或 SnailJob 调这个接口即可（见 {@code AigCallApprovalExpireJob}）。</p>
     *
     * @return 扫描结果
     */
    @SaCheckPermission(AigConstants.PERM_APPROVAL_APPROVE)
    @RepeatSubmit
    @PostMapping("/expire-scan")
    public R<AigCallApprovalSweepVo> expireScan() {
        return R.ok(approvalService.expireOverdue());
    }

}
