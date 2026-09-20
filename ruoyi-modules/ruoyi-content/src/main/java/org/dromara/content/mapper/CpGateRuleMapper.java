package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.domain.vo.CpGateRuleVo;

/**
 * 闸门规则 Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpGateRuleMapper extends BaseMapperPlus<CpGateRule, CpGateRuleVo> {

}
