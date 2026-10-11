package org.dromara.aigov.workspace.portal.service;

import org.dromara.aigov.workspace.portal.domain.vo.AigPortalMyAssetVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 资产聚合索引服务（增量 11；跨域全局分页的"索引表"路线）。
 *
 * <h3>为什么要有它</h3>
 * <p>门户的分组视图与"最近"视图都只要各域前几条；但**真正的跨域全局分页**做不到在查询时
 * 把三域合成一条有序流——要么内存归并（受数据量上限约束），要么建索引（用户选的就是索引表）。
 * 本服务就是那条索引：**同步**（按用户重建）与**读取**（按时间倒序分页）。</p>
 *
 * <h3>它不判可见性</h3>
 * <p>索引行来自各域 {@code MyAssetPort} 的返回——"我的"由各域自己过滤。本服务只搬运与排序，
 * 绝不自己查业务域的表。</p>
 *
 * @author ai-gov
 */
public interface IAigAssetIndexService {

    /**
     * 按用户重建索引（先删该用户各域旧行，再分页拉取各域全部资产写入）。
     *
     * <p>由门户"刷新我的资产"触发；将来做定时清扫时调用同一个方法即可。
     * 同一事务内完成：读者要么看到旧的一份，要么看到新的一份。</p>
     *
     * @param userId 用户ID
     * @return 本次写入的索引行数
     */
    int rebuildForUser(long userId);

    /**
     * 读索引分页（跨域全局分页；时间倒序）。
     *
     * <p>索引还没同步过时返回空页——调用方据此提示"先刷新一次"。
     * 它不是实时视图：各域新产出的资产要等下一次重建才会出现在这里。</p>
     *
     * @param userId    用户ID
     * @param domain    域编码（可空：全部域）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigPortalMyAssetVo> pageAssets(long userId, String domain, PageQuery pageQuery);

}
