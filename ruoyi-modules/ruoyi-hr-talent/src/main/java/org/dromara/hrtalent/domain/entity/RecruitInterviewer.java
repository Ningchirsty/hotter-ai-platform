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
 * 面试参与人对象 hr_recruit_interviewer。
 * <p>一条记录表示某场面试的一名面试官；一人或多人在本表逐行保存（设计文档 §7.3 第 6 条：
 * 多人面试分别保存个人意见）。字段严格对齐 {@code script/sql/hr_recruit.sql}，
 * 主键列为 {@code interviewer_id}。</p>
 *
 * <p><b>反馈一致性</b>：{@code feedback_status} / {@code feedback_time} 必须与
 * {@code feedback} 写入保持一致，服务层在提交个人反馈时同步更新。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_interviewer")
public class RecruitInterviewer extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试参与人ID（主键）
     */
    @TableId(value = "interviewer_id")
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
     * 面试官姓名快照
     */
    private String userName;

    /**
     * 面试官角色（lead主面/assist协同/hr/hr等稳定编码）
     */
    private String interviewerRole;

    /**
     * 反馈状态（pending/submitted/waived 等稳定编码）
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
     * 面试官个人意见/结论（每人分别保存）
     */
    private String feedback;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
