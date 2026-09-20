package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 调用逐次审计 Mapper 接口
 * <p>追加型审计表，无逻辑删除；本接口<b>不提供</b>物理删除相关业务方法。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigInvocationAuditMapper extends BaseMapperPlus<AigInvocationAudit, AigInvocationAuditVo> {

}
