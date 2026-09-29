package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpScenarioStep;

/**
 * 场景步骤 Mapper（V0.2 B1，只读）。
 *
 * @author creative
 */
@Mapper
public interface DpScenarioStepMapper extends BaseMapperPlus<DpScenarioStep, DpScenarioStep> {
}
