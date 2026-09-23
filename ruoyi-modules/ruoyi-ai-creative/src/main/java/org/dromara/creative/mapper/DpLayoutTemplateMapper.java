package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpLayoutTemplate;
import org.dromara.creative.domain.vo.DpLayoutTemplateVo;

/**
 * 视觉模板 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpLayoutTemplateMapper extends BaseMapperPlus<DpLayoutTemplate, DpLayoutTemplateVo> {
}
