package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.vo.AigRoutePolicyVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 路由策略 Mapper 接口
 *
 * @author ai-gov
 */
@Mapper
public interface AigRoutePolicyMapper extends BaseMapperPlus<AigRoutePolicy, AigRoutePolicyVo> {

}
