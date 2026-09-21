package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 招聘岗位执行项对象 hr_recruit_job。
 * <p>岗位是招聘主线的执行聚合根：由招聘需求或月度计划任务派生，向下承接应聘记录。
 * 一个计划任务对应零到多个岗位（计划任务 N ── 1 岗位），字段严格对齐
 * {@code script/sql/hr_recruit.sql} 的建表语句。</p>
 *
 * <p><b>并发控制</b>：{@link #version} 为乐观锁字段（设计文档 §9.6），更新必须携带版本号。</p>
 *
 * <p><b>面试官说明</b>：{@link #firstInterviewerId} / {@link #secondInterviewerId} 只是岗位上的
 * 「计划默认值」，供安排面试时带出；每轮面试的实际面试官归属 {@code hr_recruit_interviewer}
 * （按 {@code interview_id} 关联，属后续阶段实现），本阶段不写该表。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_job")
public class RecruitJob extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位执行项ID（主键）
     */
    @TableId(value = "job_id")
    private Long jobId;

    /**
     * 岗位编号（业务编号，唯一，由 RecruitBusinessNoGenerator 生成）
     */
    private String jobNo;

    /**
     * 来源招聘需求ID
     */
    private Long demandId;

    /**
     * 关联月度计划任务ID（计划任务 N ── 1 岗位）
     */
    private Long planItemId;

    /**
     * 岗位名称（建议取自招聘岗位标准库 hr_recruit_standard，本阶段只存字符串）
     */
    private String jobName;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照（历史报表用）
     */
    private String companyName;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 用工部门名称快照
     */
    private String useDeptName;

    /**
     * 岗位职级（字典编码）
     */
    private String jobLevel;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 岗位职责
     */
    private String responsibility;

    /**
     * 任职要求
     */
    private String qualification;

    /**
     * 薪资低值（低值不得大于高值）
     */
    private BigDecimal salaryMin;

    /**
     * 薪资高值
     */
    private BigDecimal salaryMax;

    /**
     * 薪资周期（month/year/day 等稳定编码，有薪资时必须明确）
     */
    private String salaryPeriod;

    /**
     * 招聘形式（internal/social/campus/headhunter/referral/other，字典 recruit_mode）
     */
    private String recruitMode;

    /**
     * 紧急程度（normal/urgent/very_urgent，字典 recruit_urgency）
     */
    private String urgency;

    /**
     * 是否需要猎头（0否 1是）
     */
    private String headhunterFlag;

    /**
     * 协助人用户ID，多个以英文逗号分隔
     */
    private String assistantIds;

    /**
     * 一面面试官用户ID（岗位计划默认值，实际参与以 hr_recruit_interviewer 为准）
     */
    private Long firstInterviewerId;

    /**
     * 二面面试官用户ID（岗位计划默认值，实际参与以 hr_recruit_interviewer 为准）
     */
    private Long secondInterviewerId;

    /**
     * 招聘期限标准天数（来自招聘期限标准）
     */
    private Integer standardDays;

    /**
     * 预计到岗日期
     */
    private LocalDate expectArrivalDate;

    /**
     * 岗位招聘人数（非负整数）
     */
    private Integer recruitCount;

    /**
     * 岗位负责（招聘负责人）用户ID
     */
    private Long ownerId;

    /**
     * 发布日期
     */
    private LocalDate publishDate;

    /**
     * 关闭日期
     */
    private LocalDate closeDate;

    /**
     * 岗位状态（draft/open/paused/closed，见 JobStatusEnum）
     */
    private String status;

    /**
     * 乐观锁版本号（设计文档 §9.6）
     */
    @Version
    private Integer version;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
