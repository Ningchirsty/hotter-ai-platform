package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.vo.AigAgentBindingVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Agent 版本绑定 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigAgentBindingMapper extends BaseMapperPlus<AigAgentBinding, AigAgentBindingVo> {
}
