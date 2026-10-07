package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigEvaluationRun;
import org.dromara.aigov.agent.domain.vo.AigEvaluationRunVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 评测运行 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigEvaluationRunMapper extends BaseMapperPlus<AigEvaluationRun, AigEvaluationRunVo> {
}
