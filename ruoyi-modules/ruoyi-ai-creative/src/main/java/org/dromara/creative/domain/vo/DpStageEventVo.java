package org.dromara.creative.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.creative.domain.DpStageEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阶段事件展示对象。
 *
 * @author creative
 */
@Data
@AutoMapper(target = DpStageEvent.class)
public class DpStageEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 视觉项目
     */
    private Long taskId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 变更前阶段
     */
    private String fromStage;

    /**
     * 变更后阶段
     */
    private String toStage;

    /**
     * 动作
     */
    private String action;

    /**
     * 事件明细
     */
    private String detailJson;

    /**
     * 操作人
     */
    private Long actorId;

    /**
     * 操作人姓名
     */
    private String actorName;

    /**
     * 发生时间
     */
    private LocalDateTime createTime;

}
