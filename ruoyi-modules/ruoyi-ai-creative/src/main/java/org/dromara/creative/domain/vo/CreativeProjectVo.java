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
     * 资料敏感级别（PUBLIC/INTERNAL/RESTRICTED）——视觉模型调用要按它选路由
     */
    private String dataLevel;

    /**
     * 是否允许外部 AI（Y/N）——未授权时即便路由命中外部模型也不发出去
     */
    private String allowExternal;

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
     * 该项目的产品是否已配置产品图（cp_product.product_image）。
     *
     * <p>R4 之前这个字段恒为 false：生产库 45 个产品的产品图全为空，
     * 出图只能拿上传的参考图当基准。页面据此提示「先把产品照片设为产品图」。</p>
     */
    private Boolean productImageConfigured;

    /**
     * 产品图文件名（未配置时为 null）
     */
    private String productImageFileName;

    /**
     * 产品图来源任务（不是本项目时页面要如实标注来源）
     */
    private Long productImageSourceTaskId;

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
