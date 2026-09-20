package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpInteractionCard;
import org.dromara.content.domain.vo.CpInteractionCardVo;

/**
 * 互动确认卡 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpInteractionCardMapper extends BaseMapperPlus<CpInteractionCard, CpInteractionCardVo> {

}
