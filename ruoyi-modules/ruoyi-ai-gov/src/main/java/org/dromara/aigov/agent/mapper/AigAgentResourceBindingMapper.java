package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
import org.dromara.aigov.agent.domain.vo.AigAgentResourceBindingVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 工具/知识绑定 Mapper（附件 07 §5）。
 *
 * @author ai-gov
 */
@Mapper
public interface AigAgentResourceBindingMapper
    extends BaseMapperPlus<AigAgentResourceBinding, AigAgentResourceBindingVo> {
}
