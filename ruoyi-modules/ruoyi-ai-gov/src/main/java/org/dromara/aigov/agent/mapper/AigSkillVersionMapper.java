package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigSkillVersion;
import org.dromara.aigov.agent.domain.vo.AigSkillVersionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * Skill 版本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigSkillVersionMapper extends BaseMapperPlus<AigSkillVersion, AigSkillVersionVo> {
}
