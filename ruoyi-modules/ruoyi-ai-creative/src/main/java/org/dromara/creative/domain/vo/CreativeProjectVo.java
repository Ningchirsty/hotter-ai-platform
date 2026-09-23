package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视觉项目展示对象。
 *
 * <p>项目本体是内容协同的 {@code cp_task}，本 VO 在任务字段之外补上视觉阶段与视觉侧计数，
 * 让「视觉项目」页一眼看出这个项目在视觉工厂里走到哪一步了。</p>
 *
 * @author creative
 */
@Data
public class CreativeProjectVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目ID（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 任务号（对外展示）
     */
    private String taskNo;

    /**
     * 项目名称
     */
    private String taskName;

    /**
     * 交付类型（本项目固定 ECOM_DETAIL）
     */
    private String deliverableType;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 负责人
     */
    private Long ownerId;

    /**
     * 负责人姓名
     */
    private String ownerName;

    /**
     * 内容协同任务状态（cp_task.status）
     */
    private String status;

    /**
     * 当前阻断原因（闸门写入）
     */
    private String blockReason;

    /**
     * 视觉阶段编码（cp_task.visual_stage）
     */
    private String visualStage;

    /**
     * 视觉阶段描述
     */
    private String visualStageDesc;

    /**
     * 待处理互动卡数
     */
    private Integer pendingCardCount;

    /**
     * 阻断型互动卡数
     */
    private Integer blockingCardCount;

    /**
     * 参考图数量（详情接口填充）
     */
    private Integer imageFileCount;

    /**
     * 出图候选数（详情接口填充）
     */
    private Integer generationCount;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
