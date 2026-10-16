package org.dromara.aigov.helper;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 高风险变更的闸门（Q1C 轻量版）：<b>需要 {@code aig:capability:high-risk}</b>。
 *
 * <p><b>它补的是哪个洞</b>：{@code aig:capability:edit} / {@code aig:route:edit} 表示"能改配置"，
 * 但把外发从 {@code N} 改成 {@code Y} 等同于"改变数据出域边界"。之前这两件事是同一个权限点，
 * 于是**一个人**就能把外发打开——这正是设计里点名要防的最大风险。轻量版不建变更单（那要新表）、
 * 也不做审批流，只做"第二个权限点 + 可读拒绝 + 确认框列影响面"，先把这个洞堵上。</p>
 *
 * <p><b>三道一起才生效</b>：①本闸门（服务端）②前端确认框列出引用方（Q6）③改动落操作日志。
 * 只有前端确认等于没防（换个请求就绕过去了），所以判据在服务端。</p>
 *
 * <p><b>fail-closed</b>：没有登录态（调度/后台线程）时按"没有该权限"处理——
 * 后台把外发打开同样是放宽边界，不能因为"不是人点的"就放行。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigHighRiskGuard {

    /**
     * 当前调用上下文是否持有高风险变更权限。
     *
     * @return 持有返回 true
     */
    public boolean allowed() {
        try {
            if (!StpUtil.isLogin()) {
                log.warn("高风险变更判定时没有登录态，按无权限处理（fail-closed）");
                return false;
            }
            return StpUtil.hasPermission(AigConstants.PERM_CAPABILITY_HIGH_RISK);
        } catch (Exception e) {
            // 身份系统异常按无权限处理：宁拒绝不误放
            log.warn("高风险变更权限判定异常，按无权限处理", e);
            return false;
        }
    }

    /**
     * 高风险变更的闸门：没有权限就抛可读异常（写清"这是一次什么变更 + 缺哪个权限 + 能怎么办"）。
     *
     * @param what 这次变更是什么（写进文案，例如"放开外发"）
     */
    public void assertAllowed(String what) {
        if (!allowed()) {
            throw new ServiceException("这是一次高风险变更（" + what + "）：当前账号没有「能力高风险变更」权限点（"
                + AigConstants.PERM_CAPABILITY_HIGH_RISK + "）。"
                + "请联系 AI 治理管理员授权；或者改成不需要该权限的变更（例如仍保持禁止外发）。"
                + "该权限点与「能改配置」刻意分开——能改配置不等于能放宽数据出界。");
        }
    }

}
