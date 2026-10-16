package org.dromara.aigov.feedback.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.feedback.domain.AigResultFeedback;
import org.dromara.aigov.feedback.domain.bo.AigFeedbackHandleBo;
import org.dromara.aigov.feedback.domain.bo.AigResultFeedbackBo;
import org.dromara.aigov.feedback.service.IAigResultFeedbackService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 结果反馈接口（设计 §6.9）。
 *
 * <h3>提交与处理刻意用不同的门</h3>
 * <ul>
 *     <li><b>提交</b>：{@code @SaCheckLogin}。反馈是员工动作，不该再要求一个权限点——
 *         加权限点只会让"漏勾了就不能提反馈"，而反馈收集最怕的就是入口太高；</li>
 *     <li><b>看队列 / 处理</b>：{@code aig:feedback:queue} / {@code aig:feedback:handle}。
 *         这两件事是训练负责人的职责，且处理会改变"问题怎么改"的去向，必须显式授权。</li>
 * </ul>
 *
 * <p>处理动作（补知识/改规则/加用例）都只是把问题**送进改进流程**，不会直接改线上版本
 * （设计 §6.9）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/feedback")
public class AigResultFeedbackController extends BaseController {

    private final IAigResultFeedbackService feedbackService;

    /**
     * 提交结果反馈（进入问题队列）。
     *
     * @param bo 入参
     * @return 反馈ID
     */
    @SaCheckLogin
    @RepeatSubmit
    @PostMapping
    public R<Long> submit(@RequestBody @Validated AigResultFeedbackBo bo) {
        return R.ok(feedbackService.submit(bo, LoginHelper.getUserId()));
    }

    /**
     * 问题队列（可按结论 / 后续动作 / 反馈原因过滤）。
     *
     * <p>按动作过滤是给"接着处理的人"用的：负责用例的人要能问出"哪些问题是要我加入用例的"，
     * 而不是把整条队列从头翻一遍。</p>
     *
     * @param verdict    处理结论过滤（可空=默认 PENDING）
     * @param actionCode 后续动作过滤（可空=不限；PENDING 行没有动作）
     * @param reasonCode 反馈原因过滤（可空=不限）
     * @return 反馈清单
     */
    @SaCheckPermission(AigConstants.PERM_FEEDBACK_QUEUE)
    @GetMapping("/queue")
    public R<List<AigResultFeedback>> queue(@RequestParam(required = false) String verdict,
                                            @RequestParam(required = false) String actionCode,
                                            @RequestParam(required = false) String reasonCode) {
        return R.ok(feedbackService.listQueue(verdict, actionCode, reasonCode));
    }

    /**
     * 问题队列（**分页**；页面走这个）。
     *
     * <p>过滤参数与 {@code /queue} 完全一致（服务端共用同一份条件构造），只是多带分页参数。
     * 队列是"收件箱"、行数只增不减，因此页面不该再全量拉取。</p>
     *
     * @param verdict    处理结论过滤（可空=默认 PENDING）
     * @param actionCode 后续动作过滤（可空=不限）
     * @param reasonCode 反馈原因过滤（可空=不限）
     * @param pageQuery  分页参数
     * @return 分页结果
     */
    @SaCheckPermission(AigConstants.PERM_FEEDBACK_QUEUE)
    @GetMapping("/queue/page")
    public R<PageResult<AigResultFeedback>> queuePage(@RequestParam(required = false) String verdict,
                                                      @RequestParam(required = false) String actionCode,
                                                      @RequestParam(required = false) String reasonCode,
                                                      PageQuery pageQuery) {
        return R.ok(feedbackService.queryQueuePage(verdict, actionCode, reasonCode, pageQuery));
    }

    /**
     * 处理一条反馈（确认问题需给出动作；驳回清空动作）。
     *
     * @param feedbackId 反馈ID
     * @param bo         入参
     * @return 处理后的反馈
     */
    @SaCheckPermission(AigConstants.PERM_FEEDBACK_HANDLE)
    @RepeatSubmit
    @PostMapping("/{feedbackId:\\d+}/handle")
    public R<AigResultFeedback> handle(
        @NotNull(message = "反馈ID不能为空") @PathVariable Long feedbackId,
        @RequestBody @Validated AigFeedbackHandleBo bo) {
        return R.ok(feedbackService.handle(feedbackId, bo, LoginHelper.getUserId()));
    }

}
