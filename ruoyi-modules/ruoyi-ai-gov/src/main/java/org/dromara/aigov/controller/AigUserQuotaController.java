package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigUserQuotaBo;
import org.dromara.aigov.domain.vo.AigUserQuotaUsageVo;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调用人均配额（C3：用量配额按人）。
 *
 * <p><b>为什么单独一个控制器</b>：配额是「人」的维度，与能力/模型/策略/绑定都不同轴；
 * 混进任一个既有控制器，都会让「这个接口改的是什么」变得含糊。</p>
 *
 * <p><b>单位是「调用次数」</b>（不是钱）：调用审计里的 {@code cost} 常为「未知而非免费」，
 * 用一列经常未知的数字做配额会算出一本对不上的账；次数是每条调用都有的、可核对的事实。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/quota")
public class AigUserQuotaController extends BaseController {

    private final IAigUserQuotaService quotaService;

    /**
     * 分页查询人均配额清单（含配置）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_QUOTA_LIST)
    @GetMapping("/list")
    public R<PageResult<AigUserQuotaVo>> list(AigUserQuotaBo bo, PageQuery pageQuery) {
        return R.ok(quotaService.queryPage(bo, pageQuery));
    }

    /**
     * 查某人的当前用量（已用 / 上限 / 周期起点）——判断「快用完了还是配错了」的依据。
     *
     * @param userId 用户ID
     * @return 用量
     */
    @SaCheckPermission(AigConstants.PERM_QUOTA_LIST)
    @GetMapping("/usage/{userId:\\d+}")
    public R<AigUserQuotaUsageVo> usage(
        @NotNull(message = "用户ID不能为空") @PathVariable Long userId) {
        return R.ok(quotaService.usage(userId));
    }

    /**
     * 保存人均配额（<b>一人一行</b>：按 userId upsert）。
     *
     * <p>日/月上限都可空：<b>空 = 不限</b>。要「清成不限」就传空值。</p>
     *
     * @param bo 配置
     * @return 配额ID
     */
    @SaCheckPermission(AigConstants.PERM_QUOTA_EDIT)
    @RepeatSubmit
    @PostMapping
    public R<Long> save(@Validated @RequestBody AigUserQuotaBo bo) {
        return R.ok(quotaService.save(bo));
    }

    /**
     * 删除人均配额——含义是<b>回到「不限」</b>，不是「禁止调用」。
     *
     * @param quotaId 配额ID
     * @return 结果
     */
    @SaCheckPermission(AigConstants.PERM_QUOTA_EDIT)
    @RepeatSubmit
    @DeleteMapping("/{quotaId:\\d+}")
    public R<Void> remove(@NotNull(message = "配额ID不能为空") @PathVariable Long quotaId) {
        quotaService.remove(quotaId);
        return R.ok();
    }

    /**
     * 当前登录人的配额用量（供业务侧自查「我还能调几次」）。
     *
     * <p>刻意不带权限点：只能看到自己的用量，不需要治理权限。</p>
     *
     * @return 用量
     */
    @GetMapping("/my-usage")
    public R<AigUserQuotaUsageVo> myUsage() {
        return R.ok(quotaService.usage(LoginHelper.getUserId()));
    }

}
