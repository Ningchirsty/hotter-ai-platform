package org.dromara.common.web.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理器的日志级别守卫（2026-10-09，P0-3）。
 *
 * <p>钉住两件事，<b>尤其是第二件</b>：</p>
 * <ol>
 *     <li>「请求地址不存在」记 <b>WARN</b>——扫描器、过期书签、老前端缓存都会打到不存在的地址，
 *         这是例行事件。原先记 ERROR 的后果不是多几行日志，而是<b>真故障被淹</b>
 *         （2026-10-09 实测：发布后 15 分钟窗口里 3 条 ERROR 全部来自未登录探针）；</li>
 *     <li>业务异常（{@code ServiceException}）<b>仍然记 ERROR</b>——这是<b>刻意保留的边界</b>，
 *         不是漏改。业务拒绝要被人看见（例如包体安全检查不通过、发布门槛拦下）。
 *         要不要连它一起降级是另一个决定（会影响按 ERROR 的告警面），
 *         本测试的作用是：谁想改它，必须先改这条断言、也就必须先做一个决定。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class GlobalExceptionHandlerTest {

    private static final String LOGGER_NAME = GlobalExceptionHandler.class.getName();

    private GlobalExceptionHandler handler;

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private Level originalLevel;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/aigov/not-exists");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("路由不存在：仍输出这一行，但级别是 WARN（例行事件不该触发 ERROR 告警）")
    void notFoundLogsWarn() {
        R<Void> result = handler.handleNoHandlerFoundException(
            new NoHandlerFoundException("GET", "/aigov/not-exists", null), request);

        List<ILoggingEvent> events = appender.list;
        assertFalse(events.isEmpty(), "级别降级不能变成不记：仍要能看出有人打到了不存在的地址");
        assertEquals(Level.WARN, events.get(0).getLevel(),
            "路由不存在是例行事件，记 ERROR 会把真故障淹掉");
        assertTrue(events.get(0).getFormattedMessage().contains("/aigov/not-exists"),
            events.get(0).getFormattedMessage());
        assertEquals(HttpStatus.HTTP_NOT_FOUND, result.getCode(), "响应体口径没变");
        assertEquals("请求地址不存在", result.getMsg(), "响应体口径没变");
    }

    @Test
    @DisplayName("业务异常：仍然记 ERROR —— 这是刻意保留的边界，不是漏改")
    void serviceExceptionStaysError() {
        handler.handleServiceException(new ServiceException("包体安全检查未通过"), request);

        List<ILoggingEvent> events = appender.list;
        assertFalse(events.isEmpty());
        assertEquals(Level.ERROR, events.get(0).getLevel(),
            "业务拒绝要被人看见；要降级它是一个独立决定，请连同本断言一起改（见类注释）");
        assertTrue(events.get(0).getFormattedMessage().contains("包体安全检查未通过"),
            events.get(0).getFormattedMessage());
    }

}
