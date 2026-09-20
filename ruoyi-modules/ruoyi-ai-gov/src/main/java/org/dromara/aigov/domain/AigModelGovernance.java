package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 模型治理扩展对象 aig_model_governance
 * <p>模型主数据仍在 snail-ai 的 {@code sai_model_config}，本表只通过
 * {@code model_id} 补齐治理属性，**刻意不加外键**。
 * 密钥一律只存引用（{@code secret_ref}），禁止明文。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_model_governance")
public class AigModelGovernance extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 治理记录ID
     */
    @TableId(value = "governance_id")
    private Long governanceId;

    /**
     * 关联 sai_model_config.id（模型主数据仍在 snail-ai）
     */
    private Long modelId;

    /**
     * 部署类型（LOCAL本地私有 GROUP集团共享 EXTERNAL_ENTERPRISE外部企业服务 EXTERNAL_API外部API）
     */
    private String deploymentType;

    /**
     * 允许处理的最高数据等级（PUBLIC公开 INTERNAL内部 RESTRICTED限制）
     */
    private String dataLevelMax;

    /**
     * 可用状态（CANDIDATE候选 TRIAL试验 GRAY灰度 PRODUCTION生产 SUSPENDED暂停 RETIRED退役）
     */
    private String lifecycleStatus;

    /**
     * 密钥引用（如 kms://ai/qwen），**禁止存明文密钥**
     */
    private String secretRef;

    /**
     * 输入限制：文本长度/文件类型/图片视频大小/并发
     */
    private String inputLimits;

    /**
     * 输出限制：格式/时长/分辨率/结构化输出能力
     */
    private String outputLimits;

    /**
     * 成本与配额：单次/单项目/单日预算与限流规则
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
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
