package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 项目经历查询业务对象（SPEC-P4 §2.2 B 线）。
 * <p>只承载列表检索条件，不承载任何可写字段；分页与排序由 {@code PageQuery} 提供。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentProjectQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称（模糊匹配）
     */
    private String projectName;

    /**
     * 项目角色（模糊匹配）
     */
    private String projectRole;

    /**
     * 关联工作经历ID（精确匹配）
     */
    private Long workId;

    /**
     * 来源类型（manual/import/resume/parse，精确匹配）
     */
    private String sourceType;

    /**
     * 项目开始日期起（含）
     */
    private LocalDate startDateBegin;

    /**
     * 项目开始日期止（含）
     */
    private LocalDate startDateEnd;

}
