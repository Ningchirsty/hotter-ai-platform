package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.vo.AigAgentVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Agent 定义 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigAgentMapper extends BaseMapperPlus<AigAgent, AigAgentVo> {
}
