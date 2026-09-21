package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 邀约（Offer）登记入参（SPEC-P3 §2.2 POST /recruit/applications/{id}/offer，§8.7）。
 *
 * <p>「邀约结果 + 计划报到日期」是进入「待报到」阶段的必要资料（§7.2 第 5 条），
 * 因此两者都在本接口强制必填。</p>
 *
 * @author hr-talent
 */
@Data
public class OfferRegisterBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 录用邀约日期，为空时服务端取当天
     */
    private LocalDate offerDate;

    /**
     * 邀约结果编码（accepted/rejected 等稳定编码，必填）
     */
    @NotBlank(message = "邀约结果不能为空")
    @Size(max = 32, message = "邀约结果长度不能超过 32")
    private String offerResult;

    /**
     * 计划报到日期（必填）
     */
    @NotNull(message = "计划报到日期不能为空")
    private LocalDate planArrivalDate;

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
