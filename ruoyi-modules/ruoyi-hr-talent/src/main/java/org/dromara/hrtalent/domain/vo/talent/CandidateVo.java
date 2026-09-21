package org.dromara.hrtalent.domain.vo.talent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.enums.CandidateStageEnum;
import org.dromara.hrtalent.enums.ApplicationResultEnum;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候选人列表视图对象（SPEC-P3 §2.1 {@code GET /recruit/candidates}）。
 *
 * <p><b>一人一档</b>：候选人不是独立实体，本视图在人才主档字段（{@link TalentProfileVo}）之上
 * 追加「最近一次应聘记录」的聚合字段，数据来源是
 * {@code hr_talent_profile} 与 {@code hr_recruit_application} 的关联查询。</p>
 *
 * <p><b>安全约束</b>：继承 {@link TalentProfileVo} 的脱敏口径，不返回任何联系方式明文或密文；
 * 电话明文只能经 {@code POST /recruit/candidates/{id}/phone-view} 获取（并写审计）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CandidateVo extends TalentProfileVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录条数
     */
    private Long applicationCount;

    /**
     * 最近一次应聘记录ID
     */
    private Long latestApplicationId;

    /**
     * 最近一次应聘编号
     */
    private String latestApplicationNo;

    /**
     * 最近一次应聘的岗位执行项ID
     */
    private Long latestJobId;

    /**
     * 最近一次应聘的阶段编码（字典 recruit_candidate_stage）
     */
    private String latestStage;

    /**
     * 最近一次应聘的阶段标签（字典 recruit_candidate_stage）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "latestStage", other = "recruit_candidate_stage")
    private String latestStageLabel;

    /**
     * 最近一次应聘的结果编码（字典 recruit_application_result）
     */
    private String latestStatus;

    /**
     * 最近一次应聘的结果标签（字典 recruit_application_result）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "latestStatus", other = "recruit_application_result")
    private String latestStatusLabel;

    /**
     * 最近一次应聘的招聘负责人用户ID
     */
    private Long latestRecruiterId;

    /**
     * 最近一次应聘的招聘负责人昵称（由 {@link #latestRecruiterId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "latestRecruiterId")
    private String latestRecruiterName;

    /**
     * 最近一次应聘的联系日期
     */
    private LocalDate latestContactDate;

    /**
     * 最近一次应聘的投递时间
     */
    private LocalDateTime latestApplyTime;

    /**
     * 最近一次应聘的进入当前阶段时间
     */
    private LocalDateTime latestStageEnterTime;

    /**
     * 最近一次应聘的计划报到日期
     */
    private LocalDate latestPlanArrivalDate;

    /**
     * 最近一次应聘的实际到岗日期
     */
    private LocalDate latestArrivalDate;

    /**
     * 最近一次应聘的期望薪资下限（应聘业务快照）
     */
    private java.math.BigDecimal latestExpectedSalaryMin;

    /**
     * 最近一次应聘的期望薪资上限（应聘业务快照）
     */
    private java.math.BigDecimal latestExpectedSalaryMax;

    /**
     * 最近一次应聘的乐观锁版本号（候选人编辑提交时必须回传）
     */
    private Integer latestVersion;

    /**
     * 阶段中文兜底（字典未配置时保证页面可读）。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getLatestStageText() {
        CandidateStageEnum stage = CandidateStageEnum.find(latestStage);
        return stage == null ? null : stage.getDesc();
    }

    /**
     * 应聘结果中文兜底。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getLatestStatusText() {
        ApplicationResultEnum result = ApplicationResultEnum.find(latestStatus);
        return result == null ? null : result.getDesc();
    }

}
