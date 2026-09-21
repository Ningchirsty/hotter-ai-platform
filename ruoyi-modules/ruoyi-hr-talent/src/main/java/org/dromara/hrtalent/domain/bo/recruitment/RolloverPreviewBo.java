package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度结转入参（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>同时承载三类用途：</p>
 * <ul>
 *     <li>结转预览与执行：{@code sourceMonth}、{@code targetMonth} 必填，
 *     {@code companyDeptId} 为空表示按来源月份跨公司结转。</li>
 *     <li>批次分页查询：使用 {@code batchNo}、{@code targetMonth}、{@code result} 过滤。
 *     由于 P1 DDL 未提供独立批次表，批次维度由 {@code hr_recruit_plan_rollover}
 *     明细的 {@code batch_no} 聚合表达。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Data
public class RolloverPreviewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公司（平台部门）ID；为空表示不限定公司
     */
    private Long companyDeptId;

    /**
     * 来源月份（yyyy-MM）
     */
    private String sourceMonth;

    /**
     * 目标月份（yyyy-MM）
     */
    private String targetMonth;

    /**
     * 结转批次号（批次分页查询用）
     */
    private String batchNo;

    /**
     * 执行结果（processing/success/failed/skipped，批次分页查询用）
     */
    private String result;

}
