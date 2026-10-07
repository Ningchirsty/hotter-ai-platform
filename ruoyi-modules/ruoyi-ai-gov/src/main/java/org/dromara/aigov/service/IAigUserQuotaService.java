package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigUserQuotaBo;
import org.dromara.aigov.domain.vo.AigUserQuotaUsageVo;
import org.dromara.aigov.domain.vo.AigUserQuotaVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.domain.PageResult;

/**
 * 调用人均配额（C3：用量配额按人）。
 *
 * <p><b>它补的是哪一个洞</b>：治理台此前只有按模型声明的<b>单次</b>成本上限
 * （{@code aig_model_governance.cost_limit_amount}）。一个人一天调一千次、每次都在单次上限内，
 * 没有任何地方会拦——因为从来没有「累计」这一层。</p>
 *
 * <p><b>计量单位是「调用次数」而不是钱</b>（刻意）：调用审计里的 {@code cost}
 * 「为空表示未知而非免费」，经 snail-ai 的链路更是恒为 null。用一列经常是「未知」的数字做配额，
 * 会算出一本对不上的账。次数是每条调用都有的、可核对的事实。</p>
 *
 * <p><b>语义</b>：无配额行 = 不限；状态停用 = 不参与判定；日/月上限可各为空（为空的那条不限）；
 * 计数统计该调用人在自然日/自然月内的<b>全部调用审计行</b>（含失败——调用发生过就算，
 * 否则反复失败重试就能绕开配额）；无调用人（调度/系统发起）时不判——没有「人」可归属。</p>
 *
 * @author ai-gov
 */
public interface IAigUserQuotaService {

    /**
     * 分页查询配额配置。
     *
     * @param bo        查询条件（用户ID / 账号 / 状态）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigUserQuotaVo> queryPage(AigUserQuotaBo bo, PageQuery pageQuery);

    /**
     * 查某人的当前用量（已用 / 上限 / 周期起点）。
     *
     * <p>只给上限不给用量，运维无法回答「他快用完了还是配错了」，用户被拦时也只能看到「超了」。</p>
     *
     * @param userId 用户ID
     * @return 用量视图
     */
    AigUserQuotaUsageVo usage(Long userId);

    /**
     * 保存配额（<b>一人一行</b>：按 userId upsert）。
     *
     * @param bo 配置
     * @return 配额ID
     */
    Long save(AigUserQuotaBo bo);

    /**
     * 删除配额（逻辑删除）。删除的含义是<b>回到「不限」</b>，不是「禁止调用」。
     *
     * @param quotaId 配额ID
     */
    void remove(Long quotaId);

    /**
     * 判定：这个调用人现在还能不能发起调用；超限则抛异常（带可读原因）。
     *
     * <p><b>由唯一的调用入口调用</b>（{@code AigInvokeServiceImpl#invoke}），且只在
     * 「确实要调用模型」之前——策略拒绝/转人工的分支不消耗额度，也不该被额度抢先拦截
     * （否则人会看到「配额超了」，而真正的原因是策略不允许）。</p>
     *
     * @param callerId 调用人用户ID（为空或非正数表示无调用人，不判）
     */
    void assertWithinQuota(Long callerId);

}
