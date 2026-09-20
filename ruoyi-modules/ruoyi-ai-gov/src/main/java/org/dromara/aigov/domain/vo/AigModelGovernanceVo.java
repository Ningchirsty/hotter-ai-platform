package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 模型治理扩展视图对象 aig_model_governance
 * <p>单表映射；联表展示模型主数据请用 {@link AigModelVo}。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigModelGovernance.class)
public class AigModelGovernanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 治理记录ID
     */
    private Long governanceId;

    /**
     * 关联 sai_model_config.id
     */
    private Long modelId;

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
    private String status;

    /**
     * 治理记录状态标签
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
