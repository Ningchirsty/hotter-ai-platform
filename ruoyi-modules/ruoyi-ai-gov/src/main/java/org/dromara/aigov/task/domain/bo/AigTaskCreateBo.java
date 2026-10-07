package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建 AI 任务入参。
 *
 * <p><b>{@link #snapshotJson} 由业务域组装成字符串后传入，而不是传对象让本层序列化</b>：
 * 快照哈希是对「实际存进库的那串字节」算的。若本层拿到对象再序列化，
 * 字段顺序、null 处理、数字格式都会影响字节；将来换个序列化配置，
 * 同样的快照就会算出不同的哈希，于是「快照被改写了吗」这个判断彻底失效。
 * 传字符串则哈希与存储永远一致。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务类型（{@code AigTaskTypeEnum}）
     */
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 32, message = "任务类型长度不能超过 32")
    private String taskType;

    /**
     * 业务能力编码（业务侧只传能力，不传厂商参数）
     */
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String capabilityCode;

    /**
     * 业务场景
     */
    @Size(max = 32, message = "场景编码长度不能超过 32")
    private String scenarioCode;

    /**
     * 所属业务域（CONTENT/CREATIVE/TALENT…）
     */
    @NotBlank(message = "业务域不能为空")
    @Size(max = 32, message = "业务域长度不能超过 32")
    private String projectType;

    /**
     * 业务对象ID
     */
    private Long projectId;

    /**
     * 发起该任务的 Agent 版本ID（人工发起可为空）
     */
    private Long agentVersionId;

    /**
     * 数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     */
    @NotBlank(message = "数据等级不能为空")
    private String dataLevel;

    /**
     * 业务侧是否允许外发（Y/N）；与路由策略<b>取与</b>
     */
    private String allowExternal;

    /**
     * 最大自动尝试次数（为空取默认 3）
     */
    private Integer maxAttempt;

    /**
     * 外部提交幂等键（同一业务域 + 同一提交人 + 同一键只建一个任务）
     */
    @Size(max = 128, message = "幂等键长度不能超过 128")
    private String idempotencyKey;

    /**
     * 输入快照内容（JSON 字符串，由业务域组装；本层原样落库并按其字节算哈希）
     */
    @NotBlank(message = "输入快照不能为空")
    private String snapshotJson;

    /**
     * 快照中的负面约束（禁止项/禁改项）
     */
    private String negativeConstraints;

    /**
     * 预算上限（算不出留空，不要填 0）
     */
    @DecimalMin(value = "0", message = "预算不能为负数")
    private BigDecimal budgetAmount;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
