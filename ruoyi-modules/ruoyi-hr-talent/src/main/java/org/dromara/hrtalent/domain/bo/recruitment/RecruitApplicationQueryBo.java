package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应聘记录分页查询业务对象（SPEC-P3 §2.2 GET /recruit/applications）。
 *
 * <p>所有条件均为可选；不接受前端传入越权范围字段。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitApplicationQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘编号（模糊匹配）
     */
    private String applicationNo;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 岗位执行项ID
     */
    private Long jobId;

    /**
     * 当前阶段（字典 recruit_candidate_stage 编码）
     */
    private String currentStage;

    /**
     * 应聘结果（字典 recruit_application_result 编码）
     */
    private String currentStatus;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 来源方式（channel/referral/import 等稳定编码）
     */
    private String sourceType;

    /**
     * 联系日期起（含）
     */
    private LocalDate contactDateBegin;

    /**
     * 联系日期止（含）
     */
    private LocalDate contactDateEnd;

    /**
     * 应聘（投递）时间起（含）
     */
    private LocalDateTime applyTimeBegin;

    /**
     * 应聘（投递）时间止（含）
     */
    private LocalDateTime applyTimeEnd;

    /**
     * 下次跟进时间起（含）
     */
    private LocalDateTime nextFollowTimeBegin;

    /**
     * 下次跟进时间止（含）
     */
    private LocalDateTime nextFollowTimeEnd;

}
