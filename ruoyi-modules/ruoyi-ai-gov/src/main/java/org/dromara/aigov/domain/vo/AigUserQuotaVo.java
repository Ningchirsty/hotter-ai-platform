package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用人均配额视图（列表用）。
 *
 * @author ai-gov
 */
@Data
public class AigUserQuotaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long quotaId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户账号（冗余存的一份；以 {@link #userId} 为准）
     */
    private String userName;

    /**
     * 每自然日调用次数上限（null = 不限）
     */
    private Integer dailyLimit;

    /**
     * 每自然月调用次数上限（null = 不限）
     */
    private Integer monthlyLimit;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}
