package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;
import org.dromara.aigov.studio.domain.vo.AigStudioSkillDraftVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 技能草案 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioSkillDraftMapper
    extends BaseMapperPlus<AigStudioSkillDraft, AigStudioSkillDraftVo> {
}
