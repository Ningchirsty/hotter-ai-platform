package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;
import org.dromara.aigov.studio.domain.vo.AigStudioChildAgentDraftVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 子 Agent 草案 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioChildAgentDraftMapper
    extends BaseMapperPlus<AigStudioChildAgentDraft, AigStudioChildAgentDraftVo> {
}
