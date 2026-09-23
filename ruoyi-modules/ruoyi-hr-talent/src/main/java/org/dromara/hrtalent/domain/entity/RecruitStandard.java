package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 招聘期限标准对象 hr_recruit_standard（设计 §9.2）。
 *
 * <p><b>它回答的问题</b>：某个公司的某个岗位，「从启动招聘到人到岗」应当用多少天。
 * 岗位执行项 {@code hr_recruit_job.standard_days} 与月度计划任务
 * {@code hr_recruit_plan_item.standard_days} 都以此为口径来源，超期与否都拿它作基线。</p>
 *
 * <p><b>公司可空</b>：{@code companyDeptId} 为 {@code null} 表示<b>集团通用</b>
 * （所有公司都适用），具体公司的条目优先于通用条目——这样绝大多数岗位只需维护一条通用标准。</p>
 *
 * <p><b>生效期</b>：{@code effectiveDate}/{@code expiryDate} 让标准可以按月演进，
 * 而不是把历史天数就地改掉（改了就没法解释历史上为什么按 30 天算）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_standard")
public class RecruitStandard extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标准ID（主键）
     */
    @TableId(value = "standard_id")
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
     * 招聘期限标准天数（非负整数）
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
     * 状态（active 生效 / inactive 停用）
     */
    private String status;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
