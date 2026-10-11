package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对话式 Copilot 的一轮对话 {@code aig_studio_copilot_turn}（附件 07 增量 6「对话式 Copilot」）。
 *
 * <h3>为什么是一张追加型账本，而不是"会话表 + 消息表"</h3>
 * <p>这里的对话不是通用聊天：它是<b>培训助手在某一版草稿上提了几次案</b>。会话的上下文就是
 * "这份草稿当前的这一版内容"，没有跨草稿的会话、也没有多轮续写的状态——因此一行＝一轮，
 * 每行自带当时的修订号与内容哈希。引入会话/消息两层只会多一个"这张消息属于哪个会话"
 * 的间接层，而回答"这条提案是模型在第几次对话里提的"反而更绕。</p>
 *
 * <h3>为什么要记 {@code status} 与 {@code failureReason}</h3>
 * <p>模型可能调不通（FAILED），也可能<b>输出了白名单之外的操作</b>（REJECTED）。
 * 后者是这条通道最有价值的信号：说明模型试图改类别/能力编码/外发许可这类它不该碰的东西。
 * 只记"成功的那几轮"会让这件事永远看不见。</p>
 *
 * <h3>为什么记 {@code contentHash} / {@code revision}</h3>
 * <p>提案是按 {@code (draftId, baseRevision)} 做 CAS 的。事后要回答"为什么这次提案失效了"，
 * 必须知道它是在哪一版内容上提的——只记 draftId 答不出来。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_copilot_turn")
public class AigStudioCopilotTurn extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对话轮次ID
     */
    @TableId(value = "turn_id")
    private Long turnId;

    /**
     * 所属训练草稿
     */
    private Long draftId;

    /**
     * 对话基于的修订号（提案 CAS 的基准）
     */
    private Integer revision;

    /**
     * 对话基于的草稿内容哈希
     */
    private String contentHash;

    /**
     * 用户消息
     */
    private String userMessage;

    /**
     * 本次数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     */
    private String dataLevel;

    /**
     * 模型原文（按 {@code outputPreviewChars} 截断；完整原文仍可按 traceId 从网关审计查）
     */
    private String modelOutput;

    /**
     * 结论（SUCCEEDED 已产出提案 / REJECTED 提案被白名单等规则拒绝 / FAILED 调用或解析失败）
     */
    private String status;

    /**
     * 拒绝/失败原因（成功时为空）
     */
    private String failureReason;

    /**
     * 产出的提案ID（仅 SUCCEEDED）
     */
    private Long proposalId;

    /**
     * 网关调用链ID
     */
    private String traceId;

    /**
     * 命中的模型键
     */
    private String modelKey;

    /**
     * 部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）
     */
    private String deploymentType;

    /**
     * 是否发生外发（Y/N；为空表示网关没给结论）
     */
    private String externalCall;

    /**
     * 耗时（毫秒）
     */
    private Long latencyMs;

}
