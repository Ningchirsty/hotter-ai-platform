package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpAsyncJob;
import org.dromara.content.domain.vo.CpAsyncJobVo;

/**
 * 异步作业 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpAsyncJobMapper extends BaseMapperPlus<CpAsyncJob, CpAsyncJobVo> {

}
