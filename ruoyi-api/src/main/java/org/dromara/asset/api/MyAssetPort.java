package org.dromara.asset.api;

import org.dromara.asset.api.domain.MyAssetDTO;

import java.util.List;

/**
 * 「我的资产」只读端口（各业务域在 {@code ruoyi-api} 里实现的对外契约）。
 *
 * <h3>为什么是端口，而不是聚合层直连各域的表</h3>
 * <p>聚合层（员工门户）读的是"我的资产"。而"我的"在三个域里根本不是一回事：
 * 图片/视频看 {@code user_id}，内容附件看所属任务的负责人。直连那些表等于把各自的归属规则
 * <b>复制</b>一份到聚合层，两份规则迟早不一致——而不一致的方向恰好是<b>放行</b>（越权）。</p>
 *
 * <p>所以每个域实现本端口、在自己的模块里做归属过滤；聚合层只调端口、只做展示分组。
 * 一个域没上线（没有对应 Bean）时，聚合结果就少一组，而不是启动失败或返回别人的数据。</p>
 *
 * @author ai-gov
 */
public interface MyAssetPort {

    /**
     * 该提供方负责的域编码（IMAGE / VIDEO / CONTENT；同一域只应有一个提供方）。
     *
     * @return 域编码
     */
    String domain();

    /**
     * 取某个用户"自己的"资产（归属过滤由实现方完成）。
     *
     * @param userId 用户ID（由登录态取得，调用方不得传入任意值）
     * @param limit  最多返回几条（实现方需自行设上限）
     * @return 资产列表（按时间倒序）；无数据返回空列表
     */
    List<MyAssetDTO> listMyAssets(long userId, int limit);

}
