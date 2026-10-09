package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 制品登记入参（生产方 → 平台制品账本）。
 *
 * <p><b>这里只做「形状」校验（非空/长度），语义校验在 {@code AigArtifactValidator}</b>：
 * MIME 是否在允许清单、大小是否超上限、sha256 是否 64 位十六进制、对象引用是不是本地路径——
 * 那些规则的失败原因要拼成一句可读的明细入库（{@code validation_detail}），
 * 用注解表达不出来，而且注解失败与业务失败的处理路径不同（前者不该落证据行）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskArtifactBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 所属尝试次数（不传则取任务当前的尝试次数）
     */
    private Integer attemptNo;

    /**
     * 关联的任务结果ID（可空）
     */
    private Long resultId;

    /**
     * 制品类型（IMAGE/DESIGN_DOCUMENT/…）
     */
    @NotBlank(message = "制品类型不能为空")
    @Size(max = 32, message = "制品类型长度不能超过 32")
    private String artifactType;

    /**
     * MIME 类型（大小写不敏感，入库统一小写）
     */
    @NotBlank(message = "MIME 类型不能为空")
    @Size(max = 128, message = "MIME 类型长度不能超过 128")
    private String mimeType;

    /**
     * 字节数
     */
    @NotNull(message = "制品大小不能为空")
    private Long sizeBytes;

    /**
     * 内容 SHA-256（大小写不敏感，入库统一小写）
     */
    @NotBlank(message = "制品 sha256 不能为空")
    @Size(max = 64, message = "制品 sha256 长度不能超过 64")
    private String sha256;

    /**
     * 对象引用（对象存储键；<b>不接受服务器本地路径</b>）
     */
    @NotBlank(message = "制品对象引用不能为空")
    @Size(max = 512, message = "制品对象引用长度不能超过 512")
    private String storageRef;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
