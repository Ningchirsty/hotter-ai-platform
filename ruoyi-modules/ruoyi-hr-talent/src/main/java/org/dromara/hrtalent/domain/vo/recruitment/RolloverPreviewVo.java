package org.dromara.hrtalent.domain.vo.recruitment;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 月度结转预览与执行结果视图对象（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>预览接口与执行接口返回同一结构：预览时 {@code failedCount} 恒为 0，
 * {@code targetPlanId} 表示「目标月表头已存在则给出ID，否则为空（执行时自动创建）」；
 * 执行时填入真实的成功 / 跳过 / 失败条数与每条明细结果。</p>
 *
 * @author hr-talent
 */
@Data
public class RolloverPreviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 来源月份（yyyy-MM）
     */
    private String sourceMonth;

    /**
     * 目标月份（yyyy-MM）
     */
    private String targetMonth;

    /**
     * 目标月计划表头ID（预览时可能为空，表示执行时将自动创建）
     */
    private Long targetPlanId;

    /**
     * 目标月计划表头是否已存在
     */
    private Boolean targetPlanExists;

    /**
     * 结转批次ID（执行时才有值）
     */
    private Long batchId;

    /**
     * 结转批次号（执行时才有值）
     */
    private String batchNo;

    /**
     * 扫描到的来源任务总数
     */
    private Integer totalItemCount;

    /**
     * 符合结转条件的任务数
     */
    private Integer eligibleCount;

    /**
     * 成功生成的目标任务数
     */
    private Integer successCount;

    /**
     * 跳过数（已结转、已取消、无剩余、关闭结转开关等）
     */
    private Integer skippedCount;

    /**
     * 失败数（单条失败已记录原因，可安全重试）
     */
    private Integer failedCount;

    /**
     * 预计 / 实际结转总人数
     */
    private Integer totalCarryoverQty;

    /**
     * 执行明细
     */
    private List<Item> items = new ArrayList<>();

    /**
     * 结转明细行。
     *
     * @author hr-talent
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 来源任务ID
         */
        private Long sourceItemId;

        /**
         * 来源任务编号
         */
        private String itemNo;

        /**
         * 岗位名称快照
         */
        private String jobName;

        /**
         * 公司（平台部门）ID
         */
        private Long companyDeptId;

        /**
         * 公司名称快照
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
         * 任务负责人用户ID
         */
        private Long ownerId;

        /**
         * 任务负责人名称
         */
        @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "ownerId")
        private String ownerName;

        /**
         * 来源任务紧急程度（字典 recruit_urgency，执行时将复制到目标任务）
         */
        private String urgency;

        /**
         * 来源任务紧急程度标签
         */
        @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "urgency", other = "recruit_urgency")
        private String urgencyLabel;

        /**
         * 原计划人数
         */
        private Integer planQty;

        /**
         * 已计入到岗人数
         */
        private Integer creditedArrivalQty;

        /**
         * 剩余人数
         */
        private Integer remainingQty;

        /**
         * 本次结转人数
         */
        private Integer carryoverQty;

        /**
         * 是否符合结转条件
         */
        private Boolean eligible;

        /**
         * 执行结果（processing/success/failed/skipped；预览时为空）
         */
        private String result;

        /**
         * 跳过原因（不符合条件或幂等命中时填写）
         */
        private String skipReason;

        /**
         * 失败原因
         */
        private String failureReason;

        /**
         * 生成的目标任务ID
         */
        private Long targetItemId;

        /**
         * 执行时间
         */
        private LocalDateTime executeTime;

    }

}
