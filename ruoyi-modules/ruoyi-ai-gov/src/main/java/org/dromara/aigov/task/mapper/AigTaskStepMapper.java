package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 任务步骤 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskStepMapper extends BaseMapperPlus<AigTaskStep, AigTaskStep> {
}
