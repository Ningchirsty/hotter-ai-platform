package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpVisualDna;
import org.dromara.creative.domain.vo.DpVisualDnaVo;

/**
 * Visual DNA Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpVisualDnaMapper extends BaseMapperPlus<DpVisualDna, DpVisualDnaVo> {
}
