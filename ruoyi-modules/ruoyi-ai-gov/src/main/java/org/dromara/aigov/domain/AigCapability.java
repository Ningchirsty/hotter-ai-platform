package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 业务能力模板对象 aig_capability
 * <p>能力模板定义「做什么」，不绑定具体模型；模型选择由
 * {@code aig_capability_model} + {@code aig_route_policy} 决定。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_capability")
public class AigCapability extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力ID
     */
    @TableId(value = "capability_id")
    private Long capabilityId;

    /**
     * 能力编码，如 talent_match / brief_precheck（对外稳定契约）
     */
    private String capabilityCode;

    /**
     * 能力名称
     */
    private String capabilityName;

    /**
     * 业务目标：该能力服务的业务结果
     */
    private String bizGoal;

    /**
     * 需要的模型能力标签，逗号分隔（TEXT/VISION/OCR/IMAGE/VIDEO/EMBEDDING/RERANK/AGENT）
     */
    private String requiredTags;

    /**
     * 输入 Schema（JSON），约定允许的字段与数据等级
     */
    private String inputSchema;

    /**
     * 输出 Schema（JSON），强制结构化字段/置信度/证据/待确认项
     */
    private String outputSchema;

    /**
     * 数据策略（LOCAL_ONLY仅本地 LOCAL_FIRST本地优先 EXTERNAL_ALLOWED允许外部）
     */
    private String dataPolicy;

    /**
     * 必须人工确认的结论点
     */
    private String humanConfirmPoints;

    /**
     * 质量阈值：格式/完整性/可信度/超时与失败处理
     */
    private String qualityThreshold;

    /**
     * 审计等级（SUMMARY摘要 FULL完整输出 HASH_ONLY仅哈希）
     */
    private String auditLevel;

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
