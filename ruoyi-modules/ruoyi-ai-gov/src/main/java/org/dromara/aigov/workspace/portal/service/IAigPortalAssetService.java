package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalAssetGroupVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;

import java.util.List;

/**
 * 门户「我的资产」跨域聚合服务（增量 8）。
 *
 * <h3>它做与不做的事</h3>
 * <p><b>做</b>：把各业务域通过 {@code MyAssetPort} 提供的"已按各自口径过滤好的我的资产"
 * 按域分组成栏。</p>
 * <p><b>不做</b>：不自己判可见性、不跨域排序/分页、不直连任何业务域的表。
 * "我的"在三个域里不是一回事，聚合层没有资格替它们统一——那只会变成第二份规则。</p>
 *
 * @author ai-gov
 */
public interface IAigPortalAssetService {

    /**
     * 取当前用户的资产，按域分组（每域最多几条）。
     *
     * @param actor 当前门户用户
     * @return 分组（顺序固定：IMAGE / VIDEO / CONTENT / 其他）
     * @throws org.dromara.common.core.exception.ServiceException 未登录
     */
    List<AigPortalAssetGroupVo> myAssets(AigPortalActor actor);

}
