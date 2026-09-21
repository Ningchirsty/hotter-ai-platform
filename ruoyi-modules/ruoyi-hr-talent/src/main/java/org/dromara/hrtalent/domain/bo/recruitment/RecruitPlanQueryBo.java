package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公司月度招聘计划表头查询对象（SPEC-P2 §3.2）。
 *
 * @author hr-talent
 */
@Data
public class RecruitPlanQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划编号（模糊匹配）
     */
    private String planNo;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照（模糊匹配）
     */
    private String companyName;

    /**
     * 计划月份（yyyy-MM，精确匹配）
     */
    private String planMonth;

    /**
     * 计划状态（draft/executing/closed）
     */
    private String status;

}
