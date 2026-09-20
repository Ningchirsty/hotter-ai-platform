package org.dromara.aigov.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.enums.AigAuditLevelEnum;
import org.dromara.aigov.enums.AigDataPolicyEnum;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 业务能力模板业务对象 aig_capability
 * <p>能力模板定义「做什么」，<b>不绑定具体模型</b>；模型选择由
 * {@code aig_capability_model} + {@code aig_route_policy} 决定。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigCapability.class, reverseConvertGenerate = false)
public class AigCapabilityBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力ID（编辑时必填）
     */
    @NotNull(message = "能力ID不能为空", groups = {EditGroup.class})
    private Long capabilityId;

    /**
     * 能力编码（对外稳定契约，创建后不建议修改）
     */
    @NotBlank(message = "能力编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "能力编码只能由小写字母/数字/下划线组成，且以字母开头",
        groups = {AddGroup.class, EditGroup.class})
    private String capabilityCode;

    /**
     * 能力名称
     */
    @NotBlank(message = "能力名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 100, message = "能力名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String capabilityName;

    /**
     * 业务目标：该能力服务的业务结果
     */
    @Size(max = 500, message = "业务目标长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String bizGoal;

    /**
     * 需要的模型能力标签，逗号分隔（TEXT/VISION/OCR/IMAGE/VIDEO/EMBEDDING/RERANK/AGENT）
     */
    @Size(max = 255, message = "能力标签长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String requiredTags;

    /**
     * 输入 Schema（JSON），约定允许的字段与数据等级
     */
    private String inputSchema;

    /**
     * 输出 Schema（JSON），强制结构化字段/置信度/证据/待确认项
     * <p>调用编排据此判定模型输出是否合格，不符合视为失败。</p>
     */
    private String outputSchema;

    /**
     * 数据策略（{@link AigDataPolicyEnum}：LOCAL_ONLY/LOCAL_FIRST/EXTERNAL_ALLOWED）
     */
    @Pattern(regexp = "^(LOCAL_ONLY|LOCAL_FIRST|EXTERNAL_ALLOWED)?$",
        message = "数据策略只能为 LOCAL_ONLY/LOCAL_FIRST/EXTERNAL_ALLOWED",
        groups = {AddGroup.class, EditGroup.class})
    private String dataPolicy;

    /**
     * 必须人工确认的结论点（逗号分隔）
     */
    @Size(max = 500, message = "人工确认点长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String humanConfirmPoints;

    /**
     * 质量阈值：格式/完整性/可信度/超时与失败处理
     */
    @Size(max = 500, message = "质量阈值长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String qualityThreshold;

    /**
     * 审计等级（{@link AigAuditLevelEnum}：SUMMARY/FULL/HASH_ONLY）
     */
    @Pattern(regexp = "^(SUMMARY|FULL|HASH_ONLY)?$", message = "审计等级只能为 SUMMARY/FULL/HASH_ONLY",
        groups = {AddGroup.class, EditGroup.class})
    private String auditLevel;

    /**
     * 状态（0正常 1停用）
     */
    @Pattern(regexp = "^(0|1)?$", message = "状态只能为 0/1", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
