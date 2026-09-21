package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘背调记录查询业务对象。
 * <p>只承载列表检索条件，不承载写字段，也<b>不承载任何敏感说明</b>；
 * 分页与排序由 {@link PageQuery} 提供。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitBackgroundQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录ID（精确匹配）
     */
    private Long applicationId;

    /**
     * 背调负责人用户ID（精确匹配）
     */
    private Long checkerId;

    /**
     * 背调结论（字典 recruit_background_result 编码，精确匹配）
     */
    private String result;

    /**
     * 背调状态（draft/checking/finished/cancelled，精确匹配）
     */
    private String status;

    /**
     * 是否已获得候选人授权（0否 1是，精确匹配）
     */
    private String authorizedFlag;

    /**
     * 未通过原因编码（字典编码，精确匹配）
     */
    private String failureReasonCode;

    /**
     * 背调完成时间起（含）
     */
    private LocalDateTime checkTimeBegin;

    /**
     * 背调完成时间止（含）
     */
    private LocalDateTime checkTimeEnd;

}
