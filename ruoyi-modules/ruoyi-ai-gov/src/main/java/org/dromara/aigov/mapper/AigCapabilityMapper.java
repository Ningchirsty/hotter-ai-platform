package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.vo.AigCapabilityVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 业务能力模板 Mapper 接口
 *
 * @author ai-gov
 */
@Mapper
public interface AigCapabilityMapper extends BaseMapperPlus<AigCapability, AigCapabilityVo> {

}
