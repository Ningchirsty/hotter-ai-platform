package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlParseTask;
import org.dromara.talent.domain.vo.TlParseTaskVo;

/**
 * 简历解析任务 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlParseTaskMapper extends BaseMapperPlus<TlParseTask, TlParseTaskVo> {

}
