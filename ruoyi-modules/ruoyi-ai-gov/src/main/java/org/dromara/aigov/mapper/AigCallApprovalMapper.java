package org.dromara.aigov.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.aigov.domain.AigCallApproval;
import org.dromara.aigov.domain.vo.AigCallApprovalVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 调用授权审批 Mapper（C3）。
 *
 * <p>没有自定义 SQL：三类查询都用条件构造器表达，且都被
 * {@code idx_aig_call_approval_grant (requester_id, capability_code, data_level, status, valid_until)}
 * 覆盖——最热的那个是调用入口上的「这个人对这个能力这个等级有没有未过期授权」。</p>
 *
 * @author ai-gov
 */
@Mapper
public interface AigCallApprovalMapper extends BaseMapperPlus<AigCallApproval, AigCallApprovalVo> {
}
