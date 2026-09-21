package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公司月度招聘计划表头对象 hr_recruit_plan（设计文档 §9.2 / §8.2.1）。
 *
 * <p>一个公司（平台部门）在一个自然月<b>只有一张</b>表头，由数据库唯一索引
 * {@code uk_hr_recruit_plan_company_month(company_dept_id, plan_month)} 保证；
 * 表头只承载汇总人数与状态，具体招聘任务在 {@code hr_recruit_plan_item} 中逐条新增。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐字一致；主键列为 {@code plan_id}。
 * 该表没有 {@code version} 列，故不使用 {@code @Version} 乐观锁。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_plan")
public class RecruitPlan extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID（主键）
     */
    @TableId(value = "plan_id")
    private Long planId;

    /**
     * 计划编号（业务编号，唯一）
     */
    private String planNo;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 计划月份（yyyy-MM）
     */
    private String planMonth;

    /**
     * 计划状态（draft/executing/closed，字典 recruit_plan_status）
     */
    private String status;

    /**
     * 是否已生成计划任务（0否 1是）
     */
    private String generatedFlag;

    /**
     * 生成时间
     */
    private LocalDateTime generatedTime;

    /**
     * 确认人用户ID
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;

    /**
     * 关闭人用户ID
     */
    private Long closedBy;

    /**
     * 关闭时间
     */
    private LocalDateTime closedTime;

    /**
     * 计划总人数（非负整数）
     */
    private Integer totalPlanQty;

    /**
     * 累计计入到岗人数（非负整数）
     */
    private Integer totalCreditedQty;

    /**
     * 累计剩余人数（非负整数）
     */
    private Integer totalRemainingQty;

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
