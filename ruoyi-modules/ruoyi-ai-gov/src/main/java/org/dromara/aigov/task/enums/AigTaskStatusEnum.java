package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 统一任务状态（设计 §9.2 状态机）。
 *
 * <p><b>为什么状态是枚举而不是字符串常量</b>：状态会出现在四张表、事件表、回调账本与
 * 前端列表里。写成散落的字符串时，「拼错一个状态」（如 {@code REVIEW_PENDDING}）不会报错，
 * 它只会让该任务永远不匹配任何查询条件——从界面上看就是「这条任务不见了」。</p>
 *
 * <p><b>{@link #terminal} 与状态机的出边必须一致</b>：这里声明「谁是终态」，
 * 状态机声明「谁能走到谁」。两处若不一致，要么出现无法离开的僵尸状态，
 * 要么出现「终态还能再变」的诡异数据。有一致性用例同时校验两侧
 * （见 {@code AigTaskStateMachineTest}）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskStatusEnum {

    /**
     * 草稿：已创建、尚未提交策略校验
     */
    DRAFT("DRAFT", "草稿", false),

    /**
     * 策略校验中
     */
    POLICY_CHECKING("POLICY_CHECKING", "策略校验中", false),

    /**
     * 已入队：通过策略校验，等待派发
     */
    QUEUED("QUEUED", "已入队", false),

    /**
     * 已派发：已提交给 Provider（异步任务即「已拿到 provider_job_id」）
     */
    DISPATCHED("DISPATCHED", "已派发", false),

    /**
     * 执行中
     */
    RUNNING("RUNNING", "执行中", false),

    /**
     * 执行成功。
     * <p><b>只代表 Provider 返回成功</b>，不代表审核通过——候选资产仍需 QA 与人工选定
     * （设计 §9.2 明确写了这一点，因此它不是终态，而是通向 {@link #REVIEW_PENDING}）。</p>
     */
    SUCCEEDED("SUCCEEDED", "执行成功（待复核，不等于通过）", false),

    /**
     * 待人工复核
     */
    REVIEW_PENDING("REVIEW_PENDING", "待人工复核", false),

    /**
     * 已通过（终态）
     */
    APPROVED("APPROVED", "已通过", true),

    /**
     * 已拒绝（终态）
     */
    REJECTED("REJECTED", "已拒绝", true),

    /**
     * 失败：可进入 {@link #RETRY_WAIT} 或 {@link #NEED_HUMAN}
     */
    FAILED("FAILED", "失败", false),

    /**
     * 等待重试
     */
    RETRY_WAIT("RETRY_WAIT", "等待重试", false),

    /**
     * 已请求取消：等 Provider 确认。
     *
     * <p><b>为什么需要这个中间态</b>：异步 Provider 的作业一旦提交，取消不是本地能立刻完成的
     * ——它可能已经在生成中并继续计费。若直接置 {@link #CANCELLED}，账上显示「已取消」
     * 而对方仍在跑，费用照样产生，事后无从对账。必须先标记「请求已发出」，
     * 等 Provider 确认（或回报作业已结束）再落 {@link #CANCELLED}。</p>
     */
    CANCEL_REQUESTED("CANCEL_REQUESTED", "已请求取消（等 Provider 确认）", false),

    /**
     * 已取消（终态）
     */
    CANCELLED("CANCELLED", "已取消", true),

    /**
     * 待人工处理：重试耗尽、需人工补输入、或策略判定转人工
     */
    NEED_HUMAN("NEED_HUMAN", "待人工处理", false);

    /**
     * 编码（入库与对外口径）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否终态（到达后不应再有状态迁移）
     */
    private final boolean terminal;

    /**
     * 是否处于「已提交、结果未定」的在途状态。
     *
     * <p>用途：判断能否取消、以及回调是否可能带来状态推进。
     * 刻意包含 {@link #CANCEL_REQUESTED}——请求取消后 Provider 仍可能回报完成，
     * 这类回调必须被接受（见状态机的 {@code CANCEL_REQUESTED → RUNNING} 边），
     * 若把该状态排除在途之外，那条回调会被当成「任务已结束」而丢弃。</p>
     *
     * @return 在途返回 true
     */
    public boolean isInFlight() {
        return this == DISPATCHED || this == RUNNING || this == CANCEL_REQUESTED;
    }

    /**
     * 是否已进入人工环节（复核或待处理）。
     *
     * @return 是否需人工关注
     */
    public boolean isHumanStage() {
        return this == REVIEW_PENDING || this == NEED_HUMAN;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigTaskStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
