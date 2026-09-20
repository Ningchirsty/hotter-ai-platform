package org.dromara.aigov.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力调用 控制层
 * <p>唯一对外调用入口；路由、调用器选择、Schema 校验与审计全部在 Service 完成，
 * 本层不注入 Mapper、不写任何路由逻辑。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/invoke")
public class AigInvokeController {

    private final IAigInvokeService invokeService;

    /**
     * 同步调用一次能力（会真实执行并写审计）。
     * <p>能力编码以路径为准：body 可省略 {@code capabilityCode}，
     * 因此这里不启用 body 的 {@code @NotBlank} 校验，改为显式补位与校验。</p>
     *
     * @param capabilityCode 能力编码（路径）
     * @param bo             调用入参
     * @return 调用结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_QUERY)
    @RepeatSubmit
    @PostMapping("/{capabilityCode}")
    public R<AigInvokeVo> invoke(@PathVariable("capabilityCode") String capabilityCode,
                                 @RequestBody(required = false) AigInvokeBo bo) {
        AigInvokeBo invokeBo = bo == null ? new AigInvokeBo() : bo;
        if (StringUtils.isBlank(invokeBo.getCapabilityCode())) {
            invokeBo.setCapabilityCode(capabilityCode);
        }
        if (StringUtils.isBlank(invokeBo.getCapabilityCode())) {
            throw new ServiceException("能力编码不能为空");
        }
        return R.ok(invokeService.invoke(invokeBo));
    }

    /**
     * 只做路由决策预览，不真调用、不写审计。
     *
     * @param bo 调用入参
     * @return 决策结果
     */
    @SaCheckPermission(AigConstants.PERM_CAPABILITY_QUERY)
    @PostMapping("/dryRun")
    public R<AigInvokeVo> dryRun(@Validated({Default.class}) @RequestBody AigInvokeBo bo) {
        return R.ok(invokeService.dryRun(bo));
    }

}
