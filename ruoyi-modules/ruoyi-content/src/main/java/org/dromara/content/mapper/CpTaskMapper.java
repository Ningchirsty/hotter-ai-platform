package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpTask;
import org.dromara.content.domain.vo.CpTaskVo;

/**
 * 内容生产任务 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpTaskMapper extends BaseMapperPlus<CpTask, CpTaskVo> {

}
