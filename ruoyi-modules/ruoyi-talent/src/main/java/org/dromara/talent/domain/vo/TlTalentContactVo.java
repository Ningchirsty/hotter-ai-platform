package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlTalentContact;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才联系跟进视图对象 tl_talent_contact
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentContact.class)
public class TlTalentContactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 联系记录ID
     */
    private Long contactId;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 联系时间
     */
    private LocalDateTime contactTime;

    /**
     * 联系人（sys_user.user_id）
     */
    private Long contactorId;

    /**
     * 联系人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "contactorId")
    private String contactorName;

    /**
     * 联系结果（字典 tl_contact_result）
     */
    private String contactResult;

    /**
     * 联系结果标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "contactResult", other = "tl_contact_result")
    private String contactResultLabel;

    /**
     * 联系内容/备注
     */
    private String content;

}
