package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 路由策略视图对象 aig_route_policy
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigRoutePolicy.class)
public class AigRoutePolicyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 策略ID
     */
    private Long policyId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 能力名称（展示用，由服务层按 capabilityCode 回填）
     */
    private String capabilityName;

    /**
     * 数据等级（字典 aig_data_level）
     */
    private String dataLevel;

    /**
     * 数据等级标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "dataLevel", other = "aig_data_level")
    private String dataLevelLabel;

    /**
     * 优先部署类型（字典 aig_deployment_type）
     */
    private String preferredDeployment;

    /**
     * 优先部署类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "preferredDeployment", other = "aig_deployment_type")
    private String preferredDeploymentLabel;

    /**
     * 是否允许外发（Y允许 N禁止）
     */
    private String allowExternal;

    /**
     * 调用前是否需要审批（Y是 N否）
     */
    private String requireApproval;

    /**
     * 无可用模型时是否转人工（Y是 N否）
     */
    private String fallbackToManual;

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
