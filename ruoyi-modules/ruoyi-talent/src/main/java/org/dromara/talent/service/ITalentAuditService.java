package org.dromara.talent.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.talent.domain.vo.TlSensitiveAuditVo;

/**
 * 敏感操作审计查询服务。
 *
 * @author talent
 */
public interface ITalentAuditService {

    /**
     * 审计分页查询。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<TlSensitiveAuditVo> queryPage(TlSensitiveAuditVo query, PageQuery pageQuery);

}
