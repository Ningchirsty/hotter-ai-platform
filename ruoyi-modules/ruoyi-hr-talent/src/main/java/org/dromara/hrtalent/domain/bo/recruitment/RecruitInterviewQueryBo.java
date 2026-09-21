package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试记录查询业务对象。
 * <p>只承载列表检索条件，不承载写字段；分页与排序由 {@link PageQuery} 提供。
 * 候选人摘要由服务层只读关联应聘记录带出，不在本对象内按姓名过滤，
 * 避免绕过人才可见范围（设计文档 §21.14）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitInterviewQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应聘记录ID（精确匹配）
     */
    private Long applicationId;

    /**
     * 面试轮次（精确匹配）
     */
    private Integer roundNo;

    /**
     * 面试状态（pending/scheduled/finished/cancelled/rescheduled，精确匹配）
     */
    private String status;

    /**
     * 面试结果（字典 recruit_interview_result 编码，精确匹配）
     */
    private String result;

    /**
     * 面试方式（onsite/video/phone 等稳定编码，精确匹配）
     */
    private String method;

    /**
     * 面试官用户ID（筛选其参与的面试）
     */
    private Long interviewerUserId;

    /**
     * 计划面试时间起（含）
     */
    private LocalDateTime scheduleTimeBegin;

    /**
     * 计划面试时间止（含）
     */
    private LocalDateTime scheduleTimeEnd;

}
