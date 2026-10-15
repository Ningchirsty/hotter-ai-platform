package org.dromara.aigov.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登记知识文档与版本入参（设计 §6.4、§8.2）。
 *
 * <p>原文件正文由上传链路处理，这里只接收<b>存储引用</b>与<b>原文哈希</b>——
 * 治理层不搬运大文件，也不在业务表里放受限原文。</p>
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeDocumentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属集合
     */
    @NotNull(message = "知识集合不能为空")
    private Long collectionId;

    /**
     * 文档名（含扩展名，用于识别来源类型）
     */
    @NotBlank(message = "文档名不能为空")
    @Size(max = 255, message = "文档名长度不能超过 255")
    private String sourceName;

    /**
     * 原文 SHA-256（上传链路计算；用于重复文件提示）
     */
    @NotBlank(message = "原文哈希不能为空")
    @Size(min = 64, max = 64, message = "原文哈希必须是 64 位 SHA-256")
    private String sha256;

    /**
     * 原文件存储引用（对象键）
     */
    @Size(max = 500, message = "存储引用长度不能超过 500")
    private String sourceRef;

    /**
     * 版本号（可空=默认 1）
     */
    @Size(max = 32, message = "版本号长度不能超过 32")
    private String version;

    /**
     * 生效开始（可空=立即）
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束（可空=长期）
     */
    private LocalDateTime effectiveTo;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
