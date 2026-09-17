package org.dromara.talent.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.talent.domain.bo.TlDuplicateConfirmBo;
import org.dromara.talent.domain.vo.TlTalentDuplicateVo;

/**
 * 重复人才预警服务（只预警不自动合并）。
 *
 * @author talent
 */
public interface ITalentDuplicateService {

    /**
     * 分页查询重复预警（服务端限定来源人才在可见区域内）。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<TlTalentDuplicateVo> queryPage(TlTalentDuplicateVo query, PageQuery pageQuery);

    /**
     * 人工确认重复结论（SAME / DIFFERENT）。
     *
     * @param bo 确认参数
     */
    void confirm(TlDuplicateConfirmBo bo);

}
