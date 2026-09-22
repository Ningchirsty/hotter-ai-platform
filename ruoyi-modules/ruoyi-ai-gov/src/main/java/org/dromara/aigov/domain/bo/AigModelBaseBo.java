package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑模型主数据业务对象（{@code PUT /aigov/model/base}）。
 *
 * <p><b>为什么需要这个入口</b>：原先治理层对 {@code sai_model_config} 只有「新增」一条写路径，
 * 登记错了就没法收拾——实测有人因为旧校验不允许斜杠、把 OpenRouter 的
 * {@code openrouter/free} 手写成了 {@code orfree}，随后既改不回来（治理台「模型键」是只读展示），
 * 又没有下架入口，只能跳到 snail-ai 管理端去改。本对象补上治理台内的编辑能力。</p>
 *
 * <p><b>写入口径</b>：只更新 {@code sai_model_config} 的<b>主数据白名单列</b>，
 * <b>绝不包含 {@code api_key}</b>（那有独立入口 {@code PUT /aigov/model/secret}，
 * 且要加密后写入）。两个入口分开，权限也分开。</p>
 *
 * <p><b>关于 {@code apiEndpoint}</b>：该字段对无 {@code aig:model:secret} 权限的账号是脱敏的
 * （列表里读不到），若照常参与更新就会把已有端点<b>清空</b>。因此约定：
 * 调用方没有该权限时，Service 会忽略本字段，且前端不下发它。详见
 * {@code AigModelGovernanceServiceImpl#updateModelBase}。</p>
 *
 * @author ai-gov
 */
@Data
public class AigModelBaseBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型ID（{@code sai_model_config.id}）
     */
    @NotNull(message = "模型ID不能为空", groups = {EditGroup.class})
    private Long modelId;

    /**
     * 供应商ID（{@code sai_model_provider.id}）
     */
    @NotNull(message = "供应商不能为空", groups = {EditGroup.class})
    private Long providerId;

    /**
     * 模型标识（会作为请求体的 model 原样发给上游，必须与上游模型目录逐字一致）
     */
    @NotBlank(message = "模型标识不能为空", groups = {EditGroup.class})
    @Size(max = 100, message = "模型标识长度不能超过 100", groups = {EditGroup.class})
    @Pattern(regexp = AigConstants.MODEL_KEY_PATTERN, message = AigConstants.MODEL_KEY_PATTERN_MESSAGE,
        groups = {EditGroup.class})
    private String modelKey;

    /**
     * 模型名称（展示用）
     */
    @NotBlank(message = "模型名称不能为空", groups = {EditGroup.class})
    @Size(max = 255, message = "模型名称长度不能超过 255", groups = {EditGroup.class})
    private String modelName;

    /**
     * 模型类型（CHAT / EMBEDDING 等）
     */
    @NotBlank(message = "模型类型不能为空", groups = {EditGroup.class})
    @Size(max = 50, message = "模型类型长度不能超过 50", groups = {EditGroup.class})
    private String modelType;

    /**
     * 适配器标识（如 openai-compatible、local-rule）
     */
    @Size(max = 100, message = "适配器标识长度不能超过 100", groups = {EditGroup.class})
    private String adapterKey;

    /**
     * 接口地址；本地部署可留空。
     * <p>无 {@code aig:model:secret} 权限时本字段会被 Service 忽略（避免误清空）。</p>
     */
    @Size(max = 500, message = "接口地址长度不能超过 500", groups = {EditGroup.class})
    private String apiEndpoint;

    /**
     * 说明
     */
    @Size(max = 1000, message = "说明长度不能超过 1000", groups = {EditGroup.class})
    private String description;

    /**
     * 作用域（GLOBAL / LOCAL）
     */
    @Size(max = 20, message = "作用域长度不能超过 20", groups = {EditGroup.class})
    private String scope;

    /**
     * 是否默认模型
     */
    private Boolean isDefault;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

}
