package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 门户里的「我的资产」分组（按域分栏；增量 8）。
 *
 * <h3>为什么按域分组，而不是合成一个大列表</h3>
 * <p>三个域的"我的"根本不是一回事（图片/视频看 user_id，内容附件看任务负责人），
 * 删除值也不同（{@code '2'} 与 {@code '1'}）。把它们合成一个列表再排序分页，
 * 就得在聚合层把三套口径揉成一个——要么内存归并（受数据量上限约束），
 * 要么建聚合索引表（引入同步一致性问题）。所以这里只回**分组**，
 * 不承诺跨域排序或分页：**宁可不做全局分页，也不做一个会漏/会重的分页**。</p>
 *
 * <p>某个域没有对应提供方时，该组根本不出现；有提供方但没有数据时，该组出现且
 * {@code items} 为空——"没接这个域"与"这个域里你暂时没有东西"是两件事。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalAssetGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 域编码（IMAGE / VIDEO / CONTENT）
     */
    private String domain;

    /**
     * 该域的资产（按域自己的口径过滤后、时间倒序）
     */
    private List<AigPortalMyAssetVo> items = new ArrayList<>();

}
