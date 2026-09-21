package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模型供应商新增/修改参数。
 * <p>
 * <b>为什么要开放新增供应商</b>：{@code sai_model_provider} 里只有内置的 7 家（OpenAI/Claude/Ollama/…），
 * 这些是 snail-ai 的种子数据。接入自建推理服务、公司内部网关或新的云厂商时，没有对应供应商就无法登记模型，
 * 治理页只能看着空清单。因此治理层对外开放「只 INSERT、列白名单、不写任何密钥」的供应商登记能力。
 * </p>
 * <p><b>边界</b>：本表只有名称/标识/描述/图标/启停，<b>没有也不会有密钥列</b>——
 * 密钥属于模型配置，治理层只登记引用（{@code aig_model_governance.secret_ref}）。
 * </p>
 *
 * @author ai-gov
 */
@Data
public class AigModelProviderBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商ID（修改时必填）
     */
    private Long id;

    /**
     * 供应商名称（展示用）
     */
    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 255, message = "供应商名称长度不能超过 255")
    private String providerName;

    /**
     * 供应商标识（唯一，小写字母/数字/下划线/中划线）
     */
    @NotBlank(message = "供应商标识不能为空")
    @Size(max = 50, message = "供应商标识长度不能超过 50")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,49}$", message = "供应商标识只能用小写字母、数字、下划线或中划线，且以字母或数字开头")
    private String providerKey;

    /**
     * 说明（可选，用于注明接入方式，例如「自建 OpenAI 兼容网关」）
     */
    @Size(max = 2000, message = "说明长度不能超过 2000")
    private String description;

    /**
     * 图标地址（可选）
     */
    @Size(max = 500, message = "图标地址长度不能超过 500")
    private String iconUrl;

    /**
     * 是否启用：停用后不出现在新增模型的供应商下拉里
     */
    private Boolean isEnabled;
}
