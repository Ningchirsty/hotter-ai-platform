package org.dromara.aigov.token.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.token.config.AigServiceTokenProperties;
import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.domain.bo.AigServiceTokenIssueBo;
import org.dromara.aigov.token.domain.vo.AigServiceTokenIssuedVo;
import org.dromara.aigov.token.holder.AigServiceIdentityHolder;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 服务令牌管理接口的行为锁定测试。
 *
 * <p><b>它守的是什么</b>：这是"发凭据"的接口，最坏的结果不是报错，而是
 * <b>机器身份能给自己发凭据</b>（自我提权/自我续期）。这类缺陷不会在日常使用中暴露，
 * 所以这里刻意用"直接摆放 holder"的方式模拟机器请求，断言三个动作全部被拒、
 * 且<b>一次都没有碰到业务服务</b>（拒在门口，而不是先做再拒）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigServiceTokenControllerTest {

    private IAigServiceTokenService tokenService;
    private AigServiceTokenProperties properties;
    private AigServiceTokenController controller;

    @BeforeEach
    void setUp() {
        tokenService = mock(IAigServiceTokenService.class);
        properties = new AigServiceTokenProperties();
        controller = new AigServiceTokenController(tokenService, properties);
    }

    @AfterEach
    void tearDown() {
        AigServiceIdentityHolder.clear();
    }

    @Test
    @DisplayName("★ 机器身份不能查看清单：403 且不触达业务服务")
    void serviceIdentityCannotListTokens() {
        asService("sneaky-worker");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.list());

        assertEquals(HttpStatus.FORBIDDEN, ex.getCode().intValue(), "应是 403（没有权限），不是 500");
        assertTrue(ex.getMessage().contains("service:sneaky-worker"), "错误信息要指出是哪个身份被拒");
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("★ 机器身份不能签发令牌（自我提权）：403 且不触达业务服务")
    void serviceIdentityCannotIssueToken() {
        asService("sneaky-worker");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.issue(newIssueBo()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getCode().intValue());
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("★ 机器身份不能停用令牌（可用来打掉别人的机器身份）：403 且不触达业务服务")
    void serviceIdentityCannotRevokeToken() {
        asService("sneaky-worker");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.revoke(7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getCode().intValue());
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("人的账号可以签发：明文只在返回体里出现一次，并给出使用方式")
    void humanCanIssueAndReceivesPlaintextOnce() {
        when(tokenService.issue(anyString(), any(), any(), any())).thenReturn(
            new IAigServiceTokenService.IssuedToken(31L, "vibeposter-worker", "hsvc_plain",
                "hsvc_plainpref", "aig:capability:query", LocalDateTime.of(2026, 12, 31, 23, 59)));

        R<AigServiceTokenIssuedVo> result = controller.issue(newIssueBo());

        assertEquals(200, result.getCode());
        AigServiceTokenIssuedVo vo = result.getData();
        assertNotNull(vo);
        assertEquals("hsvc_plain", vo.getToken(), "签发接口必须把明文交回给操作人（清单里再也取不到）");
        assertEquals("hsvc_plainpref", vo.getTokenPrefix());
        assertEquals("aig:capability:query", vo.getScopes(), "返回的应是归一化后真正落库的 scope");
        // 默认开关是关的：必须如实告知"现在拿过去也不会被接受"，否则排查方向会被误导
        assertTrue(vo.getUsageHint().contains("aigov.service-token.enabled=false"),
            "开关未开时必须如实提示，而不是只给一句'请放请求头'");
    }

    @Test
    @DisplayName("开关打开时，使用提示给出请求头名与生效路径")
    void usageHintNamesHeaderWhenEnabled() {
        properties.setEnabled(true);
        properties.setHeaderName("X-Service-Token");
        properties.setPathPatterns(List.of("/aigov/*"));
        when(tokenService.issue(anyString(), any(), any(), any())).thenReturn(
            new IAigServiceTokenService.IssuedToken(32L, "w", "hsvc_x", "hsvc_xpref", "", null));

        R<AigServiceTokenIssuedVo> result = controller.issue(newIssueBo());

        assertTrue(result.getData().getUsageHint().contains("X-Service-Token"), "要能直接抄走请求头名");
        assertTrue(result.getData().getUsageHint().contains("/aigov/*"), "要写清生效路径");
    }

    @Test
    @DisplayName("停用一把已停用的令牌 → 明确回答'没有改变什么'，不谎报成功")
    void revokeOfAlreadyDisabledTokenIsReportedHonestly() {
        when(tokenService.revoke(any())).thenReturn(false);

        R<Void> result = controller.revoke(9L);

        assertEquals(500, result.getCode(), "幂等成功会骗人：这就是失败返回");
        assertTrue(result.getMsg().contains("不存在或已停用"), "要说明为什么没生效");
    }

    @Test
    @DisplayName("★ 签发接口的日志不得记录返回体（返回体里是明文令牌）")
    void issueEndpointMustNotLogResponseBody() throws Exception {
        Log log = issueMethod().getAnnotation(Log.class);

        assertNotNull(log, "签发是要留痕的动作（谁在什么时候发了哪把令牌）");
        assertFalse(log.isSaveResponseData(),
            "★ 平台日志切面默认把返回体写进 sys_oper_log，而 excludeParamNames 只过滤入参——"
                + "不关掉它，明文令牌就会被留一份在操作日志表里");
        assertTrue(log.isSaveRequestData(), "入参里没有凭据，保留入参才能追溯是谁签的");
    }

    @Test
    @DisplayName("★ 三个动作各用独立权限码：能看清单 ≠ 能签发 ≠ 能停用")
    void eachActionRequiresItsOwnPermission() throws Exception {
        assertEquals(AigConstants.PERM_SERVICE_TOKEN_LIST, permissionOf(listMethod()));
        assertEquals(AigConstants.PERM_SERVICE_TOKEN_ISSUE, permissionOf(issueMethod()));
        assertEquals(AigConstants.PERM_SERVICE_TOKEN_REVOKE,
            permissionOf(AigServiceTokenController.class.getMethod("revoke", Long.class)));
        assertFalse(AigConstants.PERM_SERVICE_TOKEN_ISSUE.equals(AigConstants.PERM_SERVICE_TOKEN_LIST));
        assertFalse(AigConstants.PERM_SERVICE_TOKEN_REVOKE.equals(AigConstants.PERM_SERVICE_TOKEN_ISSUE));
    }

    /**
     * 模拟"当前请求带着服务身份"（过滤器认证通过后的状态）。
     *
     * @param name 服务名
     */
    private void asService(String name) {
        AigServiceIdentityHolder.set(new AigServiceIdentity(9001L, name, Set.of("aig:capability:query")));
    }

    /**
     * 一个合法的签发入参。
     *
     * @return 入参
     */
    private AigServiceTokenIssueBo newIssueBo() {
        AigServiceTokenIssueBo bo = new AigServiceTokenIssueBo();
        bo.setName("vibeposter-worker");
        bo.setScopes("aig:capability:query");
        bo.setRemark("试点：内容生产定时任务");
        return bo;
    }

    private Method listMethod() throws Exception {
        return AigServiceTokenController.class.getMethod("list");
    }

    private Method issueMethod() throws Exception {
        return AigServiceTokenController.class.getMethod("issue", AigServiceTokenIssueBo.class);
    }

    private String permissionOf(Method method) {
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation, method.getName() + " 必须有权限校验（不能靠前端藏按钮）");
        assertEquals(1, annotation.value().length, method.getName() + " 应只声明一个权限码");
        return annotation.value()[0];
    }

}
