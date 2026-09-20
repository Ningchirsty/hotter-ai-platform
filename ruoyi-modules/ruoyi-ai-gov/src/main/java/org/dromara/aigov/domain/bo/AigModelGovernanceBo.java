package org.dromara.aigov.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * AI 模型治理业务对象 aig_model_governance
 * <p>模型主数据仍在 snail-ai 的 {@code sai_model_config}，本对象只承载治理属性；
 * 密钥一律只写「引用」（{@code secretRef}），<b>禁止明文</b>。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigModelGovernance.class, reverseConvertGenerate = false)
public class AigModelGovernanceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 治理记录ID（编辑时必填）
     */
    @NotNull(message = "治理记录ID不能为空", groups = {EditGroup.class})
    private Long governanceId;

    /**
     * 关联 sai_model_config.id
     */
    @NotNull(message = "模型ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long modelId;

    /**
     * 部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）
     */
    @NotBlank(message = "部署类型不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(LOCAL|GROUP|EXTERNAL_ENTERPRISE|EXTERNAL_API)$",
        message = "部署类型只能为 LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API",
        groups = {AddGroup.class, EditGroup.class})
    private String deploymentType;

    /**
     * 允许处理的最高数据等级（PUBLIC/INTERNAL/RESTRICTED）
     */
    @NotBlank(message = "最高数据等级不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED)$", message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED",
        groups = {AddGroup.class, EditGroup.class})
    private String dataLevelMax;

    /**
     * 可用状态（CANDIDATE/TRIAL/GRAY/PRODUCTION/SUSPENDED/RETIRED）
     */
    @NotBlank(message = "生命周期状态不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(CANDIDATE|TRIAL|GRAY|PRODUCTION|SUSPENDED|RETIRED)$",
        message = "生命周期状态只能为 CANDIDATE/TRIAL/GRAY/PRODUCTION/SUSPENDED/RETIRED",
        groups = {AddGroup.class, EditGroup.class})
    private String lifecycleStatus;

    /**
     * 密钥引用（如 kms://ai/qwen），<b>禁止存明文密钥</b>
     */
    @Size(max = 255, message = "密钥引用长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String secretRef;

    /**
     * 输入限制：文本长度/文件类型/图片视频大小/并发
     */
    @Size(max = 500, message = "输入限制长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String inputLimits;

    /**
     * 输出限制：格式/时长/分辨率/结构化输出能力
     */
    @Size(max = 500, message = "输出限制长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String outputLimits;

    /**
     * 成本与配额：单次/单项目/单日预算与限流规则
     */
    @Size(max = 255, message = "成本限制长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String costLimit;

    /**
     * 技术负责人
     */
    @Size(max = 64, message = "技术负责人长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String ownerTech;

    /**
     * 业务负责人
     */
    @Size(max = 64, message = "业务负责人长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String ownerBiz;

    /**
     * 安全审批人
     */
    @Size(max = 64, message = "安全审批人长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
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
     * 状态（0正常 1停用）
     */
    @Pattern(regexp = "^(0|1)?$", message = "状态只能为 0/1", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    // ------------------------------------------------------------------
    // 以下为跨表列表查询条件（仅查询用，不参与治理属性写入）
    // ------------------------------------------------------------------

    /**
     * 查询关键字：匹配 sai_model_config.model_key / model_name / description
     */
    @Size(max = 100, message = "关键字长度不能超过 100", groups = {QueryGroup.class})
    private String keyword;

    /**
     * 模型标识符（模糊匹配，来自 sai_model_config.model_key）
     */
    @Size(max = 100, message = "模型标识长度不能超过 100", groups = {QueryGroup.class})
    private String modelKey;

    /**
     * 模型名称（模糊匹配，来自 sai_model_config.model_name）
     */
    @Size(max = 255, message = "模型名称长度不能超过 255", groups = {QueryGroup.class})
    private String modelName;

    /**
     * 模型类型（CHAT/EMBEDDING/RERANKER/IMAGE/SPEECH），来自 sai_model_config.model_type
     */
    @Size(max = 50, message = "模型类型长度不能超过 50", groups = {QueryGroup.class})
    private String modelType;

    /**
     * 是否启用（来自 sai_model_config.is_enabled）
     */
    private Boolean isEnabled;

}
