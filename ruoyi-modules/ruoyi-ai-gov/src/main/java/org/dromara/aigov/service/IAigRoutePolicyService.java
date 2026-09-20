package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigRoutePolicyBo;
import org.dromara.aigov.domain.vo.AigRoutePolicyVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * AI 路由策略服务。
 * <p>按「能力 × 数据等级」配置；<b>无策略即拒绝</b>，因此新增能力后必须补策略。</p>
 *
 * @author ai-gov
 */
public interface IAigRoutePolicyService {

    /**
     * 分页查询路由策略。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigRoutePolicyVo> queryPage(AigRoutePolicyBo bo, PageQuery pageQuery);

    /**
     * 策略详情。
     *
     * @param policyId 策略ID
     * @return 策略详情
     */
    AigRoutePolicyVo getDetail(Long policyId);

    /**
     * 新增策略（能力 × 数据等级唯一）。
     *
     * @param bo 策略参数
     * @return 新策略ID
     */
    Long create(AigRoutePolicyBo bo);

    /**
     * 修改策略。
     *
     * @param bo 策略参数
     */
    void update(AigRoutePolicyBo bo);

    /**
     * 删除策略（逻辑删除）。
     *
     * @param policyId 策略ID
     */
    void remove(Long policyId);

}
