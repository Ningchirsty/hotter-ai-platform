package org.dromara.aigov.tool.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.tool.domain.AigToolDefinition;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 工具定义 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigToolDefinitionMapper
    extends BaseMapperPlus<AigToolDefinition, AigToolDefinition> {
}
