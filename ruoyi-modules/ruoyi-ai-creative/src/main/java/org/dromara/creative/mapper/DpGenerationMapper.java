package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpGeneration;
import org.dromara.creative.domain.vo.DpGenerationVo;

/**
 * 生成记录 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpGenerationMapper extends BaseMapperPlus<DpGeneration, DpGenerationVo> {
}
