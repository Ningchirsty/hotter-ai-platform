package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 已删除项目的清单行（V0.2 R26）：给"批量清理已删项目素材"用。
 *
 * <p>只查"已软删（del_flag=1）"的项目——批量清理刻意只处理它们：
 * 批量入口最容易误点，而"项目已删除"本身就是最强的语义确认。</p>
 *
 * @author creative
 */
@Data
public class DeletedTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long taskId;

    /** 项目名 */
    private String taskName;

    /** 交付类型（展示用，便于区分是哪种场景的项目） */
    private String deliverableType;

    /** 删除时间（取 update_time：软删时会被刷新） */
    private java.time.LocalDateTime updateTime;
}
