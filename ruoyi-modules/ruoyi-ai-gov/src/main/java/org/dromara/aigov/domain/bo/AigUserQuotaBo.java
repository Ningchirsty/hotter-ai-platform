package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用人均配额配置入参。
 *
 * <p><b>一人一行</b>：{@code user_id} 上有唯一键，保存时按它 upsert——
 * 同一个人的日/月两条上限写在同一行里，避免「今天改了日上限、月上限还是旧值」这种两行不同步。</p>
 *
 * @author ai-gov
 */
@Data
public class AigUserQuotaBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 配额ID（有值表示改既有行；为空表示按 userId 新增）
     */
    private Long quotaId;

    /**
     * 用户ID
     */
    @NotNull(message = "用户不能为空")
    private Long userId;

    /**
     * 调用人账号（可空；冗余存一份便于列表显示与离线核对，以 userId 为准）
     */
    @Size(max = 64, message = "账号长度不能超过 64")
    private String userName;

    /**
     * 每自然日调用次数上限（可空 = 不限）
     */
    @Min(value = 0, message = "日上限不能为负数")
    private Integer dailyLimit;

    /**
     * 每自然月调用次数上限（可空 = 不限）
     */
    @Min(value = 0, message = "月上限不能为负数")
    private Integer monthlyLimit;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
