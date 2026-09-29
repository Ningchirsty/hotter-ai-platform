package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpWorkspaceSchema;

/**
 * 工作台装配 Mapper（V0.2 B1，只读）。
 *
 * @author creative
 */
@Mapper
public interface DpWorkspaceSchemaMapper extends BaseMapperPlus<DpWorkspaceSchema, DpWorkspaceSchema> {
}
