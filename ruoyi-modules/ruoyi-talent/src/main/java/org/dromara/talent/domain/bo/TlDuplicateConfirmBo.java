package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlTalentDuplicate;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重复人才预警确认业务对象
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentDuplicate.class, reverseConvertGenerate = false)
public class TlDuplicateConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    @NotNull(message = "预警ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long duplicateId;

    /**
     * 确认结论（PENDING/DIFFERENT/SAME，字典 tl_duplicate_conclusion）
     */
    @NotBlank(message = "确认结论不能为空", groups = {AddGroup.class, EditGroup.class})
    private String conclusion;

    /**
     * 确认说明
     */
    @Size(max = 255, message = "确认说明长度不能超过255个字符", groups = {AddGroup.class, EditGroup.class})
    private String confirmRemark;

}
