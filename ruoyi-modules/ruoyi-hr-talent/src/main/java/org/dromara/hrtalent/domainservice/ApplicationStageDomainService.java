package org.dromara.hrtalent.domainservice;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.hrtalent.enums.ApplicationResultEnum;
import org.dromara.hrtalent.enums.BackgroundResultEnum;
import org.dromara.hrtalent.enums.CandidateStageEnum;
import org.dromara.hrtalent.enums.InterviewResultEnum;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 应聘阶段机领域服务（SPEC-P3 §3.2 / 设计文档 §7.2、§7.5、§9.6）。
 *
 * <p><b>职责边界</b>：本服务只做<b>纯规则计算与校验</b>，不访问数据库、不读登录态、不写任何表。
 * 「面试安排 / 一面结论 / 最终面试结论 / 背调结论」等事实由调用方以 {@link StageFacts}
 * 传入，因此全部方法都可以脱离 Spring 容器做单元测试（这是 SPEC-P3 §5 对阶段校验单测的要求）。</p>
 *
 * <p><b>阶段顺序</b>（§7.2）：
 * {@code 新建 → 简历评审 → 待邀约 → 一面 → 二面 → 待背调 → 待录用 → 待报到 → 已报到}；
 * 任一业务阶段可转入 {@code 淘汰 / 候选人放弃 / 暂缓 / 人才保留}。
 * 前九个是「阶段」（{@link CandidateStageEnum}），后四个是「结果」（{@link ApplicationResultEnum}），
 * 转入后四个时不改变 {@code current_stage}，只改变 {@code current_status}。</p>
 *
 * <p><b>六条跳转前置校验</b>（§7.2，逐条实现）：</p>
 * <ol>
 *     <li>进入一面 → 必须存在面试安排；</li>
 *     <li>进入二面 → 必须有一面结论；</li>
 *     <li>进入待背调 → 必须有最终面试结论；</li>
 *     <li>进入待录用 → 必须有背调结论或经授权的免背调原因；</li>
 *     <li>进入待报到 → 必须记录邀约结果与计划报到日期；</li>
 *     <li>管理员例外跳转 → 必须填写原因（由调用方写审计）。</li>
 * </ol>
 *
 * <p><b>例外跳转语义</b>：{@code override = true} 表示管理员例外跳转，此时允许跳过前置校验、
 * 也允许阶段回退，但<b>必须</b>填写原因；调用方必须调用
 * {@code SensitiveAuditRecorder} 以 {@code stage_override} 事件写审计。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
public class ApplicationStageDomainService {

    /**
     * 操作动作：阶段前进。
     */
    public static final String ACTION_MOVE = "move";

    /**
     * 操作动作：登记实际报到。
     */
    public static final String ACTION_ARRIVE = "arrive";

    /**
     * 操作动作：淘汰。
     */
    public static final String ACTION_REJECT = "reject";

    /**
     * 操作动作：候选人放弃。
     */
    public static final String ACTION_WITHDRAW = "withdraw";

    /**
     * 操作动作：暂缓。
     */
    public static final String ACTION_PAUSE = "pause";

    /**
     * 操作动作：人才保留（转入人才池）。
     */
    public static final String ACTION_TALENT_POOL = "talent_pool";

    /**
     * 阶段跳转事实（由调用方只读查询面试/背调等其它域后组装）。
     *
     * @param interviewScheduled     是否存在有效的面试安排（未取消的面试记录）
     * @param firstInterviewConcluded 是否存在一面结论（第一轮面试已给出非 pending 的结论）
     * @param finalInterviewConcluded 是否存在最终面试结论（最高轮次面试已给出非 pending 的结论）
     * @param backgroundConcluded    是否存在背调结论（result 为 pass/fail 等已结论值）
     * @param backgroundWaivedWithReason 是否已豁免背调且填写了授权原因
     * @param offerResult            邀约结果编码
     * @param planArrivalDate        计划报到日期
     * @author hr-talent
     */
    public record StageFacts(boolean interviewScheduled,
                             boolean firstInterviewConcluded,
                             boolean finalInterviewConcluded,
                             boolean backgroundConcluded,
                             boolean backgroundWaivedWithReason,
                             String offerResult,
                             LocalDate planArrivalDate) {

        /**
         * 空事实：所有前置条件均不满足，仅用于「简历评审 / 待邀约」等无前置校验的阶段。
         *
         * @return 空事实
         */
        public static StageFacts none() {
            return new StageFacts(false, false, false, false, false, null, null);
        }
    }

    /**
     * 流转计划（纯计算结果，供服务实现落库使用）。
     *
     * @param actionType 操作动作编码
     * @param fromStage  原阶段
     * @param toStage    目标阶段（转入结果时与 fromStage 相同）
     * @param result     目标应聘结果
     * @author hr-talent
     */
    public record TransitionPlan(String actionType,
                                 CandidateStageEnum fromStage,
                                 CandidateStageEnum toStage,
                                 ApplicationResultEnum result) {
    }

    /* ------------------------------------------------------------------ 编码解析 ------------------------------------------------------------------ */

    /**
     * 解析阶段编码，未知编码一律按非法处理（fail-safe）。
     *
     * @param code 阶段编码
     * @return 阶段枚举
     */
    public CandidateStageEnum requireStage(String code) {
        CandidateStageEnum stage = CandidateStageEnum.find(code);
        if (stage == null) {
            throw new ServiceException("未知的应聘阶段：" + code);
        }
        return stage;
    }

    /**
     * 解析应聘结果编码，未知编码一律按非法处理（fail-safe）。
     *
     * @param code 结果编码
     * @return 结果枚举
     */
    public ApplicationResultEnum requireResult(String code) {
        ApplicationResultEnum result = ApplicationResultEnum.find(code);
        if (result == null) {
            throw new ServiceException("未知的应聘结果：" + code);
        }
        return result;
    }

    /* ------------------------------------------------------------------ 阶段顺序 ------------------------------------------------------------------ */

    /**
     * 目标阶段是否严格位于当前阶段之后（§7.2 阶段顺序）。
     *
     * @param from 当前阶段
     * @param to   目标阶段
     * @return 是否为向前流转
     */
    public boolean isForward(CandidateStageEnum from, CandidateStageEnum to) {
        return from != null && to != null && to.ordinal() > from.ordinal();
    }

    /**
     * 应聘是否已经结束（已通过并报到 / 已淘汰 / 已放弃），结束后不允许继续流转。
     *
     * @param currentStatus 当前应聘结果编码
     * @return 是否已结束
     */
    public boolean isEnded(String currentStatus) {
        ApplicationResultEnum result = ApplicationResultEnum.find(currentStatus);
        if (result == null) {
            return false;
        }
        return switch (result) {
            case PASSED, REJECTED, WITHDRAWN -> true;
            case PROCESSING, PAUSED, TALENT_POOL -> false;
        };
    }

    /**
     * 校验应聘未结束，已结束时给出中文提示。
     *
     * @param currentStatus 当前应聘结果编码
     */
    public void validateNotEnded(String currentStatus) {
        ApplicationResultEnum result = ApplicationResultEnum.find(currentStatus);
        if (result != null && isEnded(currentStatus)) {
            throw new ServiceException("本次应聘已结束（" + result.getDesc() + "），不能继续流转阶段");
        }
    }

    /* ------------------------------------------------------------------ 阶段前进 ------------------------------------------------------------------ */

    /**
     * 规划一次「阶段前进」流转并执行六条前置校验（§7.2）。
     *
     * <p>前置校验只在<b>非例外</b>跳转时生效；{@code override = true} 时允许跳过校验与阶段回退，
     * 但必须填写原因（第 6 条）。</p>
     *
     * @param from           当前阶段
     * @param to             目标阶段
     * @param override       是否为管理员例外跳转
     * @param overrideReason 例外跳转原因
     * @param facts          阶段跳转事实
     * @return 流转计划
     */
    public TransitionPlan planMove(CandidateStageEnum from,
                                   CandidateStageEnum to,
                                   boolean override,
                                   String overrideReason,
                                   StageFacts facts) {
        if (from == null || to == null) {
            throw new ServiceException("原阶段与目标阶段不能为空");
        }
        // 「已报到」必须走报到接口：报到要在同一事务内累加计划任务到岗人数并刷新状态（§7.4/§21.7）
        if (CandidateStageEnum.ARRIVED == to) {
            throw new ServiceException("登记实际报到请使用报到接口，系统需同步计入月度计划任务");
        }
        if (CandidateStageEnum.ARRIVED == from) {
            throw new ServiceException("候选人已报到，不能再次流转阶段");
        }
        if (from == to) {
            throw new ServiceException("目标阶段与当前阶段相同，无需流转");
        }
        // 第 6 条：管理员例外跳转必须填写原因
        if (override && StringUtils.isBlank(overrideReason)) {
            throw new ServiceException("管理员例外跳转必须填写原因");
        }
        if (!override) {
            if (!isForward(from, to)) {
                throw new ServiceException("应聘阶段只能向前流转，不允许从「" + from.getDesc()
                    + "」回退到「" + to.getDesc() + "」；如需例外跳转请由管理员填写原因后执行");
            }
            validatePrerequisites(to, facts == null ? StageFacts.none() : facts);
        }
        log.debug("应聘阶段前进校验通过, from={}, to={}, override={}", from.getCode(), to.getCode(), override);
        return new TransitionPlan(ACTION_MOVE, from, to, ApplicationResultEnum.PROCESSING);
    }

    /**
     * 逐条执行进入目标阶段的前置校验（§7.2 第 1~5 条）。
     *
     * @param to    目标阶段
     * @param facts 阶段跳转事实
     */
    public void validatePrerequisites(CandidateStageEnum to, StageFacts facts) {
        switch (to) {
            // 第 1 条：进入一面前必须存在面试安排
            case FIRST_INTERVIEW -> {
                if (!facts.interviewScheduled()) {
                    throw new ServiceException("进入「一面」前必须先安排面试");
                }
            }
            // 第 2 条：进入二面前必须有一面结论
            case SECOND_INTERVIEW -> {
                if (!facts.firstInterviewConcluded()) {
                    throw new ServiceException("进入「二面」前必须先录入一面结论");
                }
            }
            // 第 3 条：进入待背调前必须有最终面试结论
            case BACKGROUND -> {
                if (!facts.finalInterviewConcluded()) {
                    throw new ServiceException("进入「待背调」前必须先录入最终面试结论");
                }
            }
            // 第 4 条：进入待录用前必须有背调结论或经授权的免背调原因
            case OFFER -> {
                if (!facts.backgroundConcluded() && !facts.backgroundWaivedWithReason()) {
                    throw new ServiceException("进入「待录用」前必须有背调结论，或填写经授权的免背调原因");
                }
            }
            // 第 5 条：进入待报到前必须记录邀约结果与计划报到日期
            case PENDING_ARRIVAL -> {
                if (StringUtils.isBlank(facts.offerResult())) {
                    throw new ServiceException("进入「待报到」前必须记录邀约结果");
                }
                if (facts.planArrivalDate() == null) {
                    throw new ServiceException("进入「待报到」前必须记录计划报到日期");
                }
            }
            // 进入新建 / 简历评审 / 待邀约无额外前置资料要求
            default -> {
            }
        }
    }

    /* ------------------------------------------------------------------ 结果流转 ------------------------------------------------------------------ */

    /**
     * 规划一次「结果流转」：转入淘汰 / 候选人放弃 / 暂缓 / 人才保留（§7.2）。
     *
     * <p>转入结果时 {@code current_stage} 不变，只变更 {@code current_status}；
     * 淘汰与候选人放弃属于流程终点，<b>必须</b>填写原因（§7.6.2、§8.5）。</p>
     *
     * @param current    当前阶段
     * @param result     目标应聘结果
     * @param reasonCode 原因编码
     * @param comment    阶段说明
     * @return 流转计划
     */
    public TransitionPlan planOutcome(CandidateStageEnum current,
                                      ApplicationResultEnum result,
                                      String reasonCode,
                                      String comment) {
        if (current == null) {
            throw new ServiceException("原阶段不能为空");
        }
        if (result == null) {
            throw new ServiceException("目标应聘结果不能为空");
        }
        String actionType = switch (result) {
            case REJECTED -> ACTION_REJECT;
            case WITHDRAWN -> ACTION_WITHDRAW;
            case PAUSED -> ACTION_PAUSE;
            case TALENT_POOL -> ACTION_TALENT_POOL;
            // processing/passed 不是「转入结果」，只能由阶段前进或报到接口产生
            case PROCESSING, PASSED -> throw new ServiceException("不支持的应聘结果流转：" + result.getCode()
                + "，可选值为 淘汰/候选人放弃/暂缓/人才保留");
        };
        boolean reasonGiven = StringUtils.isNotBlank(reasonCode) || StringUtils.isNotBlank(comment);
        if (ApplicationResultEnum.REJECTED == result && !reasonGiven) {
            throw new ServiceException("淘汰候选人必须填写原因");
        }
        if (ApplicationResultEnum.WITHDRAWN == result && !reasonGiven) {
            throw new ServiceException("登记候选人放弃必须填写原因");
        }
        log.debug("应聘结果流转校验通过, stage={}, result={}, action={}", current.getCode(), result.getCode(), actionType);
        return new TransitionPlan(actionType, current, current, result);
    }

    /* ------------------------------------------------------------------ 面试/背调结论判定 ------------------------------------------------------------------ */

    /**
     * 判断面试结果是否已构成「结论」（非空且不是待反馈）。
     *
     * @param interviewResult 面试结果编码，可为空
     * @return 是否已给出结论
     */
    public boolean isInterviewResultConcluded(String interviewResult) {
        if (StringUtils.isBlank(interviewResult)) {
            return false;
        }
        return !InterviewResultEnum.PENDING.getCode().equals(interviewResult);
    }

    /**
     * 判断背调结果是否已构成「结论」（非空且不是待背调）。
     *
     * @param backgroundResult 背调结果编码，可为空
     * @return 是否已给出结论
     */
    public boolean isBackgroundResultConcluded(String backgroundResult) {
        if (StringUtils.isBlank(backgroundResult)) {
            return false;
        }
        return !BackgroundResultEnum.PENDING.getCode().equals(backgroundResult);
    }

    /**
     * 判断是否满足「经授权的免背调原因」：背调结论为已豁免且填写了授权原因。
     *
     * @param backgroundResult 背调结果编码，可为空
     * @param waiveReason      免背调授权原因，可为空
     * @return 是否满足免背调条件
     */
    public boolean isBackgroundWaivedWithReason(String backgroundResult, String waiveReason) {
        return BackgroundResultEnum.WAIVED.getCode().equals(backgroundResult) && StringUtils.isNotBlank(waiveReason);
    }

    /**
     * 阶段校验失败的中文提示（错误码 {@link HrTalentErrorCode#HR_APP_001} 的语义）。
     *
     * @return 中文提示
     */
    public String invalidTransitionMessage() {
        return HrTalentErrorCode.MSG_HR_APP_001;
    }

    /**
     * 缺少进入目标阶段必填资料的中文提示（错误码 {@link HrTalentErrorCode#HR_APP_002} 的语义）。
     *
     * @return 中文提示
     */
    public String missingPrerequisiteMessage() {
        return HrTalentErrorCode.MSG_HR_APP_002;
    }

}
