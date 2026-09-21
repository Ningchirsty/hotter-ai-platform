package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型 API 密钥写入业务对象（{@code PUT /aigov/model/secret}）。
 *
 * <p><b>为什么独立于治理属性接口</b>：治理属性由 {@code aig:model:edit} 维护，
 * 而密钥是更高敏感度的凭据。若把密钥并入 {@code /aigov/model/governance}，
 * 就等于把「改治理属性」的权限顺带升级成「改全部模型凭据」。此处独立成接口，
 * 由 {@code aig:model:secret} 单独把关。</p>
 *
 * <p><b>语义显式化</b>：不同于 snail-ai 管理端「留空 = 不修改」的隐式约定，
 * 本接口用 {@link #clearKey} 明确表达清除意图，避免「想清空却什么都没发生」：
 * {@code clearKey=true} 时忽略 {@link #apiKey} 并把密钥置空；否则 {@link #apiKey} 必填。</p>
 *
 * @author ai-gov
 */
@Data
public class AigModelSecretBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID（{@code sai_model_config.id}）
     */
    @NotNull(message = "模型ID不能为空", groups = {EditGroup.class})
    private Long modelId;

    /**
     * 明文 API 密钥。
     * <p>明文上限 500：{@code api_key} 列为 {@code VARCHAR(1000)}，SM4 密文经 Base64 后会膨胀。</p>
     */
    @Size(max = 500, message = "API 密钥长度不能超过 500", groups = {EditGroup.class})
    private String apiKey;

    /**
     * 是否清除密钥；为 true 时忽略 {@link #apiKey} 并把密钥置空
     */
    private Boolean clearKey;

}
