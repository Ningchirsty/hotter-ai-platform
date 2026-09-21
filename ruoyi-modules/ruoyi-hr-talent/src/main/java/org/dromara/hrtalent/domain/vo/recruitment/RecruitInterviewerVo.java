package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitInterviewer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试参与人视图对象 hr_recruit_interviewer。
 * <p>一名面试官一行；{@code feedback} 是该面试官<b>个人</b>意见/结论，
 * 与汇总意见（{@code hr_recruit_interview.feedback}）分开保存。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitInterviewer.class)
public class RecruitInterviewerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试参与人ID
     */
    private Long interviewerId;

    /**
     * 面试记录ID
     */
    private Long interviewId;

    /**
     * 面试官用户ID
     */
    private Long userId;

    /**
     * 面试官姓名快照（落库时的人员名称，可能滞后于用户档案）
     */
    private String userName;

    /**
     * 面试官当前昵称（由 {@link #userId} 翻译回填）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "userId")
    private String userNickName;

    /**
     * 面试官角色（lead主面/assist协同/hr/tech 等稳定编码）
     */
    private String interviewerRole;

    /**
     * 反馈状态（pending/submitted/waived）
     */
    private String feedbackStatus;

    /**
     * 反馈时间
     */
    private LocalDateTime feedbackTime;

    /**
     * 面试官评分
     */
    private BigDecimal score;

    /**
     * 面试官个人意见/结论
     */
    private String feedback;

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
