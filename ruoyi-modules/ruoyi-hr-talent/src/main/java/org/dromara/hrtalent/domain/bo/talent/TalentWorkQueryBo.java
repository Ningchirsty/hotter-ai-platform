package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 工作经历查询业务对象（SPEC-P4 §2.2 B 线）。
 * <p>只承载列表检索条件，不承载任何可写字段；分页与排序由 {@code PageQuery} 提供。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentWorkQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公司名称（模糊匹配）
     */
    private String companyName;

    /**
     * 部门名称（模糊匹配）
     */
    private String departmentName;

    /**
     * 职位名称（模糊匹配）
     */
    private String positionName;

    /**
     * 所属行业（模糊匹配）
     */
    private String industry;

    /**
     * 是否当前在职（0否 1是，精确匹配）
     */
    private String currentFlag;

    /**
     * 来源类型（manual/import/resume/parse，精确匹配）
     */
    private String sourceType;

    /**
     * 入职日期起（含）
     */
    private LocalDate startDateBegin;

    /**
     * 入职日期止（含）
     */
    private LocalDate startDateEnd;

}
