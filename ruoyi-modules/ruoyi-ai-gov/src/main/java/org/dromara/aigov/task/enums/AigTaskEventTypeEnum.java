package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务事件类型（{@code aig_task_event.event_type}）。
 *
 * <p><b>为什么事件名不按每次迁移各起一个</b>：设计文档里举过 {@code AiTaskDispatched}、
 * {@code AiTaskSucceeded} 这类名字。但事件表已经带了 {@code from_status}/{@code to_status}
 * 两列——「从哪到哪」本来就完整落在数据里。若再为每条边各起一个事件名，
 * 同一件事就有了两处表达（状态图一处、事件名一处），而两处一旦不一致，
 * 读事件流的人会得到互相矛盾的结论。故状态迁移统一用
 * {@link #AI_TASK_STATUS_CHANGED}，迁移内容看 from/to 列。</p>
 *
 * <p>其余是本层自己产生的事实性事件。业务域特有的事件（{@code CandidateAssetCreated}、
 * {@code VisualQaCompleted}、{@code AgentPlanConfirmed} 等）刻意<b>不</b>收进本枚举：
 * 它们是业务事实而非状态迁移，会随业务域不断增加。事件表的 {@code event_type} 是
 * varchar，允许业务域写自己的常量；{@link #find} 对未知名返回 null，正是为此留的口子。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskEventTypeEnum {

    /**
     * 任务创建（含首次快照冻结）
     */
    AI_TASK_CREATED("AI_TASK_CREATED", "任务创建"),

    /**
     * 状态迁移（迁移内容看 from_status/to_status）
     */
    AI_TASK_STATUS_CHANGED("AI_TASK_STATUS_CHANGED", "状态迁移"),

    /**
     * 进度更新（非状态迁移）
     */
    AI_TASK_PROGRESSED("AI_TASK_PROGRESSED", "进度更新"),

    /**
     * 结果回写（候选入库；SUCCEEDED 不等于审核通过）
     */
    AI_TASK_RESULT_RECORDED("AI_TASK_RESULT_RECORDED", "结果回写"),

    /**
     * 收到 Provider 回调（含验签与幂等结论）
     */
    AI_TASK_CALLBACK_RECEIVED("AI_TASK_CALLBACK_RECEIVED", "收到回调"),

    /**
     * 人工复核结论
     */
    AI_TASK_REVIEWED("AI_TASK_REVIEWED", "人工复核");

    /**
     * 编码
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null（业务域自定义事件名走这里返回 null，属预期）。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigTaskEventTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskEventTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
