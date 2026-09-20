package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 业务能力模板视图对象 aig_capability
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigCapability.class)
public class AigCapabilityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力ID
     */
    private Long capabilityId;

    /**
     * 能力编码，如 talent_match（对外稳定契约）
     */
    private String capabilityCode;

    /**
     * 能力名称
     */
    private String capabilityName;

    /**
     * 业务目标
     */
    private String bizGoal;

    /**
     * 需要的模型能力标签，逗号分隔
     */
    private String requiredTags;

    /**
     * 输入 Schema（JSON）
     */
    private String inputSchema;

    /**
     * 输出 Schema（JSON）
     */
    private String outputSchema;

    /**
     * 数据策略（字典 aig_data_policy）
     */
    private String dataPolicy;

    /**
     * 数据策略标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "dataPolicy", other = "aig_data_policy")
    private String dataPolicyLabel;

    /**
     * 必须人工确认的结论点
     */
    private String humanConfirmPoints;

    /**
     * 质量阈值
     */
    private String qualityThreshold;

    /**
     * 审计等级（字典 aig_audit_level）
     */
    private String auditLevel;

    /**
     * 审计等级标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "auditLevel", other = "aig_audit_level")
    private String auditLevelLabel;

    /**
     * 状态（0正常 1停用，字典 sys_normal_disable）
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
