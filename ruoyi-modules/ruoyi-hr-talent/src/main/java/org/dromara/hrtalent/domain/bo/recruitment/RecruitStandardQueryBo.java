package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招聘期限标准查询对象。
 *
 * @author hr-talent
 */
@Data
public class RecruitStandardQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位名称（模糊匹配）
     */
    private String jobName;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 状态（active/inactive）
     */
    private String status;

    /**
     * 是否只看集团通用（companyDeptId 为空的条目）
     */
    private Boolean groupOnly;

}
