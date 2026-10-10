package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.workspace.portal.domain.bo.AigPortalDefaultRoleBo;
import org.dromara.aigov.workspace.portal.domain.bo.AigPortalFavoriteBo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalPrefVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;

/**
 * 工作台偏好服务（主文档线增量 5；附件 §6.1 的 favorites）。
 *
 * <h3>两条不可让步的性质</h3>
 * <ol>
 *     <li><b>偏好不参与可见性</b>：收藏/默认岗位都只是"我想先看哪个"。
 *         读取时仍以 {@code /roles} 那条可见性为准；不这么做，就会出现
 *         "收藏过的岗位在停用后仍然看得到"这类越界。</li>
 *     <li><b>写进去的岗位必须对本人可见</b>：否则偏好表会变成一个"验证某个岗位编码存不存在"的探针，
 *         而且用户能收藏一个永远打不开的岗位（点了没反应）。</li>
 * </ol>
 *
 * @author ai-gov
 */
public interface IAigPortalPrefService {

    /**
     * 读取当前用户的偏好（没有偏好时返回空收藏 + 空默认岗位）。
     *
     * @param actor 当前用户
     * @return 偏好
     */
    AigPortalPrefVo getPref(AigPortalActor actor);

    /**
     * 收藏/取消收藏（对本人不可见的岗位会被拒绝）。
     *
     * @param bo    入参
     * @param actor 当前用户
     * @return 更新后的偏好
     */
    AigPortalPrefVo toggleFavorite(AigPortalFavoriteBo bo, AigPortalActor actor);

    /**
     * 设置/清空默认岗位（对本人不可见的岗位会被拒绝；留空表示清空）。
     *
     * @param bo    入参
     * @param actor 当前用户
     * @return 更新后的偏好
     */
    AigPortalPrefVo setDefaultRole(AigPortalDefaultRoleBo bo, AigPortalActor actor);

}
