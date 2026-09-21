package org.dromara.hrtalent.domain.vo.recruitment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitInterview;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试记录视图对象 hr_recruit_interview。
 *
 * <p><b>翻译说明</b>：{@code result} 用字典 {@code recruit_interview_result} 回填标签；
 * 面试官是<b>多值</b>字段，昵称必须通过 {@link #getInterviewerIdText()} 拼成逗号串后再交给
 * {@code @Translation}（参见 P2 岗位域 {@code RecruitJobVo.getAssistantIdText()}），
 * 否则翻译会静默失效。</p>
 *
 * <p><b>候选人摘要</b>：{@code applicationNo}/{@code candidateName}/{@code jobName}/
 * {@code currentStage}/{@code planItemId} 由服务层<b>只读</b>关联应聘记录与人才主档带出，
 * 本域不写 {@code hr_recruit_application} 与 {@code hr_recruit_plan_item}。</p>
 *
 * <p><b>面试方式与面试状态</b>：设计文档 §10 未定义对应字典组，故不凭空引用字典，
 * 状态中文标签由本类内的稳定编码映射兜底。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitInterview.class)
public class RecruitInterviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试状态：待安排完成（时间未定）
     */
    private static final String STATUS_PENDING = "pending";

    /**
     * 面试状态：已安排
     */
    private static final String STATUS_SCHEDULED = "scheduled";

    /**
     * 面试状态：已完成
     */
    private static final String STATUS_FINISHED = "finished";

    /**
     * 面试状态：已取消
     */
    private static final String STATUS_CANCELLED = "cancelled";

    /**
     * 面试状态：已改期（保留的历史记录）
     */
    private static final String STATUS_RESCHEDULED = "rescheduled";

    /**
     * 面试状态中文标签映射（DDL 注释未绑定字典组，按稳定编码兜底）。
     */
    private static final Map<String, String> STATUS_LABELS = Map.of(
        STATUS_PENDING, "待安排",
        STATUS_SCHEDULED, "已安排",
        STATUS_FINISHED, "已完成",
        STATUS_CANCELLED, "已取消",
        STATUS_RESCHEDULED, "已改期"
    );

    /**
     * 面试记录ID
     */
    private Long interviewId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 面试轮次（1 一面、2 二面、3 及以后为扩展轮次）
     */
    private Integer roundNo;

    /**
     * 计划面试时间
     */
    private LocalDateTime scheduleTime;

    /**
     * 实际结束时间
     */
    private LocalDateTime endTime;

    /**
     * 面试方式（onsite/video/phone 等稳定编码）
     */
    private String method;

    /**
     * 面试地点或线上链接
     */
    private String location;

    /**
     * 面试状态（pending/scheduled/finished/cancelled/rescheduled）
     */
    private String status;

    /**
     * 面试评分（汇总）
     */
    private BigDecimal score;

    /**
     * 面试结果（字典 recruit_interview_result 编码）
     */
    private String result;

    /**
     * 面试结果标签（字典 recruit_interview_result）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "result", other = "recruit_interview_result")
    private String resultLabel;

    /**
     * 面试汇总意见（多人意见分别见 {@link #interviewers}）
     */
    private String feedback;

    /**
     * 反馈时间
     */
    private LocalDateTime feedbackTime;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 备注（含改期/取消的操作留痕）
     */
    private String remark;

    /**
     * 面试官用户ID数组（前端多选组件绑定；由 {@link #getInterviewerIdText()} 生成翻译源）
     */
    private Long[] interviewerIds;

    /**
     * 面试官昵称，多个以英文逗号分隔（由 {@link #getInterviewerIdText()} 批量翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "interviewerIdText")
    private String interviewerNames;

    /**
     * 面试官明细（每人一行，包含个人意见与反馈状态）
     */
    private List<RecruitInterviewerVo> interviewers;

    /**
     * 应聘编号（只读关联带出）
     */
    private String applicationNo;

    /**
     * 候选人姓名（只读关联人才主档带出）
     */
    private String candidateName;

    /**
     * 岗位名称（只读关联岗位执行项带出）
     */
    private String jobName;

    /**
     * 关联月度计划任务ID（只读关联带出，供面试结果事件携带）
     */
    private Long planItemId;

    /**
     * 应聘当前阶段（字典 recruit_candidate_stage 编码，只读关联带出）
     */
    private String currentStage;

    /**
     * 应聘当前阶段标签（字典 recruit_candidate_stage）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "currentStage", other = "recruit_candidate_stage")
    private String currentStageLabel;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 面试官用户ID入库串（英文逗号分隔）。
     *
     * <p>不是数据库直出字段，而是由 {@link #interviewerIds} 组合得到的<b>只读</b>属性：
     * 一方面作为 {@link #interviewerNames} 翻译的取数来源（翻译处理器按 getter 取值），
     * 另一方面用 {@code @JsonIgnore} 避免把原始 ID 串重复输出给前端。</p>
     *
     * @return 逗号分隔的用户ID串，无面试官时返回 null
     */
    @JsonIgnore
    public String getInterviewerIdText() {
        if (interviewerIds == null || interviewerIds.length == 0) {
            return null;
        }
        return Arrays.stream(interviewerIds)
            .filter(Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    /**
     * 面试状态中文标签。
     *
     * <p>设计文档 §10 未为面试状态定义字典类型，这里按稳定编码兜底转换，保证页面始终有中文可展示。</p>
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getStatusLabel() {
        return status == null ? null : STATUS_LABELS.get(status);
    }

}
