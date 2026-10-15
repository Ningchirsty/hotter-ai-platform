package org.dromara.aigov.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建知识集合入参（设计 §8.2）。
 *
 * @author ai-gov
 */
@Data
public class AigKnowledgeCollectionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 集合编码（租户内唯一）
     */
    @NotBlank(message = "集合编码不能为空")
    @Size(max = 64, message = "集合编码长度不能超过 64")
    private String collectionCode;

    /**
     * 集合名称
     */
    @NotBlank(message = "集合名称不能为空")
    @Size(max = 160, message = "集合名称长度不能超过 160")
    private String collectionName;

    /**
     * 组织范围（可空=不限）
     */
    @Size(max = 255, message = "组织范围长度不能超过 255")
    private String orgScope;

    /**
     * 数据等级（可空=默认 STRICT）
     */
    @Size(max = 32, message = "数据等级长度不能超过 32")
    private String dataLevel;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
