package org.dromara.aigov.tool.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.tool.domain.AigToolDefinitionVersion;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 工具版本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigToolDefinitionVersionMapper
    extends BaseMapperPlus<AigToolDefinitionVersion, AigToolDefinitionVersion> {
}
