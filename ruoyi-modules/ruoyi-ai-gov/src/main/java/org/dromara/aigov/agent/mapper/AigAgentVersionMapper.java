package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.vo.AigAgentVersionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Agent 版本 Mapper。
 *
 * <p>发布状态推进须用「按当前状态做条件更新」，不要用 {@code updateById} 整行覆盖。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigAgentVersionMapper extends BaseMapperPlus<AigAgentVersion, AigAgentVersionVo> {
}
