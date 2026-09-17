package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlTalentDuplicate;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 重复人才预警视图对象 tl_talent_duplicate
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentDuplicate.class)
public class TlTalentDuplicateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    private Long duplicateId;

    /**
     * 来源人才ID（本次提交的）
     */
    private Long sourceTalentId;

    /**
     * 来源人才姓名
     */
    private String sourceName;

    /**
     * 来源人才编号
     */
    private String sourceTalentNo;

    /**
     * 命中人才ID（库中已存在的）
     */
    private Long matchedTalentId;

    /**
     * 命中人才姓名
     */
    private String matchedName;

    /**
     * 命中人才编号
     */
    private String matchedTalentNo;

    /**
     * 命中人才归属区域
     */
    private String matchedRegionCode;

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
     * 确认结论标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "conclusion", other = "tl_duplicate_conclusion")
    private String conclusionLabel;

    /**
     * 确认人
     */
    private Long confirmBy;

    /**
     * 确认人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "confirmBy")
    private String confirmByName;

    /**
     * 确认时间
     */
    private LocalDateTime confirmTime;

    /**
     * 确认说明
     */
    private String confirmRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
