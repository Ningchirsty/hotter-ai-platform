package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阶段事件 dp_stage_event
 *
 * <p>全链路可追溯的最小实现：每次阶段变更、每次出图提交/收敛都落一条，
 * 记录「谁、什么时候、把项目从哪个阶段推到哪个阶段、做了什么动作」。
 * 只追加不修改，因此是排查「这张图是什么时候、因为什么产生的」的第一手证据。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_stage_event")
public class DpStageEvent extends BaseEntity implements Serializable {

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
     * 事件类型（MATERIAL/DNA/DIRECTION/STORYBOARD/VISUAL_GATE/GENERATION/QA/LAYOUT/FINAL）
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
     * 动作（如 DNA_LOCK、GENERATION_RETRY）
     */
    private String action;

    /**
     * 事件明细（结构化）
     */
    private String detailJson;

    /**
     * 操作人
     */
    private Long actorId;

    /**
     * 操作人姓名（冗余，便于展示）
     */
    private String actorName;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
