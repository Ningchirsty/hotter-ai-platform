package org.dromara.talent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才联系记录对象 tl_talent_contact
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_talent_contact")
public class TlTalentContact extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 联系记录ID
     */
    @TableId(value = "contact_id")
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
     * 联系结果（字典 tl_contact_result）
     */
    private String contactResult;

    /**
     * 联系内容/备注
     */
    private String content;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
