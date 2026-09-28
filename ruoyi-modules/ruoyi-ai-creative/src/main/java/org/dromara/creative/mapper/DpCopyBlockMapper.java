package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpCopyBlock;
import org.dromara.creative.domain.vo.DpCopyBlockVo;

/**
 * 文案与要点块 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpCopyBlockMapper extends BaseMapperPlus<DpCopyBlock, DpCopyBlockVo> {
}
