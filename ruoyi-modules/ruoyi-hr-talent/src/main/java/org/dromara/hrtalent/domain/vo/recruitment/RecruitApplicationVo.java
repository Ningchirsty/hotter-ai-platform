package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitApplication;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应聘记录视图对象 hr_recruit_application（SPEC-P3 §2.2 / §3.2）。
 *
 * <p>本对象<b>不包含</b>人才基础信息（姓名、电话、邮箱等），人才侧资料一律回查人才主档接口；
 * 应聘记录侧只返回业务快照字段。字典标签与人员昵称统一用 {@code @Translation} 回填。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitApplication.class)
public class RecruitApplicationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 应聘编号（业务编号，唯一）
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
     * 本次应聘使用的简历版本ID
     */
    private Long resumeId;

    /**
     * 当前阶段（字典 recruit_candidate_stage 编码）
     */
    private String currentStage;

    /**
     * 当前阶段标签（字典 recruit_candidate_stage）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "currentStage", other = "recruit_candidate_stage")
    private String currentStageLabel;

    /**
     * 应聘结果（字典 recruit_application_result 编码）
     */
    private String currentStatus;

    /**
     * 应聘结果标签（字典 recruit_application_result）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "currentStatus", other = "recruit_application_result")
    private String currentStatusLabel;

    /**
     * 来源渠道ID
     */
    private Long sourceChannelId;

    /**
     * 来源方式（channel/referral/import 等稳定编码）
     */
    private String sourceType;

    /**
     * 期望薪资低值
     */
    private BigDecimal expectedSalaryMin;

    /**
     * 期望薪资高值
     */
    private BigDecimal expectedSalaryMax;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 招聘负责人昵称（由 {@link #recruiterId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "recruiterId")
    private String recruiterName;

    /**
     * 联系日期
     */
    private LocalDate contactDate;

    /**
     * 下次跟进时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 计划报到日期
     */
    private LocalDate planArrivalDate;

    /**
     * 录用邀约日期
     */
    private LocalDate offerDate;

    /**
     * 邀约结果（accepted/rejected 等稳定编码）
     */
    private String offerResult;

    /**
     * 实际到岗日期
     */
    private LocalDate arrivalDate;

    /**
     * 未报到原因
     */
    private String noArrivalReason;

    /**
     * 应聘（投递）时间
     */
    private LocalDateTime applyTime;

    /**
     * 进入当前阶段时间
     */
    private LocalDateTime stageEnterTime;

    /**
     * 淘汰/撤回原因
     */
    private String rejectReason;

    /**
     * 乐观锁版本号（流转提交时必须回传）
     */
    private Integer version;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
