package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.vo.AigModelGovernanceVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 模型治理扩展 Mapper 接口
 *
 * @author ai-gov
 */
@Mapper
public interface AigModelGovernanceMapper extends BaseMapperPlus<AigModelGovernance, AigModelGovernanceVo> {

}
