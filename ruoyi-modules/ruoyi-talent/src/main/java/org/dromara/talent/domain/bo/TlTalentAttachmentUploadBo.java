package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.talent.domain.TlTalentAttachment;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才附件上传业务对象（文件本体由 MultipartFile 单独承载）
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentAttachment.class, reverseConvertGenerate = false)
public class TlTalentAttachmentUploadBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才ID
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class})
    private Long talentId;

    /**
     * 附件类型（RESUME/ID_CARD/EDUCATION_CERT/OTHER，字典 tl_attachment_type）
     */
    @NotBlank(message = "附件类型不能为空", groups = {AddGroup.class})
    private String attachmentType;

}
