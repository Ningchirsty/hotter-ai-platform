package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务步骤 aig_task_step（设计 §8.2、§6.5）。
 *
 * <p>只记引用（{@link #outputRef}/{@link #approvalRef}），不把大结果或受限原文落进本表。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_task_step")
public class AigTaskStep extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 步骤ID */
    @TableId(value = "step_id")
    private Long stepId;

    /** 所属任务 */
    private Long taskId;

    /** 步骤序号（同任务内从 1 开始） */
    private Integer sequence;

    /** 步骤类型 */
    private String kind;

    /** 引用的资源版本（子 Agent 版本 / 工具版本；可空） */
    private Long resourceVersionId;

    /** 步骤状态 */
    private String status;

    /** 输入摘要 */
    private String inputHash;

    /** 结果引用 */
    private String outputRef;

    /** 批准引用 */
    private String approvalRef;

    /** 第几次尝试 */
    private Integer attempt;

    /** 失败分类 */
    private String errorCode;

    /** 失败原因 */
    private String errorMessage;

    /** 删除标志 */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;

}
