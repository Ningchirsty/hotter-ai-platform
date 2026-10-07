package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 场景强制绑定视图对象 aig_route_scenario_binding
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigRouteScenarioBinding.class)
public class AigRouteScenarioBindingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    private Long bindId;

    /**
     * 场景编码
     */
    private String scenarioCode;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 能力名称（展示用，由服务层按 capabilityCode 回填）
     */
    private String capabilityName;

    /**
     * 供应商ID
     */
    private Long providerId;

    /**
     * 供应商名称（展示用，由服务层回填）
     */
    private String providerName;

    /**
     * 优先序
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
