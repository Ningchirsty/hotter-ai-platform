package org.dromara.content.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.CpProduct;
import org.dromara.content.domain.vo.CpProductVo;

/**
 * 轻量产品/SKU Mapper 接口
 *
 * @author content
 */
@Mapper
public interface CpProductMapper extends BaseMapperPlus<CpProduct, CpProductVo> {

}
