package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务事件 aig_task_event（追加型，不做逻辑删除）。
 *
 * <p><b>为什么状态列之外还要事件表</b>：{@code aig_task.status} 只有「现在」，
 * 回答不了「什么时候变成这样的、变了多少次、是谁改的」。任务卡住时，
 * 唯一能定位的线索就是事件流的 from/to 轨迹。</p>
 *
 * <p><b>{@link #sequence} 与 task_id 组成唯一键</b>：任务内事件必须严格有序且不重号。
 * 唯一键是最后的防线——并发写入若产生重号，插入直接失败，
 * 而不是安静地留下两条顺序不明的事件（顺序错了，事件流就失去了排障价值）。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_task_event")
public class AigTaskEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    @TableId(value = "event_id")
    private Long eventId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务内事件序号（从1递增，与 task_id 组成唯一键）
     */
    private Integer sequence;

    /**
     * 事件类型（{@code AigTaskEventTypeEnum}；业务域可写自定义名）
     */
    private String eventType;

    /**
     * 迁移前状态
     */
    private String fromStatus;

    /**
     * 迁移后状态
     */
    private String toStatus;

    /**
     * 所属尝试次数
     */
    private Integer attemptNo;

    /**
     * 事件载荷（JSON；<b>不得含密钥与受限原文</b>）
     */
    private String payloadJson;

    /**
     * 可读说明
     */
    private String detail;

    /**
     * 操作者（系统事件为空）
     */
    private Long actorId;

    /**
     * 操作者名称
     */
    private String actorName;

    /**
     * 调用链追踪ID
     */
    private String traceId;

    /**
     * 事件时间
     */
    private LocalDateTime operateTime;

}
