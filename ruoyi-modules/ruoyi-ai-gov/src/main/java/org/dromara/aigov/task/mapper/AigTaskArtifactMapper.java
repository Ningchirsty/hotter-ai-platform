package org.dromara.aigov.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.task.domain.AigTaskArtifact;
import org.dromara.aigov.task.domain.vo.AigTaskArtifactVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 任务制品账本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigTaskArtifactMapper extends BaseMapperPlus<AigTaskArtifact, AigTaskArtifactVo> {

}
