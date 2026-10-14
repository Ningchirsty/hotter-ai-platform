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
     * 问题队列。
     *
     * @param verdict 处理结论过滤（可空=默认 PENDING）
     * @return 反馈清单
     */
    List<AigResultFeedback> listQueue(String verdict);

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
