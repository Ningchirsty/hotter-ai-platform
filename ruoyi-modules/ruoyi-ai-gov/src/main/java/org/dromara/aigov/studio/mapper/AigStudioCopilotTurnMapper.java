package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioCopilotTurn;
import org.dromara.aigov.studio.domain.vo.AigStudioCopilotTurnVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 对话式 Copilot 轮次 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioCopilotTurnMapper
    extends BaseMapperPlus<AigStudioCopilotTurn, AigStudioCopilotTurnVo> {
}
