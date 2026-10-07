package org.dromara.aigov.task.domain;

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
 * 任务结果 aig_task_result（候选资产回写）。
 *
 * <p><b>{@link #candidateStatus} 的语义由 {@code AigCandidateStatusEnum} 保证：
 * 自动流程只筛除、不放行。</b>因此本表有「已选定必须有人」这条隐含约束：
 * {@code selected_by}/{@code selected_at} 只在人工选定（APPROVED）时写入，
 * 服务层必须在写入前校验操作者是人工而非编排器。</p>
 *
 * <p><b>{@link #validationResult} 与 {@link #qaVerdict} 是两件事</b>：
 * 前者是「输出是否符合 Schema」（结构问题，机器可判），
 * 后者是「内容是否与事实/品牌一致」（业务问题，设计 §13 的三层质检）。
 * 合并成一个字段会让「格式对但内容错」这种最常见的失败无法表达。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_task_result")
public class AigTaskResult extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结果ID
     */
    @TableId(value = "result_id")
    private Long resultId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 所属尝试次数
     */
    private Integer attemptNo;

    /**
     * 结果类型（STRUCTURED结构化输出 ASSET资产 SESSION会话）
     */
    private String resultType;

    /**
     * 资产ID/对象引用（<b>禁止存服务器本地路径</b>）
     */
    private Long assetId;

    /**
     * 结构化输出（已通过 Schema 校验才入库）
     */
    private String structuredOutputJson;

    /**
     * 结构校验结果（PASS/FAIL）
     */
    private String validationResult;

    /**
     * 校验明细（失败时给字段级原因）
     */
    private String validationDetail;

    /**
     * 候选状态（{@code AigCandidateStatusEnum}）
     */
    private String candidateStatus;

    /**
     * 质检结论（CONSISTENT/INCONSISTENT/UNCERTAIN）
     */
    private String qaVerdict;

    /**
     * 质检说明（含本地确定性度量的边界说明）
     */
    private String qaDetail;

    /**
     * 选定人（必须人工）
     */
    private Long selectedBy;

    /**
     * 选定时间
     */
    private LocalDateTime selectedAt;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
