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
 * 月度招聘计划结转记录对象 hr_recruit_plan_rollover（设计文档 §9.2 / §7.1.2 / §9.6）。
 *
 * <p><b>幂等唯一键</b>：{@code uk_hr_recruit_plan_rollover(source_item_id, target_month)}。
 * 结转执行时<b>先占位写入本条记录</b>，再创建目标任务；重复执行或并发执行时，
 * 占位插入会命中唯一索引从而判定为「已结转」并安全跳过，绝不重复生成下月任务。</p>
 *
 * <p>{@code batch_id} 为同一次结转执行的批次关联键（与
 * {@code hr_recruit_plan_item.carryover_batch_id} 对应），{@code batch_no} 为可读批次号。
 * P1 DDL 未提供独立批次表，故批次状态由本表明细按 {@code batch_no} 汇总得出。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐字一致；主键列为 {@code rollover_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_plan_rollover")
public class RecruitPlanRollover extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结转记录ID（主键）
     */
    @TableId(value = "rollover_id")
    private Long rolloverId;

    /**
     * 来源（上月）计划任务ID
     */
    private Long sourceItemId;

    /**
     * 目标（本月）计划任务ID
     */
    private Long targetItemId;

    /**
     * 来源月份（yyyy-MM）
     */
    private String sourceMonth;

    /**
     * 目标月份（yyyy-MM）
     */
    private String targetMonth;

    /**
     * 结转人数（非负整数）
     */
    private Integer carryoverQty;

    /**
     * 结转批次ID（一次结转执行一个批次）
     */
    private Long batchId;

    /**
     * 结转批次号（批次的可读编号，便于整体重试）
     */
    private String batchNo;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 执行结果（processing处理中/success成功/failed失败/skipped跳过等稳定编码）
     */
    private String result;

    /**
     * 失败原因（单条失败可安全重试）
     */
    private String failureReason;

    /**
     * 重试次数（非负整数）
     */
    private Integer retryCount;

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
