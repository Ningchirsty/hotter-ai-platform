package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试记录对象 hr_recruit_interview。
 * <p>一条记录表示某条应聘记录在某一轮次（一面/二面/扩展轮次）的一次面试安排，
 * 字段严格对齐 {@code script/sql/hr_recruit.sql} 的建表语句，主键列为 {@code interview_id}。</p>
 *
 * <p><b>多人面试</b>：面试官归属 {@link RecruitInterviewer}（按 {@code interview_id} 关联），
 * 个人意见保存在 {@code hr_recruit_interviewer.feedback}；本实体只保存汇总结论、评分与意见。</p>
 *
 * <p><b>改期与取消</b>（设计文档 §7.3、§21.13）：面试表没有操作日志表与乐观锁列，
 * 改期采用「原记录置 {@code rescheduled} 保留 + 新增一条有效面试记录」表达，
 * 取消通过 {@code status=cancelled} 与 {@code cancel_reason} 表达。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_interview")
public class RecruitInterview extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试记录ID（主键）
     */
    @TableId(value = "interview_id")
    private Long interviewId;

    /**
     * 应聘记录ID
     */
    private Long applicationId;

    /**
     * 面试轮次（非负整数，从 1 开始：1 一面、2 二面、3 及以后为扩展轮次）
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
     * 面试方式（onsite现场/video视频/phone电话等稳定编码）
     */
    private String method;

    /**
     * 面试地点或线上链接
     */
    private String location;

    /**
     * 面试状态（pending/scheduled/finished/cancelled/rescheduled 等稳定编码）
     */
    private String status;

    /**
     * 面试评分（汇总结论评分）
     */
    private BigDecimal score;

    /**
     * 面试结果（pending/pass/fail/reserve/absent，字典 recruit_interview_result）
     */
    private String result;

    /**
     * 面试汇总意见
     */
    private String feedback;

    /**
     * 反馈时间
     */
    private LocalDateTime feedbackTime;

    /**
     * 取消原因（status=cancelled 时填写）
     */
    private String cancelReason;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注；改期/取消的操作留痕以追加文本形式保存在此列
     */
    private String remark;

}
