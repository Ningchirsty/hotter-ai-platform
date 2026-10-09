package org.dromara.aigov.task.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTaskEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务事件视图。
 *
 * <p><b>这是排障的主视图</b>：任务卡住时唯一能看出「从哪到哪、什么时候、谁改的」的地方。
 * 因此 from/to 与序号都原样暴露，不做任何合并或省略。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTaskEvent.class)
public class AigTaskEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long eventId;

    private Long taskId;

    /**
     * 任务内序号（顺序错了事件流就失去排障价值，故原样给出）
     */
    private Integer sequence;

    private String eventType;

    /**
     * 事件类型描述
     */
    private String eventTypeLabel;

    private String fromStatus;

    private String toStatus;

    private Integer attemptNo;

    /**
     * 事件载荷（JSON 文本；契约 {@code execution-event.schema.json} 的 {@code payload}）。
     *
     * <p><b>为什么必须下发</b>：这一列一直有写入方（进度 {@code {"progress":N}}、制品
     * {@code {"artifactIds":[…]}}、策略决策 {@code {"reasonCode":…,"errorCode":…}}），
     * 但视图里没有这个字段——于是<b>写进库的载荷谁都读不到</b>：界面拿不到、
     * 契约消费方也拿不到。它是"机器读的那一份"（{@code detail} 是给人读的那一份），
     * 两者缺一不可。</p>
     *
     * <p>这是在线端到端验证时发现的：库里 {@code payload_json='{"artifactIds":["…"]}'}，
     * 而 {@code /aigov/task/{id}} 返回的事件里该字段为空（不是没写，是没下发）。</p>
     */
    private String payloadJson;

    private String detail;

    private Long actorId;

    private String actorName;

    private String traceId;

    private LocalDateTime operateTime;

}
