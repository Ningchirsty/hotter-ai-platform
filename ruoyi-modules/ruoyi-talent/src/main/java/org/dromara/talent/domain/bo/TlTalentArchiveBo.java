package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlTalent;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才归档业务对象
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalent.class, reverseConvertGenerate = false)
public class TlTalentArchiveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才ID
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long talentId;

    /**
     * 归档备注
     */
    @Size(max = 500, message = "归档备注长度不能超过500个字符", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
