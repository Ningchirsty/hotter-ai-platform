package org.dromara.hrtalent.service.recruitment;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitSensitiveAuditQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitSensitiveAuditVo;

/**
 * 敏感操作审计查询服务（SPEC-P3 §2.5 / §3.6，设计文档 §15.2）。
 *
 * <p><b>审计表是追加型</b>：本服务只提供查询与导出，<b>不提供</b>修改、删除接口；
 * 写入统一由 {@code support/SensitiveAuditRecorder} 完成。</p>
 *
 * @author hr-talent
 */
public interface ISensitiveAuditService {

    /**
     * 分页查询敏感操作审计。
     *
     * @param bo        查询条件（事件类型/业务类型/操作人/事件时间区间等）
     * @param pageQuery 分页参数
     * @return 审计分页结果
     */
    PageResult<RecruitSensitiveAuditVo> queryPage(RecruitSensitiveAuditQueryBo bo, PageQuery pageQuery);

    /**
     * 按当前筛选条件导出审计记录（限制最大条数），并<b>为本次导出动作本身写一条审计</b>。
     *
     * @param bo       查询条件
     * @param response HTTP 响应
     */
    void export(RecruitSensitiveAuditQueryBo bo, HttpServletResponse response);

}
