package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlTalentAttachment;
import org.dromara.talent.domain.vo.TlTalentAttachmentVo;

/**
 * 人才附件 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlTalentAttachmentMapper extends BaseMapperPlus<TlTalentAttachment, TlTalentAttachmentVo> {

}
