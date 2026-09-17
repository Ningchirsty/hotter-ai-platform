package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlParseField;
import org.dromara.talent.domain.vo.TlParseFieldVo;

/**
 * 解析字段复核 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlParseFieldMapper extends BaseMapperPlus<TlParseField, TlParseFieldVo> {

}
