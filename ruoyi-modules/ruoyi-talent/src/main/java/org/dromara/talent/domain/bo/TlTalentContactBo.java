package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlTalentContact;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才联系跟进业务对象 tl_talent_contact
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentContact.class, reverseConvertGenerate = false)
public class TlTalentContactBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 联系记录ID
     */
    @NotNull(message = "联系记录ID不能为空", groups = {EditGroup.class})
    private Long contactId;

    /**
     * 人才ID
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long talentId;

    /**
     * 联系时间
     */
    @NotNull(message = "联系时间不能为空", groups = {AddGroup.class, EditGroup.class})
    private LocalDateTime contactTime;

    /**
     * 联系结果（字典 tl_contact_result）
     */
    private String contactResult;

    /**
     * 联系内容/备注
     */
    @Size(max = 1000, message = "联系内容长度不能超过1000个字符", groups = {AddGroup.class, EditGroup.class})
    private String content;

}
