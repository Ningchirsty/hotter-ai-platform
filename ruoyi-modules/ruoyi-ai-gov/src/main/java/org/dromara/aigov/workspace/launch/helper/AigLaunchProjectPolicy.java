package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.workspace.portal.helper.AigPortalActor;

/**
 * 项目权判定（主文档线增量 4；附件 §12 的 {@code PROJECT_ACCESS_DENIED} / ROLE-006）。
 *
 * <h3>为什么是一个可替换的判定口，而不是直接查表</h3>
 * <p>"这个用户能不能用这个项目"的答案属于**业务域主数据**（创作/内容/视频各域自己的项目表与归属规则），
 * 不在本模块里。把判定抽成一个口，本模块只消费结论：将来哪个域提供了归属来源，
 * 就换这个口的一个实现，而校验链、错误码、用例都不用动。</p>
 *
 * <h3>默认实现是 **fail-closed**</h3>
 * <p>今天没有任何可用的项目归属来源。这时有两种做法，差别很大：
 * <ul>
 *     <li>"判不了就放行"——跨组织拿到别人 projectId 的请求会被放行，且**没有任何日志或报错**，
 *         等于把一个越权面伪装成"功能正常"；</li>
 *     <li>"判不了就拒绝"——带 projectId 的启动会被明确拒绝（{@code PROJECT_ACCESS_DENIED}），
 *         用户看到的是"项目绑定暂不可用"，运维看到的是同一件事。</li>
 * </ul>
 * 取后者：**没有校验的允许，是比拒绝更糟的默认值**。</p>
 *
 * @author ai-gov
 */
@FunctionalInterface
public interface AigLaunchProjectPolicy {

    /**
     * 当前用户是否可以使用该项目。
     *
     * @param projectId 项目ID（调用方保证非空时才问）
     * @param actor     当前用户
     * @return 允许返回 true
     */
    boolean allowed(Long projectId, AigPortalActor actor);

    /**
     * 今天是否真的能判定项目权（用于把"判不了"与"判过但没通过"在文案上区分开）。
     *
     * @return 能判定返回 true
     */
    default boolean enforceable() {
        return false;
    }

}
