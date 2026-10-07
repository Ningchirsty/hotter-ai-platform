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

    private String detail;

    private Long actorId;

    private String actorName;

    private String traceId;

    private LocalDateTime operateTime;

}
