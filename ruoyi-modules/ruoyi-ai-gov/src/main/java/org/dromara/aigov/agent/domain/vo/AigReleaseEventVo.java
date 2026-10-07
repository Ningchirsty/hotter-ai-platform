package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigReleaseEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发布事件视图（账本行，只读）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigReleaseEvent.class)
public class AigReleaseEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发布事件ID
     */
    private Long eventId;

    /**
     * 对象类型
     */
    private String targetType;

    /**
     * 对象版本ID
     */
    private Long targetVersionId;

    /**
     * 源发布状态
     */
    private String fromStatus;

    /**
     * 目标发布状态
     */
    private String toStatus;

    /**
     * 本次推进所依据的门槛
     */
    private String passedGates;

    /**
     * 操作人
     */
    private Long operatorId;

    /**
     * 说明
     */
    private String detail;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
