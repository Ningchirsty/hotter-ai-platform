package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 能力与模型绑定视图对象 aig_capability_model
 * <p>{@code modelKey}/{@code modelName}/{@code deploymentType} 为联表展示补充字段，
 * 由服务层按 {@code modelId} 回填，不落库。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigCapabilityModel.class)
public class AigCapabilityModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    private Long bindId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 关联 sai_model_config.id
     */
    private Long modelId;

    /**
     * 模型标识符（展示用，来自 sai_model_config.model_key）
     */
    private String modelKey;

    /**
     * 模型名称（展示用，来自 sai_model_config.model_name）
     */
    private String modelName;

    /**
     * 模型类型（展示用，来自 sai_model_config.model_type）
     */
    private String modelType;

    /**
     * 模型是否启用（展示用，来自 sai_model_config.is_enabled）
     */
    private Boolean modelEnabled;

    /**
     * 部署类型（展示用，来自 aig_model_governance.deployment_type）
     */
    private String deploymentType;

    /**
     * 部署类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "deploymentType", other = "aig_deployment_type")
    private String deploymentTypeLabel;

    /**
     * 用途（PRIMARY主选 FALLBACK备选 GRAY灰度，字典 aig_usage_type）
     */
    private String usageType;

    /**
     * 用途标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "usageType", other = "aig_usage_type")
    private String usageTypeLabel;

    /**
     * 优先级，数值越小越优先
     */
    private Integer priority;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "status", other = "sys_normal_disable")
    private String statusLabel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
