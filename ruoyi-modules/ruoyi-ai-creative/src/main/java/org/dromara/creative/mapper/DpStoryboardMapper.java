package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpStoryboard;
import org.dromara.creative.domain.vo.DpStoryboardVo;

/**
 * 分镜 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpStoryboardMapper extends BaseMapperPlus<DpStoryboard, DpStoryboardVo> {
}
