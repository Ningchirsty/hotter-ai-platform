package org.dromara.aigov.workspace.portal.helper;

import org.dromara.aigov.task.enums.AigTaskStatusEnum;

/**
 * 员工看到的**业务状态**（设计 §6.8：「点击运行后展示『待检查、排队中、查询资料、生成中、
 * 等待确认、完成、失败』等业务状态」）。
 *
 * <h3>为什么放在服务端算，而不是前端再写一份映射</h3>
 * <p>本仓已经有一条纪律：状态码 → 中文由服务端下发（见 {@code AigTaskStatusEnum} 与任务视图），
 * 前端不再另维护一份。业务状态同样是"给人看的措辞"，而且它还依赖
 * {@code reviewStatus} 这类**任务侧才知道的事实**——放到前端就得把整个状态机搬过去。</p>
 *
 * <h3>一条刻意保留的内部状态</h3>
 * <p>设计把「查询资料」列为业务状态之一，但本平台<b>没有</b>"正在检索知识"这个任务状态：
 * 检索发生在一次模型调用内部（解析阶段），任务级看不见。所以这里<b>不造一个假状态</b>；
 * 真要在界面上看到它，得先让执行侧把阶段上报出来——那是独立变更。
 * 同一条纪律：{@link #resolve} 遇到认不出的状态码返回 {@code null} 文案，
 * 由调用方回落到服务端已下发的中文，而不是猜一个。</p>
 *
 * @author ai-gov
 */
public final class AigPortalTaskStatus {

    private AigPortalTaskStatus() {
    }

    /**
     * 业务状态（码 + 给人看的措辞）。
     *
     * @param code  业务状态码（可读、稳定；认不出时为 null）
     * @param label 业务状态文案（认不出时为 null）
     * @author ai-gov
     */
    public record BusinessStatus(String code, String label) {
    }

    /** 已通过人工复核 */
    private static final String REVIEW_APPROVED = "APPROVED";

    /** 复核未通过 */
    private static final String REVIEW_REJECTED = "REJECTED";

    /**
     * 内部任务状态 → 员工看到的业务状态。
     *
     * @param status       任务状态码（可空）
     * @param reviewStatus 复核状态（可空=尚未复核）
     * @return 业务状态；认不出的状态码返回 null（调用方回落到服务端文案）
     */
    public static BusinessStatus resolve(String status, String reviewStatus) {
        AigTaskStatusEnum current = AigTaskStatusEnum.find(status);
        if (current == null) {
            return null;
        }
        return switch (current) {
            case DRAFT, POLICY_CHECKING -> new BusinessStatus("CHECKING", "待检查");
            case QUEUED -> new BusinessStatus("QUEUED", "排队中");
            case DISPATCHED, RUNNING -> new BusinessStatus("GENERATING", "生成中");
            case RETRY_WAIT -> new BusinessStatus("RETRY_WAIT", "等待重试");
            case NEED_HUMAN -> new BusinessStatus("WAITING_CONFIRM", "等待确认");
            case REVIEW_PENDING -> new BusinessStatus("DRAFT_READY", "草稿已生成 待复核");
            // ★ 关键一条：执行成功**不等于**通过。只有复核通过才叫"完成"；
            //   未复核的显示"草稿已生成 待复核"，复核未通过的如实说未通过。
            //   （注意：这里刻意用 if 而不是 switch——Java 对 String 的 switch 遇到 null 会抛 NPE，
            //   而"未复核"恰恰就是 null。）
            case SUCCEEDED -> {
                String review = blankToNull(reviewStatus);
                if (REVIEW_APPROVED.equals(review)) {
                    yield new BusinessStatus("DONE", "完成");
                }
                if (REVIEW_REJECTED.equals(review)) {
                    yield new BusinessStatus("REVIEW_REJECTED", "已复核 未通过");
                }
                yield new BusinessStatus("DRAFT_READY", "草稿已生成 待复核");
            }
            case APPROVED -> new BusinessStatus("DONE", "完成");
            case REJECTED -> new BusinessStatus("REVIEW_REJECTED", "已复核 未通过");
            case FAILED -> new BusinessStatus("FAILED", "失败");
            case CANCEL_REQUESTED -> new BusinessStatus("CANCELING", "取消中");
            case CANCELLED -> new BusinessStatus("CANCELLED", "已取消");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

}
