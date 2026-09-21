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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘需求来源对象 hr_recruit_demand（设计文档 §9.2 / §8.2）。
 *
 * <p><b>聚合根</b>：招聘需求是招聘主线的入口聚合根，字段名、类型与可空性
 * 严格对齐 {@code script/sql/hr_recruit.sql} 的建表语句。</p>
 *
 * <p><b>乐观锁</b>：含 {@code version} 字段（设计文档 §9.6），更新必须携带版本号，
 * 冲突时由服务层转换为中文提示。</p>
 *
 * <p><b>派生字段</b>：待招聘人数 {@code max(demand_count - hired_count, 0)}
 * <b>不落库</b>，由出参 VO 计算（SPEC §4.1）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_demand")
public class RecruitDemand extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求ID（主键，对外只展示 demand_no）
     */
    @TableId(value = "demand_id")
    private Long demandId;

    /**
     * 需求编号（业务编号，唯一，由 RecruitBusinessNoGenerator 生成）
     */
    private String demandNo;

    /**
     * 需求标题
     */
    private String demandTitle;

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
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 需求申请日期
     */
    private LocalDate applyDate;

    /**
     * 期望到岗日期（不得早于申请日期）
     */
    private LocalDate expectArrivalDate;

    /**
     * 需求人数（非负整数，业务规则要求大于 0）
     */
    private Integer demandCount;

    /**
     * 已到岗人数（非负整数）
     */
    private Integer hiredCount;

    /**
     * 紧急程度（normal/urgent/very_urgent，字典 recruit_urgency）
     */
    private String urgency;

    /**
     * 招聘形式（internal/social/campus/headhunter/referral/other，字典 recruit_mode）
     */
    private String recruitMode;

    /**
     * 需求岗位名称
     */
    private String jobName;

    /**
     * 岗位职级（字典编码）
     */
    private String jobLevel;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 需求原因说明
     */
    private String demandReason;

    /**
     * 需求状态（draft/submitted/recruiting/paused/completed/closed，字典 recruit_demand_status）
     */
    private String status;

    /**
     * 提交人用户ID
     */
    private Long submittedBy;

    /**
     * 提交时间
     */
    private LocalDateTime submittedTime;

    /**
     * 确认人用户ID（进入招聘中时写入）
     */
    private Long confirmedBy;

    /**
     * 确认时间（进入招聘中时写入）
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
     * 乐观锁版本号（设计 §9.6）
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
