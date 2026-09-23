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
 * 分镜 dp_storyboard
 *
 * <p>一次分镜编排的版本头。屏在 {@code dp_storyboard_screen}。
 * 关联「采用哪版基因、哪个方向」——这样事后能回答「这套分镜是按哪版基因与方向拆的」。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_storyboard")
public class DpStoryboard extends BaseEntity implements Serializable {

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
     * 编号 SB-yyyyMMdd-xxxx
     */
    private String storyboardNo;

    /**
     * 版本
     */
    private Integer version;

    /**
     * 采用的方向（dp_visual_direction.id）
     */
    private Long visualDirectionId;

    /**
     * 采用的基因（dp_visual_dna.id，锁定版）
     */
    private Long visualDnaId;

    /**
     * 屏数
     */
    private Integer screenCount;

    /**
     * 节奏编排（信息密度/情绪曲线）
     */
    private String rhythmJson;

    /**
     * 状态（DRAFT草稿/REVIEW待审/LOCKED已锁定）
     */
    private String status;

    /**
     * 来源（TEMPLATE模板派生/AI生成/MANUAL人工）
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
     * 锁定人
     */
    private Long approvedBy;

    /**
     * 锁定时间
     */
    private LocalDateTime approvedAt;

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
