package org.dromara.common.satoken.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.servlet.http.HttpServletRequest;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SaToken 异常处理器的日志级别守卫（2026-10-09，P0-3）。
 *
 * <p><b>这条口径为什么要测试来钉</b>：把 {@code error} 改成 {@code warn} 是一行改动，
 * 而它的价值全在"真故障不再被例行失败淹没"上——恰恰是<b>改错了也不会报错</b>的那类改动：
 * 有人顺手改回 error（或新加一个 handler 又用了 error），线上不会有任何异常，
 * 只会慢慢变回"ERROR 里全是探针"。所以这里断言两件事：</p>
 * <ol>
 *     <li><b>仍然输出</b>——级别降级不能变成"不记了"（可见性一点没少，只是不再触发按 ERROR 的告警）；</li>
 *     <li><b>级别是 WARN</b>——未登录/权限不足是认证授权层的例行结果，不是平台故障。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SaTokenExceptionHandlerTest {

    /**
     * handler 内部用的 logger 名（见类上的 {@code @Slf4j}）
     */
    private static final String LOGGER_NAME = SaTokenExceptionHandler.class.getName();

    private SaTokenExceptionHandler handler;

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private Level originalLevel;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new SaTokenExceptionHandler();
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/aigov/task/1");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("未登录：仍输出这一行，但级别是 WARN 不是 ERROR（例行失败不该触发 ERROR 告警）")
    void notLoginLogsWarn() {
        R<Void> result = handler.handleNotLoginException(
            NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN, "无 token", null),
            request);

        List<ILoggingEvent> events = appender.list;
        assertFalse(events.isEmpty(), "级别降级不能变成不记：探针与过期会话仍要留下痕迹");
        assertEquals(Level.WARN, events.get(0).getLevel(),
            "未登录是例行结果，记 ERROR 会淹没真故障");
        assertTrue(events.get(0).getFormattedMessage().contains("/aigov/task/1"),
            "仍要写明是哪个地址：" + events.get(0).getFormattedMessage());
        assertEquals(401, result.getCode(), "响应体口径没变");
    }

    @Test
    @DisplayName("权限不足：同样是 WARN，401/403 的响应体与文案都不变")
    void notPermissionLogsWarn() {
        R<Void> denied = handler.handleNotAccessException(
            new NotPermissionException("aig:task:operate"), request);
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("权限码校验失败"),
            appender.list.get(0).getFormattedMessage());
        assertEquals(403, denied.getCode());

        appender.list.clear();
        R<Void> roleDenied = handler.handleNotAccessException(new NotRoleException("admin"), request);
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("角色权限校验失败"),
            appender.list.get(0).getFormattedMessage());
        assertEquals(403, roleDenied.getCode());
    }

}
