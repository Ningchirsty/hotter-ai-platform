package org.dromara.aigov.tool.exec;

/**
 * 工具调用人权限判定端口（设计 §7.5：「工具许可…也要命中<b>调用人权限</b>」）。
 *
 * <p>抽成端口的理由：授权事实必须来自<b>服务端身份</b>（登录态或机器身份），
 * 而不能由调用方在请求里自报"我有这个权限"。抽成端口后，判据来自哪套身份系统可替换，
 * 工具服务只依赖"这次调用的人有没有这个权限点"这一结论。</p>
 *
 * @author ai-gov
 */
public interface ToolPermissionChecker {

    /**
     * 当前调用上下文是否持有指定权限点。
     *
     * @param permissionPoint 权限点（非空）
     * @return 持有返回 true
     */
    boolean hasPermission(String permissionPoint);

}
