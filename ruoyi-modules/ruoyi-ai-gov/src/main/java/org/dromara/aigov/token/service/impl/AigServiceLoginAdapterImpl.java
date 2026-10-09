package org.dromara.aigov.token.service.impl;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.token.config.AigServiceTokenProperties;
import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.service.IAigServiceLoginAdapter;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

/**
 * 服务身份 → Sa-Token 会话 的适配器实现。
 *
 * <p><b>四个刻意的参数选择</b>：</p>
 * <ol>
 *     <li>{@code userType = "service"}、{@code userId = tokenId}：既满足
 *         {@code LoginUser.getLoginId()}（{@code userType:userId}）的格式要求，
 *         又让会话 id 与"哪把令牌"一一对应，审计里能直接对上；</li>
 *     <li>{@code menuPermission = scopes}：这是让既有 {@code @SaCheckPermission} 生效的关键
 *         （平台从会话里的 {@code LoginUser} 取权限）；</li>
 *     <li>{@code rolePermission = 空集}：机器身份**不给角色**——角色会牵出数据权限/部门范围等
 *         一整套"人的"语义，给机器发角色等于放大权限面；</li>
 *     <li>{@code isConcurrent=true}：允许并发。若为 false，后一次登录会踢掉前一次的会话，
 *         于是<b>正在并发执行的那个请求会突然失去身份</b>——这类间歇性 401 极难排查。</li>
 * </ol>
 *
 * <p><b>一个已实测的代价（不要在这里写"已优化"）</b>：我原本以为设 {@code isShare=true}
 * 能让同一服务身份复用一个会话，<b>实测不成立</b>——同一身份两次请求拿到的是两个不同的 token 值，
 * 也就是<b>每个请求都会新建一个 Sa-Token 会话</b>。因此这里改为<b>用短 TTL 限幅</b>：
 * 会话数上限 ≈ 该身份每秒请求数 × {@code sessionTimeoutSeconds}（默认 300 秒），
 * 每个会话很小，属于可接受量级。真正省事的做法是"按令牌缓存一个长期会话并把 token 注入请求头"，
 * 那需要在真实 servlet 上下文里验证注入是否生效——留待有集成测试环境时再做。</p>
 *
 * @author ai-gov
 */
@Component
@RequiredArgsConstructor
public class AigServiceLoginAdapterImpl implements IAigServiceLoginAdapter {

    /**
     * 服务身份的 userType。刻意不是一个 {@code sys_user} 行：
     * 机器身份不该出现在"用户"表里，否则它会被人事/权限界面当成一个真人。
     */
    private static final String USER_TYPE_SERVICE = "service";

    private final AigServiceTokenProperties properties;

    @Override
    public void login(AigServiceIdentity identity, String clientId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType(USER_TYPE_SERVICE);
        loginUser.setUserId(identity.tokenId());
        loginUser.setUsername(identity.principal());
        loginUser.setNickname("服务：" + identity.name());
        loginUser.setClientKey(clientId);
        loginUser.setDeviceType(USER_TYPE_SERVICE);
        loginUser.setMenuPermission(new LinkedHashSet<>(identity.scopes()));
        loginUser.setRolePermission(new LinkedHashSet<>());

        SaLoginParameter param = new SaLoginParameter();
        param.setDeviceType(USER_TYPE_SERVICE);
        // deviceId 固定为"服务名"：会话列表里能看出是哪把令牌建的，也便于按身份踢人
        param.setDeviceId(USER_TYPE_SERVICE + ":" + identity.name());
        // 显式给短 TTL：本适配器每请求都会建会话，TTL 就是堆积量的唯一闸门（见类注释）
        param.setTimeout(properties.getSessionTimeoutSeconds());
        // 允许并发：false 会让后一次登录踢掉前一次，正在执行的那个请求会莫名 401
        param.setIsConcurrent(Boolean.TRUE);
        // 刻意不设 isShare：实测它并不能把同一身份的会话合并，留着只会让人以为已经复用
        //
        // clientid 必须进 token 扩展：平台的 SecurityConfig 拦截器会做
        // StpUtil.getExtra("clientid").toString() 与请求头的比对（硬取，没有判空），
        // 缺了它每个"已授权"的请求都会 NPE → 500。这是真机跑出来的（单测没有那个拦截器）。
        //
        // 注意语义：这是"随请求绑定"——每次认证都用本次请求的 clientid，因此换了 clientid 也能通过。
        // 也就是说对机器身份而言 clientid **不是**第二因子（机器凭据就是令牌本身，授权看 scope）。
        // 若将来要让它成为约束，得在签发时绑定并存列（要改表），那是另一个决定。
        param.setExtra(LoginHelper.CLIENT_KEY, clientId);
        LoginHelper.login(loginUser, param);
    }

}
