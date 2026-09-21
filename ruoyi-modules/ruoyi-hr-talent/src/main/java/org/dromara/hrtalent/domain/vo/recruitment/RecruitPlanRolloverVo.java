package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitPlanRollover;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 月度招聘计划结转记录视图对象 hr_recruit_plan_rollover（SPEC-P2 §3.3）。
 *
 * <p>一行代表「一条来源任务对目标月份的一次结转」，唯一键为
 * {@code (source_item_id, target_month)}；同一 {@code batchNo} 的多行构成一个批次。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlanRollover.class)
public class RecruitPlanRolloverVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结转记录ID
     */
    private Long rolloverId;

    /**
     * 来源（上月）计划任务ID
     */
    private Long sourceItemId;

    /**
     * 来源任务编号
     */
    private String sourceItemNo;

    /**
     * 来源任务岗位名称
     */
    private String sourceJobName;

    /**
     * 目标（本月）计划任务ID
     */
    private Long targetItemId;

    /**
     * 目标任务编号
     */
    private String targetItemNo;

    /**
     * 来源月份（yyyy-MM）
     */
    private String sourceMonth;

    /**
     * 目标月份（yyyy-MM）
     */
    private String targetMonth;

    /**
     * 结转人数
     */
    private Integer carryoverQty;

    /**
     * 结转批次ID
     */
    private Long batchId;

    /**
     * 结转批次号
     */
    private String batchNo;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 执行结果（processing处理中/success成功/failed失败/skipped跳过）
     */
    private String result;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
