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
 * 月度计划任务与应聘记录关联对象 hr_recruit_plan_application_rel（设计文档 §9.2 / §7.1.6）。
 *
 * <p><b>P2 范围说明</b>：{@code hr_recruit_application} 实体在 P3 才存在，因此本阶段
 * <b>只提供实体与 Mapper 占位</b>，不实现任何与应聘记录的联动逻辑（SPEC-P2 §0）。
 * 表结构语义保持：一个到岗结果只能计入一条当前有效的月度任务，避免跨月重复计算，
 * 由 {@code effective_end}（NULL 表示当前有效）与 {@code credited_flag} 共同表达。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐字一致；主键列为 {@code rel_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_plan_application_rel")
public class RecruitPlanApplicationRel extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID（主键）
     */
    @TableId(value = "rel_id")
    private Long relId;

    /**
     * 月度计划任务ID
     */
    private Long planItemId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 关联类型（plan计入/arrival到岗/source来源等稳定编码）
     */
    private String relationType;

    /**
     * 关联生效开始时间
     */
    private LocalDateTime effectiveStart;

    /**
     * 关联生效结束时间（NULL 表示当前有效）
     */
    private LocalDateTime effectiveEnd;

    /**
     * 是否已计入到岗统计（0否 1是）
     */
    private String creditedFlag;

    /**
     * 计入到岗统计时间
     */
    private LocalDateTime creditedTime;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

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
