package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigCapabilityBo;
import org.dromara.aigov.domain.vo.AigCapabilityVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * AI 能力目录服务。
 *
 * @author ai-gov
 */
public interface IAigCapabilityService {

    /**
     * 分页查询能力目录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigCapabilityVo> queryPage(AigCapabilityBo bo, PageQuery pageQuery);

    /**
     * 能力详情。
     *
     * @param capabilityId 能力ID
     * @return 能力详情
     */
    AigCapabilityVo getDetail(Long capabilityId);

    /**
     * 新增能力（能力编码唯一）。
     *
     * @param bo 能力参数
     * @return 新能力ID
     */
    Long create(AigCapabilityBo bo);

    /**
     * 修改能力。
     *
     * @param bo 能力参数
     */
    void update(AigCapabilityBo bo);

    /**
     * 删除能力（逻辑删除）。
     *
     * @param capabilityId 能力ID
     */
    void remove(Long capabilityId);

}
