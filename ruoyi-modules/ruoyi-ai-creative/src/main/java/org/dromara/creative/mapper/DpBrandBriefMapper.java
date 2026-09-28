package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpBrandBrief;
import org.dromara.creative.domain.vo.DpBrandBriefVo;

/**
 * 品牌 Brief Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpBrandBriefMapper extends BaseMapperPlus<DpBrandBrief, DpBrandBriefVo> {
}
