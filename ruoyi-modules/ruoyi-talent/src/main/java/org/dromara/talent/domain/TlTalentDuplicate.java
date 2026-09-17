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
 * 重复人才预警对象 tl_talent_duplicate
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_talent_duplicate")
public class TlTalentDuplicate extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    @TableId(value = "duplicate_id")
    private Long duplicateId;

    /**
     * 来源人才ID（本次提交的）
     */
    private Long sourceTalentId;

    /**
     * 命中人才ID（库中已存在的）
     */
    private Long matchedTalentId;

    /**
     * 匹配规则（PHONE_HASH/NAME_PHONE_TAIL4/NAME_REGION）
     */
    private String matchRule;

    /**
     * 匹配分数（0-100）
     */
    private Integer matchScore;

    /**
     * 确认结论（PENDING/DIFFERENT/SAME，字典 tl_duplicate_conclusion）
     */
    private String conclusion;

    /**
     * 确认人
     */
    private Long confirmBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmTime;

    /**
     * 确认说明
     */
    private String confirmRemark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
