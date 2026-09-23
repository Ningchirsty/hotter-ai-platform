package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitStandard;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘期限标准视图对象 hr_recruit_standard。
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitStandard.class)
public class RecruitStandardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标准ID
     */
    private Long standardId;

    /**
     * 适用岗位名称
     */
    private String jobName;

    /**
     * 公司（平台部门）ID；为空表示集团通用
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 是否集团通用（companyDeptId 为空即通用），便于列表直接标注
     */
    private Boolean groupWide;

    /**
     * 招聘期限标准天数
     */
    private Integer standardDays;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expiryDate;

    /**
     * 状态（active/inactive）
     */
    private String status;

    /**
     * 状态标签
     */
    private String statusLabel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建者用户ID
     */
    private Long createBy;

    /**
     * 创建者名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
