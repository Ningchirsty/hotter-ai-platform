package org.dromara.aigov.domain.vo;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 模型联表展示视图对象
 * <p>以 snail-ai 的 {@code sai_model_config} 为主表，左连 {@code aig_model_governance}
 * 补治理属性；<b>只读</b>，任何情况下都不返回 {@code sai_model_config.api_key}。</p>
 * <p>{@code secretRef} 与 {@code apiEndpoint} 仅在调用方具备 {@code aig:model:secret}
 * 权限时下发，其余情况由服务层置空。</p>
 *
 * @author ai-gov
 */
@Data
public class AigModelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 模型主数据（sai_model_config）
    // ------------------------------------------------------------------

    /**
     * 模型ID（sai_model_config.id）
     */
    private Long modelId;

    /**
     * 提供商ID（sai_model_provider.id）
     */
    private Long providerId;

    /**
     * 提供商名称（左连 sai_model_provider，便于展示）
     */
    private String providerName;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型标识符
     */
    private String modelKey;

    /**
     * 模型类型（CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH）
     */
    private String modelType;

    /**
     * 底层协议适配器标识
     */
    private String adapterKey;

    /**
     * 模型描述
     */
    private String description;

    /**
     * API 端点 URL（仅在有 aig:model:secret 权限时下发）
     */
    private String apiEndpoint;

    /**
     * 作用域（GLOBAL/PERSONAL）
     */
    private String scope;

    /**
     * 是否为默认模型
     */
    private Boolean isDefault;

    /**
     * 是否启用（sai_model_config.is_enabled）
     */
    private Boolean isEnabled;

    // ------------------------------------------------------------------
    // 治理属性（aig_model_governance，可能为 null：尚未登记治理属性）
    // ------------------------------------------------------------------

    /**
     * 治理记录ID，为 null 表示该模型尚未登记治理属性
     */
    private Long governanceId;

    /**
     * 部署类型（字典 aig_deployment_type）
     */
    private String deploymentType;

    /**
     * 部署类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "deploymentType", other = "aig_deployment_type")
    private String deploymentTypeLabel;

    /**
     * 允许处理的最高数据等级（字典 aig_data_level）
     */
    private String dataLevelMax;

    /**
     * 最高数据等级标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "dataLevelMax", other = "aig_data_level")
    private String dataLevelMaxLabel;

    /**
     * 可用状态（字典 aig_lifecycle_status）
     */
    private String lifecycleStatus;

    /**
     * 可用状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "lifecycleStatus", other = "aig_lifecycle_status")
    private String lifecycleStatusLabel;

    /**
     * 密钥引用（仅在有 aig:model:secret 权限时下发）
     */
    private String secretRef;

    /**
     * 输入限制
     */
    private String inputLimits;

    /**
     * 输出限制
     */
    private String outputLimits;

    /**
     * 成本与配额
     */
    private String costLimit;

    /**
     * 技术负责人
     */
    private String ownerTech;

    /**
     * 业务负责人
     */
    private String ownerBiz;

    /**
     * 安全审批人
     */
    private String ownerSecurity;

    /**
     * 有效期起
     */
    private LocalDate validFrom;

    /**
     * 有效期止
     */
    private LocalDate validTo;

    /**
     * 最近健康检查结果（UP/DOWN/DEGRADED）
     */
    private String healthStatus;

    /**
     * 最近健康检查时间
     */
    private LocalDateTime healthTime;

    /**
     * 治理记录状态（0正常 1停用，字典 sys_normal_disable）
     */
    private String govStatus;

    /**
     * 治理记录状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "govStatus", other = "sys_normal_disable")
    private String govStatusLabel;

    /**
     * 治理备注
     */
    private String govRemark;

    /**
     * 治理记录更新时间
     */
    private LocalDateTime governanceUpdateTime;

}
