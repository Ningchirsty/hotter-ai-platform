package org.dromara.aigov.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.aigov.agent.domain.vo.AigEvaluationCaseVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 黄金用例 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigEvaluationCaseMapper
    extends BaseMapperPlus<AigEvaluationCase, AigEvaluationCaseVo> {
}
