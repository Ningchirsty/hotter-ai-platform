package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务制品账本 aig_task_artifact（V2 执行契约的「输出制品」）。
 *
 * <p><b>{@link #artifactId} 与字节是两件事</b>：这里存的是<b>平台铸造的制品ID</b>
 * （执行结果 {@code outputs[].artifactId} 与事件 {@code AI_TASK_ARTIFACT_ADDED} 引用它），
 * 字节在对象存储里、由 {@link #storageRef} 指向。合成一列（拿存储键当主键）会让
 * 「同一份字节被两个任务引用」或「存储策略换前缀」直接动摇引用的稳定性。</p>
 *
 * <p><b>{@link #hashVerified} 是这张表最重要的一列</b>：v1 平台<b>不</b>回读对象存储重算哈希，
 * 所以 {@link #sha256} 是<b>生产方声明值</b>，{@code hashVerified} 一律为 {@code N}。
 * 它存在的意义就是让读的人不必猜：看到 {@code sha256} 有值 ≠ 平台验过它。
 * 「有哈希」与「哈希被核对过」混为一谈，会让一次内容替换在下游看起来完全正常。</p>
 *
 * <p><b>被拒的登记也留一行</b>（{@link #validationStatus} = {@code FAIL}）：
 * 那是「对方交了什么、为什么没收」的证据，见 {@code AigArtifactValidationStatusEnum}。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_task_artifact")
public class AigTaskArtifact extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 制品ID（平台铸造；事件与执行结果引用它）
     */
    @TableId(value = "artifact_id")
    private Long artifactId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 所属尝试次数
     */
    private Integer attemptNo;

    /**
     * 关联的任务结果ID（结果可以没有制品，制品也可以多份，故可空）
     */
    private Long resultId;

    /**
     * 制品类型（IMAGE/DESIGN_DOCUMENT/…；契约里是开放字符串，≤32）
     */
    private String artifactType;

    /**
     * MIME 类型（小写存储）
     */
    private String mimeType;

    /**
     * 字节数（生产方声明值；0 允许）
     */
    private Long sizeBytes;

    /**
     * 内容 SHA-256（小写、64 位十六进制；v1 为声明值，见 {@link #hashVerified}）
     */
    private String sha256;

    /**
     * 对象引用（对象存储键；<b>禁止服务器本地路径</b>）
     */
    private String storageRef;

    /**
     * 平台是否回读重算过哈希（v1 一律 N）
     */
    private String hashVerified;

    /**
     * 校验结论（{@code AigArtifactValidationStatusEnum}）
     */
    private String validationStatus;

    /**
     * 校验明细（FAIL 时给字段级原因，一次列全）
     */
    private String validationDetail;

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
