package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigAuditQueryBo;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/**
 * AI 调用审计查询服务（只读）。
 *
 * @author ai-gov
 */
public interface IAigAuditService {

    /**
     * 分页查询逐次调用审计。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<AigInvocationAuditVo> queryPage(AigAuditQueryBo bo, PageQuery pageQuery);

}
