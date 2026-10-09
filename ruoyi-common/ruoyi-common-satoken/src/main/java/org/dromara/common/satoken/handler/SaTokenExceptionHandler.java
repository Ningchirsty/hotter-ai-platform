package org.dromara.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SaToken异常处理器
 *
 * <p><b>两个 handler 都记 warn，不记 error</b>（2026-10-09 改）：未登录/登录态失效与
 * 权限码不足都是<b>认证授权层的例行结果</b>，不是平台故障——健康探针、过期的浏览器会话、
 * 用户点了一个自己没权限的按钮，都会走到这里。原先记 error 的后果是<b>真故障被淹</b>：
 * 发布后 15 分钟窗口里的 3 条 ERROR 全部来自未登录探针。
 * 判定口径：<b>error = 平台自己出了问题；例行失败记 warn</b>。
 * warn 一样落盘（logback 默认 INFO 级别即输出），只是不再触发按 ERROR 的告警。</p>
 *
 * @author Lion Li
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /**
     * 处理权限码校验失败异常。
     *
     * @param e       异常信息
     * @param request 当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public R<Void> handleNotAccessException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String reason = e instanceof NotRoleException ? "角色权限校验失败" : "权限码校验失败";
        log.warn("请求地址'{}',{}'{}'", requestURI, reason, e.getMessage());
        return R.fail(HttpStatus.HTTP_FORBIDDEN, "没有访问权限，请联系管理员授权");
    }

    /**
     * 处理未登录或登录态失效异常。
     *
     * @param e       异常信息
     * @param request 当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, e.getMessage());
        String msg = switch (e.getType()) {
            case NotLoginException.TOKEN_TIMEOUT,
                 NotLoginException.TOKEN_FREEZE -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "当前账号已在其他设备登录，您已被强制下线";
            case NotLoginException.KICK_OUT -> "账号已被管理员强制下线";
            default -> "登录状态异常，请重新登录";
        };
        return R.fail(HttpStatus.HTTP_UNAUTHORIZED, msg);
    }

}
