package org.dromara.aigov.tool.exec.impl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.tool.exec.ToolPermissionChecker;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token 登录态的权限判定（设计 §7.5）。
 *
 * <p><b>没有登录态时一律返回 false（fail-closed）</b>：调度器/后台线程发起的调用没有"调用人"，
 * 此时若放行，等于把"没人授权"当成"已授权"。机器身份应有自己的授权路径
 * （服务令牌 scope，设计 §10.3）——那属于后续接线，当前如实拒绝并留痕。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class SaTokenToolPermissionChecker implements ToolPermissionChecker {

    @Override
    public boolean hasPermission(String permissionPoint) {
        if (StringUtils.isBlank(permissionPoint)) {
            return false;
        }
        try {
            if (!StpUtil.isLogin()) {
                log.debug("工具调用无登录态，按无权限处理：permissionPoint={}", permissionPoint);
                return false;
            }
            return StpUtil.hasPermission(permissionPoint.trim());
        } catch (Exception e) {
            // 身份系统异常按无权限处理：宁拒绝不误放
            log.warn("工具调用权限判定异常，按无权限处理：permissionPoint={}", permissionPoint, e);
            return false;
        }
    }

}
