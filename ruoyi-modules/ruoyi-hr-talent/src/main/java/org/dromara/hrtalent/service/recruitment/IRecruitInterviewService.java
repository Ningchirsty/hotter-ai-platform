package org.dromara.hrtalent.service.recruitment;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewCancelBo;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewFeedbackBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewQueryBo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewVo;

import java.util.List;

/**
 * 面试服务接口（SPEC-P3 §2.3 / §3.3，设计文档 §7.3、§8.6）。
 *
 * <p><b>职责边界</b>：本服务只维护 {@code hr_recruit_interview} 与
 * {@code hr_recruit_interviewer}；应聘阶段流转与月度计划任务状态刷新由其它服务消费
 * {@code InterviewResultChangedEvent} 完成，本服务<b>不写</b>
 * {@code hr_recruit_application} 与 {@code hr_recruit_plan_item}。</p>
 *
 * @author hr-talent
 */
public interface IRecruitInterviewService {

    /**
     * 分页查询面试记录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 面试记录分页结果（含面试官明细与候选人摘要）
     */
    PageResult<RecruitInterviewVo> queryPage(RecruitInterviewQueryBo bo, PageQuery pageQuery);

    /**
     * 获取面试详情。
     *
     * @param interviewId 面试记录ID
     * @return 面试详情（含各面试官个人意见）
     */
    RecruitInterviewVo getDetail(Long interviewId);

    /**
     * 安排面试（一面/二面/扩展轮次）。
     * <p>同一应聘记录同一轮次只允许一条有效面试记录；面试官一人或多人逐行落库。</p>
     *
     * @param bo 面试入参
     * @return 新增的面试记录ID
     */
    Long schedule(RecruitInterviewBo bo);

    /**
     * 面试改期（保留操作记录）。
     * <p>原面试记录置为 {@code rescheduled} 并追加改期原因作留痕，同时新增一条有效面试记录
     * （设计文档 §7.3 第 7 条、§21.13「改期使用状态和历史表达」）。</p>
     *
     * @param bo 改期入参，只有面试记录ID必填，其余缺省项沿用原记录
     * @return 改期后新的面试记录ID
     */
    Long reschedule(RecruitInterviewBo bo);

    /**
     * 取消面试（必须填写原因）。
     * <p>取消后该场面试的待反馈面试官统一置为 {@code waived}，不再产生待办。</p>
     *
     * @param bo 取消入参
     */
    void cancel(InterviewCancelBo bo);

    /**
     * 面试官提交个人反馈；可选同时提交汇总结论。
     * <p>个人评分/意见写入 {@code hr_recruit_interviewer}；提供汇总结论时同步写入
     * {@code hr_recruit_interview} 的 {@code result}/{@code score}/{@code feedback}
     * 并发布 {@code InterviewResultChangedEvent}。</p>
     *
     * @param bo 反馈入参
     */
    void feedback(InterviewFeedbackBo bo);

    /**
     * 面试官待办：当前登录用户作为面试官且尚未反馈的面试，按计划面试时间升序。
     *
     * @return 待办面试列表
     */
    List<RecruitInterviewVo> myTodos();

}
