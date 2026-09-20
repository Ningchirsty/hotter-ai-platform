package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigCapabilityModelBo;
import org.dromara.aigov.domain.vo.AigCapabilityModelVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/**
 * AI 能力-模型绑定服务。
 *
 * @author ai-gov
 */
public interface IAigModelBindingService {

    /**
     * 分页查询绑定关系。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigCapabilityModelVo> queryPage(AigCapabilityModelBo bo, PageQuery pageQuery);

    /**
     * 查询某能力的启用绑定列表（按用途与优先级排序）。
     *
     * @param capabilityCode 能力编码
     * @return 绑定列表
     */
    List<AigCapabilityModelVo> listByCapability(String capabilityCode);

    /**
     * 新增绑定（能力 × 模型唯一）。
     *
     * @param bo 绑定参数
     * @return 新绑定ID
     */
    Long create(AigCapabilityModelBo bo);

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param bindId 绑定ID
     */
    void remove(Long bindId);

}
