package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.vo.AigRouteScenarioBindingVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 场景强制绑定 Mapper 接口
 *
 * @author ai-gov
 */
@Mapper
public interface AigRouteScenarioBindingMapper extends BaseMapperPlus<AigRouteScenarioBinding, AigRouteScenarioBindingVo> {

}
