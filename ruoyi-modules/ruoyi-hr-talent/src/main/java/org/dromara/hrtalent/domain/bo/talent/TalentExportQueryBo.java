package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才导出任务查询业务对象（SPEC-P4 §2.6 F 线，{@code GET /talent/exports}）。
 *
 * @author hr-talent
 */
@Data
public class TalentExportQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出类型（normal/sensitive，精确匹配）
     */
    private String exportType;

    /**
     * 任务状态（pending/running/success/failed/expired，精确匹配）
     */
    private String status;

    /**
     * 导出人用户ID（精确匹配）
     */
    private Long exportedBy;

    /**
     * 创建时间起（含，{@code yyyy-MM-dd}）
     */
    private LocalDate createDateBegin;

    /**
     * 创建时间止（含，{@code yyyy-MM-dd}）
     */
    private LocalDate createDateEnd;

}
