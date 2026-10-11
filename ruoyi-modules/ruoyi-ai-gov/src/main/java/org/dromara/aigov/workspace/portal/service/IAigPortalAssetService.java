package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalAssetGroupVo;
import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.aigov.workspace.portal.helper.AigPortalActor;

import java.util.List;

/**
 * 门户「我的资产」跨域聚合服务（增量 8）。
 *
 * <h3>它做与不做的事</h3>
 * <p><b>做</b>：把各业务域通过 {@code MyAssetPort} 提供的"已按各自口径过滤好的我的资产"
 * 按域分组成栏，另提供一个**有界的"最近资产"合并视图**（不是分页）。</p>
 * <p><b>不做</b>：不自己判可见性、不做跨域全局分页、不直连任何业务域的表。
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

    /**
     * "最近资产"：各域各取最近若干条后按时间倒序合并，最多显示固定条数。
     *
     * <p><b>它不是分页</b>：不承诺全量、没有 total、翻不到第 2 页。要看全量去各域自己的入口。
     * 之所以做成这样：真正的跨域分页要么内存归并（受数据量上限约束），要么建聚合索引
     * （引入同步一致性问题）——在拿到真实产品需求前不做。</p>
     *
     * @param actor 当前门户用户
     * @return 合并后的资产（时间倒序，无时间的排最后）；条数有上限
     * @throws org.dromara.common.core.exception.ServiceException 未登录
     */
    List<AigPortalMyAssetVo> recentAssets(AigPortalActor actor);

}

