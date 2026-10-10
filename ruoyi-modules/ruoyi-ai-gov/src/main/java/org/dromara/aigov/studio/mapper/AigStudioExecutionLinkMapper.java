package org.dromara.aigov.studio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.studio.domain.AigStudioExecutionLink;
import org.dromara.aigov.studio.domain.vo.AigStudioExecutionLinkVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 训练台测试证据 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigStudioExecutionLinkMapper
    extends BaseMapperPlus<AigStudioExecutionLink, AigStudioExecutionLinkVo> {

}
