package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应聘阶段流转入参（SPEC-P3 §2.2 POST /recruit/applications/{id}/transition，§3.2）。
 *
 * <p><b>二选一</b>：{@link #toStage}（阶段前进）与 {@link #result}（转入淘汰/放弃/暂缓/人才保留）
 * 必须且只能提供一个；同时提供或都不提供都会被拒绝。</p>
 *
 * <p>{@link #override} 为管理员例外跳转开关：置为 {@code true} 时可跳过 §7.2 的前置校验与阶段回退限制，
 * 但 {@link #overrideReason} 必填，且服务端会写一条 {@code stage_override} 审计。</p>
 *
 * <p>进入「待报到」需要邀约结果与计划报到日期，可在本次流转中一并提交
 * （{@link #offerResult} / {@link #planArrivalDate}），避免二次调用。</p>
 *
 * @author hr-talent
 */
@Data
public class ApplicationTransitionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原阶段编码（可选；用于 §9.6「校验当前阶段与请求前置阶段一致」，
     * 传入后与服务端当前阶段不一致会直接报错，避免前端基于过期页面提交流转）
     */
    @Size(max = 32, message = "原阶段长度不能超过 32")
    private String fromStage;

    /**
     * 目标阶段编码（字典 recruit_candidate_stage），与 {@link #result} 二选一
     */
    @Size(max = 32, message = "目标阶段长度不能超过 32")
    private String toStage;

    /**
     * 目标应聘结果编码（字典 recruit_application_result），与 {@link #toStage} 二选一；
     * 仅接受 淘汰(rejected) / 候选人放弃(withdrawn) / 暂缓(paused) / 人才保留(talent_pool)
     */
    @Size(max = 32, message = "应聘结果长度不能超过 32")
    private String result;

    /**
     * 原因编码（字典编码，不存中文）
     */
    @Size(max = 64, message = "原因编码长度不能超过 64")
    private String reasonCode;

    /**
     * 阶段说明（淘汰/放弃时同时作为必填原因的兜底）
     */
    @Size(max = 1000, message = "阶段说明长度不能超过 1000")
    private String comment;

    /**
     * 本次跟进的下一步日期
     */
    private LocalDateTime nextFollowTime;

    /**
     * 是否管理员例外跳转（跳过前置校验与阶段回退限制，必须填写原因并写审计）
     */
    private Boolean override;

    /**
     * 管理员例外跳转原因
     */
    @Size(max = 500, message = "例外跳转原因长度不能超过 500")
    private String overrideReason;

    /**
     * 邀约结果编码（进入「待报到」时必填，可在本接口一并登记）
     */
    @Size(max = 32, message = "邀约结果长度不能超过 32")
    private String offerResult;

    /**
     * 计划报到日期（进入「待报到」时必填，可在本接口一并登记）
     */
    private LocalDate planArrivalDate;

    /**
     * 乐观锁版本号（必填，取自详情返回值）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试")
    private Integer version;

}
