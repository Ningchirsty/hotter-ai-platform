package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigSkill;
import org.dromara.aigov.agent.domain.vo.AigSkillVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Skill 定义 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigSkillMapper extends BaseMapperPlus<AigSkill, AigSkillVo> {
}
