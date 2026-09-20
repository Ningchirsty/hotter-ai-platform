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
