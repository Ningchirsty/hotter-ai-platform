package org.dromara.hrtalent.domain.vo.recruitment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.converter.AssistantIdsConverter;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.enums.JobStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 招聘岗位执行项视图对象 hr_recruit_job。
 * <p>岗位数据不含电话、身份证明细等敏感字段，可安全用于列表返回。
 * 字典标签与用户昵称统一用 {@code @Translation} 回填（全仓惯例）；
 * 岗位状态在设计文档 §10 未定义字典组，改由 {@link JobStatusEnum} 兜底转换。</p>
 *
 * <p><b>面试官说明</b>：{@link #firstInterviewerId} / {@link #secondInterviewerId} 是岗位上的
 * 计划默认值；每轮面试的实际面试官归属 {@code hr_recruit_interviewer}（按面试记录关联，后续阶段实现）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitJob.class, uses = AssistantIdsConverter.class)
public class RecruitJobVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位执行项ID
     */
    private Long jobId;

    /**
     * 岗位编号（业务编号，唯一）
     */
    private String jobNo;

    /**
     * 来源招聘需求ID
     */
    private Long demandId;

    /**
     * 关联月度计划任务ID
     */
    private Long planItemId;

    /**
     * 岗位名称
     */
    private String jobName;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 用工部门名称快照
     */
    private String useDeptName;

    /**
     * 岗位职级（字典编码）
     */
    private String jobLevel;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 岗位职责
     */
    private String responsibility;

    /**
     * 任职要求
     */
    private String qualification;

    /**
     * 薪资低值
     */
    private BigDecimal salaryMin;

    /**
     * 薪资高值
     */
    private BigDecimal salaryMax;

    /**
     * 薪资周期（month/year/day 等稳定编码）
     */
    private String salaryPeriod;

    /**
     * 招聘形式（字典 recruit_mode 编码）
     */
    private String recruitMode;

    /**
     * 招聘形式标签（字典 recruit_mode）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "recruitMode", other = "recruit_mode")
    private String recruitModeLabel;

    /**
     * 紧急程度（字典 recruit_urgency 编码）
     */
    private String urgency;

    /**
     * 紧急程度标签（字典 recruit_urgency）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "urgency", other = "recruit_urgency")
    private String urgencyLabel;

    /**
     * 是否需要猎头（0否 1是）
     */
    private String headhunterFlag;

    /**
     * 协助人用户ID数组（由 {@code assistant_ids} 逗号串拆回，前端多选组件直接绑定）
     */
    private Long[] assistantIds;

    /**
     * 协助人昵称，多个以英文逗号分隔（由 {@link #getAssistantIdText()} 批量翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "assistantIdText")
    private String assistantNames;

    /**
     * 一面面试官用户ID（岗位计划默认值）
     */
    private Long firstInterviewerId;

    /**
     * 一面面试官昵称（由 {@link #firstInterviewerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "firstInterviewerId")
    private String firstInterviewerName;

    /**
     * 二面面试官用户ID（岗位计划默认值）
     */
    private Long secondInterviewerId;

    /**
     * 二面面试官昵称（由 {@link #secondInterviewerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "secondInterviewerId")
    private String secondInterviewerName;

    /**
     * 招聘期限标准天数
     */
    private Integer standardDays;

    /**
     * 预计到岗日期
     */
    private LocalDate expectArrivalDate;

    /**
     * 岗位招聘人数
     */
    private Integer recruitCount;

    /**
     * 招聘负责人用户ID
     */
    private Long ownerId;

    /**
     * 招聘负责人昵称（由 {@link #ownerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "ownerId")
    private String ownerName;

    /**
     * 发布日期
     */
    private LocalDate publishDate;

    /**
     * 关闭日期
     */
    private LocalDate closeDate;

    /**
     * 岗位状态（draft/open/paused/closed）
     */
    private String status;

    /**
     * 乐观锁版本号（编辑提交时必须回传）
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

    /**
     * 协助人用户ID入库原串（英文逗号分隔）。
     *
     * <p>不是数据库直出字段，而是由 {@link #assistantIds} 组合得到的<b>只读</b>属性：
     * 一方面作为 {@link #assistantNames} 翻译的取数来源（翻译处理器按 getter 取值），
     * 另一方面用 {@code @JsonIgnore} 避免把原始 ID 串重复输出给前端。</p>
     *
     * @return 逗号分隔的用户ID串，无协助人时返回 null
     */
    @JsonIgnore
    public String getAssistantIdText() {
        if (assistantIds == null || assistantIds.length == 0) {
            return null;
        }
        return Arrays.stream(assistantIds)
            .filter(Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    /**
     * 岗位状态中文标签。
     *
     * <p>设计文档 §10 未为岗位状态定义字典类型（仅需求/计划等状态有字典），
     * 因此这里不使用 {@code @Translation} 凭空引用一个不存在的字典组，
     * 而是由 {@link JobStatusEnum} 的稳定编码直接兜底转换，保证页面始终有中文可展示。</p>
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getStatusLabel() {
        return JobStatusEnum.labelOf(status);
    }

}
