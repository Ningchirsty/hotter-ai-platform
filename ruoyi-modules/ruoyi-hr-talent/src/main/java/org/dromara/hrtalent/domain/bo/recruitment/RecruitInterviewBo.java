package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitInterview;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试记录业务对象 hr_recruit_interview。
 * <p>同时服务于两个接口：</p>
 * <ul>
 *     <li>{@code POST /recruit/interviews} 安排面试：{@code applicationId}、{@code roundNo}、
 *     {@code scheduleTime}、{@code interviewerIds} 必填；</li>
 *     <li>{@code PUT /recruit/interviews/{id}} 改期：只有 {@code interviewId} 必填，
 *     时间/方式/地点/面试官均可选，未提供的项沿用原面试记录。</li>
 * </ul>
 *
 * <p>面试官是<b>一人或多人</b>，以 {@link #interviewerIds} 数组入参，落库时逐行写入
 * {@code hr_recruit_interviewer}；顺序第一人默认主面（lead），其余为协同（assist）。</p>
 *
 * <p>面试状态、结果、反馈时间均由服务层维护，前端不能通过本 BO 直接改写。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitInterview.class, reverseConvertGenerate = false)
public class RecruitInterviewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试记录ID（改期时必填）
     */
    @NotNull(message = "面试记录ID不能为空", groups = {EditGroup.class})
    private Long interviewId;

    /**
     * 应聘记录ID（安排面试时必填；改期沿用原值）
     */
    @NotNull(message = "应聘记录ID不能为空", groups = {AddGroup.class})
    private Long applicationId;

    /**
     * 面试轮次（从 1 开始：1 一面、2 二面、3 及以后为扩展轮次）
     */
    @NotNull(message = "面试轮次不能为空", groups = {AddGroup.class})
    @Min(value = 1, message = "面试轮次必须从 1 开始", groups = {AddGroup.class, EditGroup.class})
    private Integer roundNo;

    /**
     * 计划面试时间（安排面试时必填；改期未提供时沿用原时间）
     */
    @NotNull(message = "计划面试时间不能为空", groups = {AddGroup.class})
    private LocalDateTime scheduleTime;

    /**
     * 预计结束时间
     */
    private LocalDateTime endTime;

    /**
     * 面试方式（onsite现场/video视频/phone电话等稳定编码）
     */
    @Size(max = 32, message = "面试方式长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String method;

    /**
     * 面试地点或线上链接
     */
    @Size(max = 255, message = "面试地点长度不能超过 255", groups = {AddGroup.class, EditGroup.class})
    private String location;

    /**
     * 面试官用户ID数组（一人或多人，安排面试时必填；改期未提供时沿用原面试官）
     */
    @NotEmpty(message = "请至少指定一名面试官", groups = {AddGroup.class})
    @Size(max = 20, message = "面试官最多 20 人", groups = {AddGroup.class, EditGroup.class})
    private Long[] interviewerIds;

    /**
     * 改期原因（§7.3 保留操作记录，会追加写入原面试记录的备注）
     */
    @Size(max = 500, message = "改期原因长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String changeReason;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
