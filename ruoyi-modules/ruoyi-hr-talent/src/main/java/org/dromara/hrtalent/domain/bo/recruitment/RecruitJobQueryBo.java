package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 招聘岗位执行项查询业务对象。
 * <p>只承载列表检索条件，不承载写字段；分页与排序由 {@link PageQuery} 提供。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitJobQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编号（模糊匹配）
     */
    private String jobNo;

    /**
     * 岗位名称（模糊匹配）
     */
    private String jobName;

    /**
     * 来源招聘需求ID
     */
    private Long demandId;

    /**
     * 关联月度计划任务ID
     */
    private Long planItemId;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 招聘负责人用户ID
     */
    private Long ownerId;

    /**
     * 岗位状态（draft/open/paused/closed，精确匹配）
     */
    private String status;

    /**
     * 招聘形式（字典 recruit_mode 编码，精确匹配）
     */
    private String recruitMode;

    /**
     * 紧急程度（字典 recruit_urgency 编码，精确匹配）
     */
    private String urgency;

    /**
     * 岗位职级（字典编码，精确匹配）
     */
    private String jobLevel;

    /**
     * 工作城市（模糊匹配）
     */
    private String workCity;

    /**
     * 是否需要猎头（0否 1是，精确匹配）
     */
    private String headhunterFlag;

    /**
     * 发布日期起（含）
     */
    private LocalDate publishDateBegin;

    /**
     * 发布日期止（含）
     */
    private LocalDate publishDateEnd;

}
