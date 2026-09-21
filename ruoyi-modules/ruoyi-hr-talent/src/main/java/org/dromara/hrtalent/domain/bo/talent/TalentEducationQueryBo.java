package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 教育经历查询业务对象（SPEC-P4 §2.2 B 线）。
 * <p>只承载列表检索条件，不承载任何可写字段；分页与排序由 {@code PageQuery} 提供。
 * 人才主档ID不接受前端传入，一律取路径变量。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentEducationQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 学校名称（模糊匹配）
     */
    private String schoolName;

    /**
     * 专业（模糊匹配）
     */
    private String major;

    /**
     * 学历（字典编码，精确匹配）
     */
    private String education;

    /**
     * 学位（字典编码，精确匹配）
     */
    private String degree;

    /**
     * 是否全日制（0否 1是，精确匹配）
     */
    private String fullTimeFlag;

    /**
     * 来源类型（manual/import/resume/parse，精确匹配）
     */
    private String sourceType;

    /**
     * 入学日期起（含）
     */
    private LocalDate startDateBegin;

    /**
     * 入学日期止（含）
     */
    private LocalDate startDateEnd;

}
