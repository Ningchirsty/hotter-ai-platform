package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigRouteScenarioBindingBo;
import org.dromara.aigov.domain.vo.AigRouteScenarioBindingVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * 场景强制绑定服务：维护「场景 × 能力 → 指定供应商」的绑定。
 *
 * <p><b>语义边界（务必先读）</b>：本服务维护的绑定只被路由引擎用于
 * <b>收窄</b>候选范围——把不属于指定供应商的候选筛掉。它<b>不会</b>让任何
 * 被治理策略/数据等级/生命周期排除的模型变得可用。因此：
 * 绑定配置错误的最坏后果是「该场景没模型可用 → 按策略转人工或拒绝」，
 * 不会变成「数据被发给了不该发的地方」。</p>
 *
 * <p>也正因如此，本服务的增删改<b>不需要</b>与 {@code aig_route_policy} 同级的数据
 * 外发审批语义：它改不了外发禁令，只能改「在已允许的候选里先试谁」。</p>
 *
 * @author ai-gov
 */
public interface IAigRouteScenarioBindingService {

    /**
     * 分页查询场景强制绑定。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 绑定分页结果
     */
    PageResult<AigRouteScenarioBindingVo> queryPage(AigRouteScenarioBindingBo bo, PageQuery pageQuery);

    /**
     * 获取绑定详情。
     *
     * @param bindId 绑定ID
     * @return 绑定详情
     */
    AigRouteScenarioBindingVo getDetail(Long bindId);

    /**
     * 新增绑定。
     *
     * @param bo 绑定参数
     * @return 新增的绑定ID
     */
    Long create(AigRouteScenarioBindingBo bo);

    /**
     * 修改绑定。
     *
     * @param bo 绑定参数
     */
    void update(AigRouteScenarioBindingBo bo);

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param bindId 绑定ID
     */
    void remove(Long bindId);

}
