package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 未报到登记入参（SPEC-P3 §2.2 POST /recruit/applications/{id}/no-arrival，§8.4）。
 *
 * <p>记录「未报到原因」并把应聘结果置为结束；<b>不</b>改动月度计划任务的到岗人数。</p>
 *
 * @author hr-talent
 */
@Data
public class NoArrivalBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 未报到原因（必填）
     */
    @NotBlank(message = "未报到原因不能为空")
    @Size(max = 500, message = "未报到原因长度不能超过 500")
    private String noArrivalReason;

    /**
     * 可再次联系时间（写入阶段历史的下一步日期）
     */
    private LocalDateTime nextFollowTime;

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
