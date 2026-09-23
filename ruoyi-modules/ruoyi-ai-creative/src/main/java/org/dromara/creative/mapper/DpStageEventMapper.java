package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpStageEvent;
import org.dromara.creative.domain.vo.DpStageEventVo;

/**
 * 阶段事件 Mapper（只追加）。
 *
 * @author creative
 */
@Mapper
public interface DpStageEventMapper extends BaseMapperPlus<DpStageEvent, DpStageEventVo> {
}
