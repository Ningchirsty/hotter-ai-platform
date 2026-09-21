package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitStageLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应聘阶段历史视图对象 hr_recruit_stage_log（SPEC-P3 §2.2 / §8.5）。
 *
 * <p>历史记录只追加、不覆盖；本对象只读返回，不含任何敏感字段。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitStageLog.class)
public class RecruitStageLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 阶段历史ID
     */
    private Long logId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 原阶段（字典 recruit_candidate_stage 编码）
     */
    private String fromStage;

    /**
     * 原阶段标签（字典 recruit_candidate_stage）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "fromStage", other = "recruit_candidate_stage")
    private String fromStageLabel;

    /**
     * 目标阶段（字典 recruit_candidate_stage 编码）
     */
    private String toStage;

    /**
     * 目标阶段标签（字典 recruit_candidate_stage）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "toStage", other = "recruit_candidate_stage")
    private String toStageLabel;

    /**
     * 操作动作（move/reject/withdraw/pause/talent_pool/arrive）
     */
    private String actionType;

    /**
     * 阶段结果（字典 recruit_application_result 编码）
     */
    private String result;

    /**
     * 阶段结果标签（字典 recruit_application_result）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "result", other = "recruit_application_result")
    private String resultLabel;

    /**
     * 原因编码（字典编码，不存中文）
     */
    private String reasonCode;

    /**
     * 阶段说明
     */
    private String comment;

    /**
     * 本次跟进的下一步日期
     */
    private LocalDateTime nextFollowTime;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人昵称（由 {@link #operatorId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 备注
     */
    private String remark;

}
