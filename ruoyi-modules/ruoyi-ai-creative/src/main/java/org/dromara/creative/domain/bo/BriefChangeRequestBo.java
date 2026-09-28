package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 平面设计申请修改品牌要求的表单。
 *
 * <p>品牌要求的作者是品牌部、设计只读；设计要改得"提需求"而不是直接改，
 * 所以这里只有一段话（要改什么、为什么），没有字段级编辑。</p>
 *
 * @author creative
 */
@Data
public class BriefChangeRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设计要求修改的内容（必填）
     */
    @NotBlank(message = "请写清希望品牌方修改什么")
    @Size(max = 500, message = "申请内容不能超过 500 字")
    private String message;

}
