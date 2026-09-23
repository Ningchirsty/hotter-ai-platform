package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpStoryboardScreen;
import org.dromara.creative.domain.vo.DpStoryboardScreenVo;

/**
 * 分镜单屏 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpStoryboardScreenMapper extends BaseMapperPlus<DpStoryboardScreen, DpStoryboardScreenVo> {
}
