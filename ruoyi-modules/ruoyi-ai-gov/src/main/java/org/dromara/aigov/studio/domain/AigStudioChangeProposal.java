package org.dromara.aigov.studio.domain;

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
 * Agent Studio 变更提案 {@code aig_studio_change_proposal}（增量 21／附件 07 增量 6）。
 *
 * <p><b>本表存在的原因</b>：培训助手（Copilot）只能**提案**，不能直接改草稿。
 * 没有这张表时，"模型改的"与"人改的"在库里长得一模一样，事后无法回答
 * "这条改动是谁提的、谁批的"。提案是一条<b>待审对象</b>：它引用草稿与基准修订、
 * 保存一份白名单操作清单与改动预览；人采纳后才产生一条
 * {@code source=COPILOT_ACCEPTED} 的新修订。</p>
 *
 * <p><b>为什么存 {@code content_hash_before/after}</b>：采纳时重新计算并比对——
 * 提案之后草稿若被改过（哪怕有人只改了别处），内容漂移就必须拒绝，
 * 而不是"按老提案覆盖新内容"。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_change_proposal")
public class AigStudioChangeProposal extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提案ID
     */
    @TableId(value = "proposal_id")
    private Long proposalId;

    /**
     * 所属草稿
     */
    private Long draftId;

    /**
     * 提案基于的修订号（采纳时做 CAS）
     */
    private Integer baseRevision;

    /**
     * 提案来源（{@code AigStudioProposalSourceEnum}）
     */
    private String source;

    /**
     * 规范化后的操作清单（白名单操作 + JSON Pointer）
     */
    private String operationsJson;

    /**
     * 改动预览（path/before/after）
     */
    private String previewJson;

    /**
     * 提案时的草稿内容哈希
     */
    private String contentHashBefore;

    /**
     * 应用后预计的内容哈希
     */
    private String contentHashAfter;

    /**
     * 状态（{@code AigStudioProposalStatusEnum}）
     */
    private String status;

    /**
     * 处理人（采纳/拒绝）
     */
    private Long decidedBy;

    /**
     * 处理时间
     */
    private LocalDateTime decidedAt;

    /**
     * 处理说明（拒绝原因；采纳时可为空）
     */
    private String decidedReason;

    /**
     * 提案说明
     */
    private String summary;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
