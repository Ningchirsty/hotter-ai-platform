package org.dromara.creative.domain;

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
 * 视觉方向 dp_visual_direction
 *
 * <p>A/B/C 三套方向，同一个锁定基因下的不同取舍（配色偏向、光线气氛、场景、构图）。
 * {@code strategy_json} 是差异点明细的权威内容；选定一条后其余置为 REJECTED，
 * 但<b>不删除</b>——「当时为什么没选 B」是可复盘信息。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_visual_direction")
public class DpVisualDirection extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 方向代号（A/B/C）
     */
    private String directionCode;

    /**
     * 方向名称
     */
    private String directionName;

    /**
     * 一句话概念
     */
    private String concept;

    /**
     * 策略明细（差异点，结构化）
     */
    private String strategyJson;

    /**
     * 预览图附件ID（逗号分隔，可空＝尚未出预览）
     */
    private String previewFileIds;

    /**
     * 状态（GENERATED待选/SELECTED已选/REJECTED已弃）
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 来源（AI生成/MANUAL人工）
     */
    private String source;

    /**
     * 生成所用模型标识
     */
    private String modelKey;

    /**
     * 治理层调用链ID
     */
    private String traceId;

    /**
     * 选定人
     */
    private Long selectedBy;

    /**
     * 选定时间
     */
    private LocalDateTime selectedAt;

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
