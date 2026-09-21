package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 实际报到登记入参（SPEC-P3 §2.2 POST /recruit/applications/{id}/arrival，§7.4）。
 *
 * <p>报到是唯一会把到岗人数计入月度计划任务的入口，服务端在<b>同一事务</b>内完成
 * 「更新应聘记录 → 累加计划任务 {@code credited_arrival_qty} → 刷新任务状态」，
 * 因此不允许调用方直接指定到岗人数或状态值。</p>
 *
 * @author hr-talent
 */
@Data
public class ArrivalRegisterBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 实际报到日期（必填）
     */
    @NotNull(message = "实际报到日期不能为空")
    private LocalDate arrivalDate;

    /**
     * 计入的月度计划任务ID（可选；不传时由应聘记录的岗位或有效计入关系解析，
     * 解析出多条有效任务时按「一个到岗结果只能计入一条月度任务」直接报错）
     */
    private Long planItemId;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /**
     * 乐观锁版本号（必填，取自详情返回值）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试")
    private Integer version;

}
