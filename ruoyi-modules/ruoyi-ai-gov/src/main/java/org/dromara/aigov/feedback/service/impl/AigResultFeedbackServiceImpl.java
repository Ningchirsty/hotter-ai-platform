package org.dromara.aigov.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.feedback.domain.AigResultFeedback;
import org.dromara.aigov.feedback.domain.bo.AigFeedbackHandleBo;
import org.dromara.aigov.feedback.domain.bo.AigResultFeedbackBo;
import org.dromara.aigov.feedback.enums.AigFeedbackActionEnum;
import org.dromara.aigov.feedback.enums.AigFeedbackReasonEnum;
import org.dromara.aigov.feedback.enums.AigFeedbackVerdictEnum;
import org.dromara.aigov.feedback.mapper.AigResultFeedbackMapper;
import org.dromara.aigov.feedback.service.IAigResultFeedbackService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结果反馈实现（设计 §6.9）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigResultFeedbackServiceImpl implements IAigResultFeedbackService {

    private static final String STATUS_NORMAL = "0";

    private final AigResultFeedbackMapper feedbackMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(AigResultFeedbackBo bo, Long reporterId) {
        if (bo == null) {
            throw new ServiceException("反馈入参不能为空");
        }
        AigFeedbackReasonEnum reason = AigFeedbackReasonEnum.find(bo.getReasonCode());
        if (reason == null) {
            throw new ServiceException("非法的反馈原因：" + bo.getReasonCode()
                + "（只能是 USEFUL/FACT_ERROR/CITATION_MISMATCH/MISSING_SOURCE/FORMAT_MISMATCH/FLOW_INTERRUPTED）");
        }
        if (bo.getResultId() == null && bo.getTaskId() == null) {
            // 反馈要能定位到"哪次运行/哪条结果"，否则只是一条无法处理的感受
            throw new ServiceException("反馈必须关联任务或结果：缺一条就无法定位到具体运行");
        }
        AigResultFeedback feedback = new AigResultFeedback();
        feedback.setTaskId(bo.getTaskId());
        feedback.setResultId(bo.getResultId());
        feedback.setAgentVersionId(bo.getAgentVersionId());
        feedback.setKnowledgeVersionId(bo.getKnowledgeVersionId());
        feedback.setReasonCode(reason.getCode());
        feedback.setVerdict(AigFeedbackVerdictEnum.PENDING.getCode());
        feedback.setDetailRef(bo.getDetailRef());
        feedback.setReporterId(reporterId);
        feedback.setStatus(STATUS_NORMAL);
        feedback.setDelFlag(STATUS_NORMAL);
        feedbackMapper.insert(feedback);
        log.info("提交结果反馈, feedbackId={}, taskId={}, resultId={}, reason={}",
            feedback.getFeedbackId(), bo.getTaskId(), bo.getResultId(), reason.getCode());
        return feedback.getFeedbackId();
    }

    @Override
    public List<AigResultFeedback> listQueue(String verdict) {
        return listQueue(verdict, null, null);
    }

    @Override
    public List<AigResultFeedback> listQueue(String verdict, String actionCode, String reasonCode) {
        return feedbackMapper.selectList(queueWrapper(verdict, actionCode, reasonCode));
    }

    @Override
    public PageResult<AigResultFeedback> queryQueuePage(String verdict, String actionCode, String reasonCode,
                                                       PageQuery pageQuery) {
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        Page<AigResultFeedback> page = feedbackMapper.selectPage(query.build(),
            queueWrapper(verdict, actionCode, reasonCode));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 队列的过滤条件（**唯一一处**，全量与分页共用）。
     *
     * <p>抽出来的理由：分页版与全量版若各写一份过滤，迟早会出现"翻到第 2 页看到的行
     * 与第 1 页的筛选口径不一致"这类看不见的错。</p>
     *
     * <p>过滤条件一律走枚举校验：拼错的编码若被当成"没有匹配"，页面会显示成"队列是空的"——
     * 那是"没有问题要处理"的意思，与"你筛错了"完全不同。</p>
     *
     * @param verdict    处理结论过滤（可空=默认 PENDING）
     * @param actionCode 后续动作过滤（可空=不限）
     * @param reasonCode 反馈原因过滤（可空=不限）
     * @return 查询条件
     */
    private LambdaQueryWrapper<AigResultFeedback> queueWrapper(String verdict, String actionCode,
                                                               String reasonCode) {
        String wanted = StringUtils.isBlank(verdict)
            ? AigFeedbackVerdictEnum.PENDING.getCode() : verdict.trim();
        if (AigFeedbackVerdictEnum.find(wanted) == null) {
            throw new ServiceException("非法的处理结论过滤：" + verdict);
        }
        AigFeedbackActionEnum action = null;
        if (StringUtils.isNotBlank(actionCode)) {
            action = AigFeedbackActionEnum.find(actionCode);
            if (action == null) {
                throw new ServiceException("非法的处理动作过滤：" + actionCode
                    + "（只能是 ADD_KNOWLEDGE/CHANGE_RULE/ADD_CASE/CLOSE）");
            }
        }
        AigFeedbackReasonEnum reason = null;
        if (StringUtils.isNotBlank(reasonCode)) {
            reason = AigFeedbackReasonEnum.find(reasonCode);
            if (reason == null) {
                throw new ServiceException("非法的反馈原因过滤：" + reasonCode);
            }
        }
        return new LambdaQueryWrapper<AigResultFeedback>()
            .eq(AigResultFeedback::getVerdict, wanted)
            .eq(action != null, AigResultFeedback::getActionCode,
                action == null ? null : action.getCode())
            .eq(reason != null, AigResultFeedback::getReasonCode,
                reason == null ? null : reason.getCode())
            .eq(AigResultFeedback::getStatus, STATUS_NORMAL)
            .orderByDesc(AigResultFeedback::getCreateTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigResultFeedback handle(Long feedbackId, AigFeedbackHandleBo bo, Long actorId) {
        if (feedbackId == null) {
            throw new ServiceException("反馈ID不能为空");
        }
        AigResultFeedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new ServiceException("反馈不存在或已删除：" + feedbackId);
        }
        AigFeedbackVerdictEnum verdict = bo == null ? null : AigFeedbackVerdictEnum.find(bo.getVerdict());
        if (verdict == null) {
            throw new ServiceException("非法的处理结论：" + (bo == null ? null : bo.getVerdict()));
        }
        if (verdict == AigFeedbackVerdictEnum.PENDING) {
            throw new ServiceException("处理结论必须是 CONFIRMED 或 REJECTED（PENDING 是未处理态）");
        }
        if (verdict == AigFeedbackVerdictEnum.CONFIRMED) {
            // 确认问题必须给出后续动作：否则问题被"确认"了却没人接着处理
            AigFeedbackActionEnum action = AigFeedbackActionEnum.find(bo.getActionCode());
            if (action == null) {
                throw new ServiceException("确认问题时必须给出处理动作（补知识/改规则/加入用例/关闭）");
            }
            feedback.setActionCode(action.getCode());
        } else {
            feedback.setActionCode(null);
        }
        feedback.setVerdict(verdict.getCode());
        feedback.setHandledBy(actorId);
        feedback.setHandledAt(LocalDateTime.now());
        if (bo != null && StringUtils.isNotBlank(bo.getRemark())) {
            feedback.setRemark(StringUtils.substring(bo.getRemark(), 0, 500));
        }
        feedbackMapper.updateById(feedback);
        log.info("处理结果反馈, feedbackId={}, verdict={}, action={}, handledBy={}",
            feedbackId, verdict.getCode(), feedback.getActionCode(), actorId);
        return feedback;
    }

}
