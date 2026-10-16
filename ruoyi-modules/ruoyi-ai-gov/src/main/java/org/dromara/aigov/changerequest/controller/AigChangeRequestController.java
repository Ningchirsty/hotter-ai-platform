package org.dromara.aigov.changerequest.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.changerequest.domain.bo.AigChangeRequestBo;
import org.dromara.aigov.changerequest.domain.vo.AigChangeRequestVo;
import org.dromara.aigov.changerequest.service.IAigChangeRequestService;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.AddGroup;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 高风险变更单接口（Q1C 完整版）。
 *
 * <h3>权限刻意分两档、且**不新增权限点**</h3>
 * <ul>
 *     <li><b>申请</b>：{@code aig:capability:edit} —— 能改配置的人就能提变更单。
 *         这正是完整版的意义：<b>没有</b>高风险权限的人也能把改动提出来，而不是去 IM 上找人；</li>
 *     <li><b>批准/驳回</b>：{@code aig:capability:high-risk} —— 与轻量版的闸门同一权限点，
 *         所以"能放宽边界的人"就是"该批这件事的人"，不需要再造一个权限点（也就没有授权变更）。</li>
 * </ul>
 *
 * <p><b>批准即执行</b>：批准会当场执行改动（复用既有服务方法，Q1C 闸门仍生效），
 * 执行前与现场做乐观并发校验；冲突则置为 CONFLICTED 并给出原因。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/change-request")
public class AigChangeRequestController extends BaseController {

    private final IAigChangeRequestService changeRequestService;

    /**
     * 提交变更单。
     *
     * @param bo 申请内容（能力 / 变更类型 / 目标值 / 理由）
     * @return 变更单ID
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_EDIT)
    @RepeatSubmit
    @PostMapping
    public R<Long> submit(@Validated({Default.class, AddGroup.class}) @RequestBody AigChangeRequestBo bo) {
        return R.ok(changeRequestService.submit(bo, LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    /**
     * 分页查询变更单（可按能力 / 状态过滤）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_LIST)
    @GetMapping("/list")
    public R<PageResult<AigChangeRequestVo>> list(@Validated({Default.class}) AigChangeRequestBo bo,
                                                  PageQuery pageQuery) {
        return R.ok(changeRequestService.queryPage(bo, pageQuery));
    }

    /**
     * 我的变更单（按登录态覆盖申请人，只看自己提交的）。
     *
     * @param bo        查询条件（能力/状态）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_LIST)
    @GetMapping("/my")
    public R<PageResult<AigChangeRequestVo>> my(@Validated({Default.class}) AigChangeRequestBo bo,
                                                PageQuery pageQuery) {
        AigChangeRequestBo query = bo == null ? new AigChangeRequestBo() : bo;
        query.setRequesterId(LoginHelper.getUserId());
        return R.ok(changeRequestService.queryPage(query, pageQuery));
    }

    /**
     * 批准并生效（需高风险权限；申请人不得自批）。
     *
     * <p>返回处理后的变更单：status = APPLIED 表示已生效；CONFLICTED 表示**未生效**
     * （与现场冲突或执行失败，原因在 resultRemark）——这时页面要如实展示，不要显示"成功"。</p>
     *
     * @param changeRequestId 变更单ID
     * @param remark          审批意见（可空）
     * @return 处理后的变更单
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_HIGH_RISK)
    @RepeatSubmit
    @PostMapping("/{changeRequestId:\\d+}/approve")
    public R<AigChangeRequestVo> approve(@NotNull(message = "变更单ID不能为空")
                                         @PathVariable("changeRequestId") Long changeRequestId,
                                         @RequestParam(required = false) String remark) {
        return R.ok(changeRequestService.approve(changeRequestId, remark,
            LoginHelper.getUserId(), LoginHelper.getUsername()));
    }

    /**
     * 驳回（需高风险权限；必须写明理由）。
     *
     * @param changeRequestId 变更单ID
     * @param remark          驳回理由
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_HIGH_RISK)
    @RepeatSubmit
    @PostMapping("/{changeRequestId:\\d+}/reject")
    public R<Void> reject(@NotNull(message = "变更单ID不能为空")
                          @PathVariable("changeRequestId") Long changeRequestId,
                          @RequestParam String remark) {
        changeRequestService.reject(changeRequestId, remark, LoginHelper.getUserId(), LoginHelper.getUsername());
        return R.ok();
    }

    /**
     * 撤回（只有申请人本人可以撤，且只能在待批准时）。
     *
     * @param changeRequestId 变更单ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_EDIT)
    @RepeatSubmit
    @PostMapping("/{changeRequestId:\\d+}/cancel")
    public R<Void> cancel(@NotNull(message = "变更单ID不能为空")
                          @PathVariable("changeRequestId") Long changeRequestId) {
        changeRequestService.cancel(changeRequestId, LoginHelper.getUserId());
        return R.ok();
    }
}
