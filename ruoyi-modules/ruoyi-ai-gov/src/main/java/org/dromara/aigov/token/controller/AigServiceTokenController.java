package org.dromara.aigov.token.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.token.config.AigServiceTokenProperties;
import org.dromara.aigov.token.domain.bo.AigServiceTokenIssueBo;
import org.dromara.aigov.token.domain.vo.AigServiceTokenIssuedVo;
import org.dromara.aigov.token.domain.vo.AigServiceTokenVo;
import org.dromara.aigov.token.holder.AigServiceIdentityHolder;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务令牌（机器身份）管理 控制层。
 *
 * <p><b>它守的是一条边界</b>：给外部调用方发"机器身份"，但机器身份<b>不能反过来管理令牌</b>。
 * 这条边界由两道彼此独立的措施保证，缺一不可：</p>
 * <ol>
 *     <li><b>签发侧不可表达</b>：{@code AigServiceTokenServiceImpl#issue} 拒绝任何以
 *         {@code aig:service-token:} 开头的 scope，也拒绝通配 {@code *}。
 *         于是"能调用本控制层的机器令牌"在数据上根本建不出来；</li>
 *     <li><b>运行期显式拒绝</b>（本类的 {@code rejectServiceCaller}）：即便库里已存在这样的
 *         scope（历史数据、或有人直接写 SQL 插了一行），机器身份依然进不来。<b>只靠第 1 条不够</b>——
 *         第 1 条约束的是"通过本接口签发"这件事，约束不了库里的既成事实。</li>
 * </ol>
 *
 * <p>注意本控制层落在 {@code /aigov/*} 之下，而服务令牌过滤器正是按这个前缀注册的——
 * 也就是<b>机器令牌的请求会真实到达这里</b>（不会因为"URL 藏得深"而躲开），
 * 所以上面的第 2 条是必需的，不是多余的。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/service-token")
public class AigServiceTokenController {

    private final IAigServiceTokenService tokenService;
    private final AigServiceTokenProperties properties;

    /**
     * 列出全部服务令牌（不含明文、不含哈希）。
     *
     * @return 令牌清单
     */
    @SaCheckPermission(AigConstants.PERM_SERVICE_TOKEN_LIST)
    @GetMapping("/list")
    public R<List<AigServiceTokenVo>> list() {
        rejectServiceCaller("查看服务令牌清单");
        return R.ok(tokenService.list());
    }

    /**
     * 签发一把服务令牌。
     *
     * <p>{@code isSaveResponseData = false} 是<b>必须的</b>：平台的日志切面默认会把返回体
     * 写进 {@code sys_oper_log}，而 {@code excludeParamNames} <b>只过滤入参、不过滤返回体</b>，
     * 所以不关掉它，明文令牌会被留一份在操作日志表里。</p>
     *
     * @param bo 签发参数
     * @return 签发结果（含明文，唯一一次）
     */
    @SaCheckPermission(AigConstants.PERM_SERVICE_TOKEN_ISSUE)
    @RepeatSubmit
    @Log(title = "服务令牌", businessType = BusinessType.INSERT, isSaveResponseData = false)
    @PostMapping
    public R<AigServiceTokenIssuedVo> issue(
        @Validated({Default.class, AddGroup.class}) @RequestBody AigServiceTokenIssueBo bo) {
        rejectServiceCaller("签发服务令牌");
        IAigServiceTokenService.IssuedToken issued = tokenService.issue(
            bo.getName(), bo.getScopes(), bo.getExpiresAt(), bo.getRemark());
        return R.ok(toVo(issued));
    }

    /**
     * 停用一把服务令牌（保留行，不物理删除）。
     *
     * <p>影响面是"该调用方的所有调用立刻 401"，所以与签发分开授权。</p>
     *
     * @param tokenId 令牌ID
     * @return 操作结果
     */
    @SaCheckPermission(AigConstants.PERM_SERVICE_TOKEN_REVOKE)
    @RepeatSubmit
    @Log(title = "服务令牌", businessType = BusinessType.UPDATE)
    @PutMapping("/{tokenId}/revoke")
    public R<Void> revoke(@NotNull(message = "令牌ID不能为空") @PathVariable("tokenId") Long tokenId) {
        rejectServiceCaller("停用服务令牌");
        if (!tokenService.revoke(tokenId)) {
            // 已停用 / 不存在 都与"叫停它"的诉求一致，但必须让操作人知道"这次没有真的改变什么"
            return R.fail("令牌不存在或已停用");
        }
        return R.ok();
    }

    /**
     * 拒绝机器身份执行令牌管理动作。
     *
     * @param action 动作的中文描述（用于错误信息）
     */
    private void rejectServiceCaller(String action) {
        String principal = AigServiceIdentityHolder.currentPrincipal();
        if (StringUtils.isNotBlank(principal)) {
            throw new ServiceException("服务身份（" + principal + "）不能" + action
                + "：令牌管理必须由人的账号操作（否则机器可以自我提权/自我续期）", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 签发结果 → 返回体。
     *
     * @param issued 签发结果
     * @return 返回体
     */
    private AigServiceTokenIssuedVo toVo(IAigServiceTokenService.IssuedToken issued) {
        AigServiceTokenIssuedVo vo = new AigServiceTokenIssuedVo();
        vo.setTokenId(issued.tokenId());
        vo.setName(issued.name());
        vo.setToken(issued.plaintextToken());
        vo.setTokenPrefix(issued.tokenPrefix());
        vo.setScopes(issued.scopes());
        vo.setExpiresAt(issued.expiresAt());
        vo.setUsageHint(usageHint());
        return vo;
    }

    /**
     * 使用提示。
     *
     * <p>刻意把"本环境开关是否打开"写进提示：否则会出现"令牌签发成功、拿过去却 401"，
     * 而排查方向会被误导到令牌本身（哈希、过期、作用域）——实际原因只是开关没开。</p>
     *
     * @return 提示文本
     */
    private String usageHint() {
        if (!properties.isEnabled()) {
            return "注意：本环境未开启服务令牌认证（aigov.service-token.enabled=false），"
                + "此令牌当前不会被接受；请先确认开关与令牌要部署到哪个环境";
        }
        return "请在请求头 " + properties.getHeaderName() + " 中携带该令牌，并同时携带平台约定的 "
            + "clientid 请求头（生效路径：" + String.join(",", properties.getPathPatterns()) + "）";
    }

}
