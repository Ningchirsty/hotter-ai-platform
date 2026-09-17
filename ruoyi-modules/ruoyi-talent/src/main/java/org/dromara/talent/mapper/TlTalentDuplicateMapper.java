package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlTalentDuplicate;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;

/**
 * 重复人才预警 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlTalentDuplicateMapper extends BaseMapperPlus<TlTalentDuplicate, TlTalentDuplicateVo> {

}
