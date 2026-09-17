package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlTalentContact;
import org.dromara.talent.domain.vo.TlTalentContactVo;

/**
 * 人才联系跟进 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlTalentContactMapper extends BaseMapperPlus<TlTalentContact, TlTalentContactVo> {

}
