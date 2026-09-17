package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlParseField;

import java.io.Serial;
import java.io.Serializable;

/**
 * 解析字段人工复核确认业务对象
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlParseField.class, reverseConvertGenerate = false)
public class TlParseFieldConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段ID
     */
    @NotNull(message = "字段ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long fieldId;

    /**
     * 人工确认值
     */
    @Size(max = 500, message = "人工确认值长度不能超过500个字符", groups = {AddGroup.class, EditGroup.class})
    private String confirmedValue;

    /**
     * 确认状态（0待确认 1已确认 2已忽略）
     */
    @NotBlank(message = "确认状态不能为空", groups = {AddGroup.class, EditGroup.class})
    private String confirmStatus;

}
