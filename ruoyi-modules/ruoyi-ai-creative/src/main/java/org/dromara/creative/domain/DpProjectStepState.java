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
 * 项目步骤状态 dp_project_step_state（V0.2 D2）。
 *
 * <p><b>谁写它</b>：阶段推进走 {@code CreativeProjectServiceImpl#moveStage}（经
 * {@code CreativeStepStateWriter}）；人为动作走 {@code CreativeStepStateServiceImpl#skip /
 * cancelSkip}（V0.2 R36）。查询路径不写库——没持久化就按当前阶段推导投影。</p>
 *
 * <p><b>本表的一条纪律（R36 补）：删除必须物理删除。</b>表上有唯一键
 * {@code uk_dp_project_step(task_id, step_code)}，而 {@code delFlag} 是逻辑删除列——
 * 一旦留下 {@code delFlag='1'} 的软删行，这一步之后**任何**写入都会撞唯一键：
 * 重新跳过 409，{@code moveStage} 的步骤状态同步也 409（而它在一个事务里，
 * 会把整次阶段推进一起回滚）。所以取消跳过用
 * {@code DpProjectStepStateMapper#hardDelete}，历史由 {@code dp_stage_event} 承担。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_project_step_state")
public class DpProjectStepState extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 当时的场景档案（便于换档案后溯源）
     */
    private Long profileId;

    /**
     * 步骤编码（对应 dp_scenario_step.step_code）
     */
    private String stepCode;

    /**
     * 步骤名称（快照）
     */
    private String stepName;

    /**
     * 状态（PENDING/ACTIVE/DONE/SKIPPED）
     */
    private String status;

    /**
     * 顺序（快照）
     */
    private Integer sortNo;

    /**
     * 触发本次状态变化的阶段（对应 dp_stage_event.to_stage）
     */
    private String stageCode;

    /**
     * 进入进行中的时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
