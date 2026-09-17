package org.dromara.talent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.talent.domain.TlSensitiveAudit;
import org.dromara.talent.domain.vo.TlSensitiveAuditVo;

/**
 * 人才库敏感操作审计 Mapper 接口
 *
 * @author talent
 */
@Mapper
public interface TlSensitiveAuditMapper extends BaseMapperPlus<TlSensitiveAudit, TlSensitiveAuditVo> {

}
