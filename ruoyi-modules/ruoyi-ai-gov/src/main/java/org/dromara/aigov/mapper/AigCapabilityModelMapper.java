package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.vo.AigCapabilityModelVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 能力与模型绑定 Mapper 接口
 *
 * @author ai-gov
 */
@Mapper
public interface AigCapabilityModelMapper extends BaseMapperPlus<AigCapabilityModel, AigCapabilityModelVo> {

}
