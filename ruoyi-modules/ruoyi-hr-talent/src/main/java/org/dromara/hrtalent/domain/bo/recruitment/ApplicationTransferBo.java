package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应聘记录转移入参（SPEC-P3 §2.2 POST /recruit/applications/{id}/transfer）。
 *
 * <p>用于在应聘进行中转移招聘负责人或调整应聘岗位；两者至少提供一个。
 * 调整岗位会同时重算该应聘记录与月度计划任务的计入关系（§9.4 一个到岗结果只计入一条有效月度任务）。</p>
 *
 * @author hr-talent
 */
@Data
public class ApplicationTransferBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新的招聘负责人用户ID（与 {@link #jobId} 至少提供一个）
     */
    private Long recruiterId;

    /**
     * 新的岗位执行项ID（与 {@link #recruiterId} 至少提供一个）
     */
    private Long jobId;

    /**
     * 转移原因
     */
    @Size(max = 500, message = "转移原因长度不能超过 500")
    private String reason;

    /**
     * 乐观锁版本号（必填，取自详情返回值）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试")
    private Integer version;

}
