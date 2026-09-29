package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpDeliveryType;

/**
 * 交付类型 Mapper（V0.2 B1 场景配置层）。
 *
 * <p>配置表就是"配置本身"，读出来不需要二次加工，所以 VO 类型参数直接用实体——
 * 不额外造一层只做字段复制的 VO。查询一律走 {@code selectList(Wrapper)}。</p>
 *
 * @author creative
 */
@Mapper
public interface DpDeliveryTypeMapper extends BaseMapperPlus<DpDeliveryType, DpDeliveryType> {
}
