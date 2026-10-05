package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容生产任务对象 cp_task
 *
 * <p>状态只由闸门判定 + 人工动作共同推进（见 SPEC §4.1）；
 * {@code blockReason} 由闸门写入，用于列表页直接显示「卡在哪」。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_task")
public class CpTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 任务号（对外展示）
     */
    private String taskNo;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 交付类型（见 ContentDeliverableTypeEnum）
     */
    private String deliverableType;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 任务负责人（互动卡默认指派人）
     */
    private Long ownerId;

    /**
     * 任务负责人姓名
     */
    private String ownerName;

    /**
     * 资料敏感级别（PUBLIC/INTERNAL/RESTRICTED，复用 aig_data_level）
     */
    private String dataLevel;

    /**
     * 是否允许外部AI（Y/N）
     */
    private String allowExternal;

    /**
     * 任务状态（见 ContentTaskStatusEnum）
     */
    private String status;

    /**
     * 视觉阶段（{@code cp_task.visual_stage}，由创作域写入）。
     *
     * <p><b>为什么内容域要读它</b>（内测 S6 / 冲突 B 的最佳建议）：品牌部关心"图做到哪了"，
     * 但 {@link #status} 回答的是"资料齐不齐、能不能开工"——两个正交的问题。
     * 内测实测：视觉侧已经 {@code COMPLETED}，内容侧仍显示"可开工"，品牌部只能靠问人。
     * 所以这里**只读展示**，绝不回写 status：一旦两边都写 status，
     * "资料就绪度"这个判据就会随制作进度漂移，闸门与审计都会失真。</p>
     *
     * <p>取值见创作域 {@code DpVisualStageEnum}（内容域不依赖创作模块，故存编码，
     * 中文由前端映射——字典在 {@code @/api/creative/types} 的 CREATIVE_STAGE_LABELS，
     * 两处共用一份，避免抄出第二张会对不上的表）。</p>
     */
    private String visualStage;

    /**
     * 当前阻断原因（闸门写入）
     */
    private String blockReason;

    /**
     * 解析完成时间
     */
    private LocalDateTime parseDoneAt;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
