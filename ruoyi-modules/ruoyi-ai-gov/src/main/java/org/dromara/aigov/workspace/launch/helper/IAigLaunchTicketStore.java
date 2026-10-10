package org.dromara.aigov.workspace.launch.helper;

import org.dromara.aigov.workspace.launch.domain.AigLaunchTicket;

import java.time.Duration;

/**
 * 启动票据的存放（主文档线增量 3）。
 *
 * <p><b>为什么是接口</b>：票据的过期与一次性语义是安全属性（过期必须拒绝、用过必须作废），
 * 而它们需要被逐条单测。把存储抽象出来，服务层就能用内存实现来验证
 * "过期拒绝""票不能跨用户使用""用过即作废"，而不必依赖真实 Redis。</p>
 *
 * @author ai-gov
 */
public interface IAigLaunchTicketStore {

    /**
     * 保存票据。
     *
     * @param ticket 票据
     * @param ttl    存活时间
     */
    void save(AigLaunchTicket ticket, Duration ttl);

    /**
     * 读取票据。
     *
     * @param ticketId 票据ID
     * @return 票据；不存在返回 null
     */
    AigLaunchTicket load(String ticketId);

    /**
     * 作废票据（commit 成功后调用：票据是**一次性**的）。
     *
     * @param ticketId 票据ID
     */
    void consume(String ticketId);

}
