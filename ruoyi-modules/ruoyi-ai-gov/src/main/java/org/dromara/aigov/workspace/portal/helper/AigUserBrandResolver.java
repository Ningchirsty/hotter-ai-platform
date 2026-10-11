package org.dromara.aigov.workspace.portal.helper;

import java.util.Set;

/**
 * 「用户属于哪些品牌」的解析口（主文档线 ④；F-05）。
 *
 * <h3>为什么要单独抽一个口</h3>
 * <p>岗位绑定可以按品牌定向（{@code aig_role_binding.brand_id}）。判定"这个岗位对这个用户可见吗"
 * 需要知道用户所属品牌，而本仓<b>没有任何品牌归属数据源</b>——F-05 已冻：
 * <b>不为岗位可见性新造授权体系，品牌归属属于业务侧主数据</b>。</p>
 *
 * <p>所以这里不猜、也不在可见性判定里写死空集，而是留一个显式的口：默认实现返回空集且
 * {@link #available()} 为 false，含义是「按品牌定向的绑定暂时对谁都不生效」（fail-closed）。
 * 业务侧提供了品牌归属后，换掉实现即可——判定规则（命中品牌即可见）已经由
 * {@code AigRoleVisibilityResolverTest} 钉住，不用重写。</p>
 *
 * <p>与 {@code AigLaunchProjectPolicy} 同一形态：判定口自报"能不能判"，
 * 调用方据此决定要不要把"判不了"如实告诉用户，而不是把它伪装成一个结论。</p>
 *
 * @author ai-gov
 */
public interface AigUserBrandResolver {

    /**
     * 取某个用户所属的品牌ID。
     *
     * @param userId 用户ID（可空）
     * @return 品牌ID集合；取不到或数据源未接入时返回空集（**不是**"这个用户没有品牌"）
     */
    Set<Long> brandsOf(Long userId);

    /**
     * 品牌归属数据源是否已接入。
     *
     * <p>{@code false} 表示"按品牌定向的绑定对谁都不生效"，而不是"所有人都没有品牌"。</p>
     *
     * @return 已接入返回 true
     */
    boolean available();

}
