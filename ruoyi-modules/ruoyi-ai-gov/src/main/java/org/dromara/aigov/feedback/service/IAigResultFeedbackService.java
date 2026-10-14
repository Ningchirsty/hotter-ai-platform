package org.dromara.aigov.feedback.service;

import org.dromara.aigov.feedback.domain.AigResultFeedback;
import org.dromara.aigov.feedback.domain.bo.AigFeedbackHandleBo;
import org.dromara.aigov.feedback.domain.bo.AigResultFeedbackBo;

import java.util.List;

/**
 * 结果反馈服务（设计 §6.9）。
 *
 * <p><b>采纳反馈不直接改线上版本</b>：反馈进入问题队列，处理动作（补知识/改规则/加用例）
 * 分别落到知识中心、草稿与评测用例，再经既有发布门槛才会生效。</p>
 *
 * @author ai-gov
 */
public interface IAigResultFeedbackService {

    /**
     * 提交结果反馈（进入问题队列，结论为 {@code PENDING}）。
     *
     * @param bo         入参
     * @param reporterId 反馈人
     * @return 反馈ID
     */
    Long submit(AigResultFeedbackBo bo, Long reporterId);

    /**
     * 问题队列（只按结论过滤）。
     *
     * @param verdict 处理结论过滤（可空=默认 PENDING）
     * @return 反馈清单
     */
    List<AigResultFeedback> listQueue(String verdict);

    /**
     * 问题队列（按结论 + 后续动作 + 反馈原因过滤）。
     *
     * <p><b>为什么必须能按"动作"查</b>：确认问题时要选一个后续动作（补知识/改规则/加入用例/关闭），
     * 但动作只是写在反馈行上——没有按动作过滤，负责用例的人就没法问出
     * "哪些问题是要我加入用例的"，只能把整条队列从头翻一遍。
     * 队列因此退化成"收件箱"，而收件箱是会被放弃的。</p>
     *
     * <p>按原因过滤同理：资料缺失要去补知识、事实有误多半要改规则，
     * 两个方向本来就该各自看到自己那一份。</p>
     *
     * @param verdict    处理结论过滤（可空=默认 PENDING）
     * @param actionCode 后续动作过滤（可空=不限；PENDING 行没有动作，因此该过滤只在 CONFIRMED 下有意义）
     * @param reasonCode 反馈原因过滤（可空=不限）
     * @return 反馈清单
     */
    List<AigResultFeedback> listQueue(String verdict, String actionCode, String reasonCode);

    /**
     * 处理一条反馈。
     *
     * @param feedbackId 反馈ID
     * @param bo         入参
     * @param actorId    处理人
     * @return 处理后的反馈
     */
    AigResultFeedback handle(Long feedbackId, AigFeedbackHandleBo bo, Long actorId);

}
