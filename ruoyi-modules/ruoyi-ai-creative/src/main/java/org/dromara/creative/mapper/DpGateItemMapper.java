package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpGateItem;

/**
 * 闸门项 Mapper（V0.2 B2，只读）。
 *
 * @author creative
 */
@Mapper
public interface DpGateItemMapper extends BaseMapperPlus<DpGateItem, DpGateItem> {
}
