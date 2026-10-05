package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpTask;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容生产任务视图对象 cp_task
 *
 * @author content
 */
@Data
@AutoMapper(target = CpTask.class)
public class CpTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务号
     */
    private String taskNo;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 交付类型
     */
    private String deliverableType;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品名称（联表带出，便于列表直接看）
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
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 任务负责人
     */
    private Long ownerId;

    /**
     * 任务负责人姓名
     */
    private String ownerName;

    /**
     * 资料敏感级别
     */
    private String dataLevel;

    /**
     * 是否允许外部AI
     */
    private String allowExternal;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 视觉阶段（{@code cp_task.visual_stage}，创作域写入，本域只读展示）。
     *
     * <p>品牌部需要"图做到哪了"，而 {@link #status} 只回答"资料齐不齐"。
     * 内测 S6 实测：视觉侧已经 {@code COMPLETED}，内容侧仍显示"可开工"。
     * 这里是那个缺口的补位——**只读**，不回写 status
     * （回写会让"资料就绪度"随制作进度漂移，闸门与审计都会失真）。</p>
     */
    private String visualStage;

    /**
     * 当前阻断原因
     */
    private String blockReason;

    /**
     * 解析完成时间
     */
    private LocalDateTime parseDoneAt;

    /**
     * 待处理互动卡数量（列表页直接显示「还有几张卡要处理」）
     */
    private Integer pendingCardCount;

    /**
     * 阻断卡数量
     */
    private Integer blockingCardCount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
