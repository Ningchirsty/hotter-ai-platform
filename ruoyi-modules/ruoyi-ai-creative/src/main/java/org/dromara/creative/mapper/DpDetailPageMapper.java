package org.dromara.creative.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.creative.domain.DpDetailPage;
import org.dromara.creative.domain.vo.DpDetailPageVo;

/**
 * 详情页 Mapper。
 *
 * @author creative
 */
@Mapper
public interface DpDetailPageMapper extends BaseMapperPlus<DpDetailPage, DpDetailPageVo> {
}
