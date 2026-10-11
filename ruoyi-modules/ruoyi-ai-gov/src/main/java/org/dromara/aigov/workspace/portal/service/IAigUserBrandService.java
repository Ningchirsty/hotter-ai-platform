package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.workspace.portal.domain.bo.AigUserBrandBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigUserBrandVo;

import java.util.List;

/**
 * 用户↔品牌归属管理（④；运维方式）。
 *
 * <h3>它管理的是什么</h3>
 * <p>岗位绑定可以按品牌定向（{@code aig_role_binding.brand_id}）。本服务维护
 * "某个用户属于哪些品牌"——这是**最小主数据登记**，不是授权体系：
 * 它只改变"按品牌定向的岗位对谁生效"，不提高任何用户在平台上的其它权限。</p>
 *
 * <h3>停用而不是删除</h3>
 * <p>撤销用 {@code status='1'（停用）}，不物理删除：重新登记时可以复用同一行，
 * 也不会让唯一键 {@code (user_id, brand_id)} 被"已删但仍在"的行挡住
 * （本仓已在别的表上踩过逻辑删除与唯一键的相互作用）。</p>
 *
 * @author ai-gov
 */
public interface IAigUserBrandService {

    /**
     * 查某个用户的品牌归属（含已停用的行，便于运维看清历史）。
     *
     * @param userId 用户ID
     * @return 归属清单（按主键倒序）
     */
    List<AigUserBrandVo> listByUser(Long userId);

    /**
     * 登记/恢复某人的品牌归属（幂等：同一对 user+brand 只会有一条，重复登记返回同一条）。
     *
     * @param bo 入参
     * @return 归属记录ID
     */
    Long grant(AigUserBrandBo bo);

    /**
     * 停用一条归属（不物理删除）。
     *
     * @param userBrandId 归属记录ID
     */
    void revoke(Long userBrandId);

}
