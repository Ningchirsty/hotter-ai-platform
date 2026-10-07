package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTaskResult;
import org.dromara.aigov.task.domain.vo.AigTaskResultVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 任务结果（候选资产）Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskResultMapper extends BaseMapperPlus<AigTaskResult, AigTaskResultVo> {

}
