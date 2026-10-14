package org.dromara.aigov.tool.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.tool.domain.AigToolCall;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 工具调用账本 Mapper。
 *
 * @author ai-gov
 */
@Mapper
public interface AigToolCallMapper extends BaseMapperPlus<AigToolCall, AigToolCall> {
}
