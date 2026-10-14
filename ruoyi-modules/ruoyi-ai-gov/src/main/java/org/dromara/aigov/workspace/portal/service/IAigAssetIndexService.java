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
     * 增量同步：只补各域**新增**的资产（不做删除/更新）。
     *
     * <p><b>水位线口径</b>：以索引里该域已有的最大 {@code asset_id} 为水位线，只取比它大的资产。
     * 各域端口都按资产 ID 倒序返回（内容/视频/图像三个实现的 SQL 都是 {@code ORDER BY id DESC}），
     * 因此"翻到一条不大于水位线的资产"就意味着后面只会更旧，可以停。
     * 该域<b>还没有水位线</b>（没同步过）时退回该域全量，绝不拿水位线猜。</p>
     *
     * <p><b>它不做两件事，必须知道</b>：①<b>不删</b>——各域删掉的资产仍留在索引里，
     * 要回收得走 {@link #rebuildForUser}（门户"刷新索引"）；②<b>不更新</b>——源头改名/换大小
     * 不会被发现。所以定时增量 + 定期全量，才是完整口径；只跑增量会让旧名字一直显示。</p>
     *
     * <p><b>为什么不用变更订阅</b>：三个业务域都没有对外发变更事件，引入订阅要动三个模块的写路径
     * （属独立变更）。水位线只需读，代价是一部分"更新"要靠全量兜底——这个取舍写在上面。</p>
     *
     * @param userId 用户ID
     * @return 本次新增写入的索引行数
     */
    int syncNewForUser(long userId);

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
