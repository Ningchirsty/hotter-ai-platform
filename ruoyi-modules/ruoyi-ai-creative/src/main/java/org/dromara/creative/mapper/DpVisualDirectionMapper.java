package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpVisualDirection;
import org.dromara.creative.domain.vo.DpVisualDirectionVo;

/**
 * 视觉方向 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpVisualDirectionMapper extends BaseMapperPlus<DpVisualDirection, DpVisualDirectionVo> {
}
