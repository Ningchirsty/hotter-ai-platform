package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpOutputSpec;

/**
 * 输出规格 Mapper（V0.2 B1，只读）。
 *
 * @author creative
 */
@Mapper
public interface DpOutputSpecMapper extends BaseMapperPlus<DpOutputSpec, DpOutputSpec> {
}
