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
 * 互动确认卡对象 cp_interaction_card
 *
 * <p>对应设计文档 §7.2：卡片必须是**具体问题**，而不是要求用户补整份资料。
 * 因此 {@code title} 一句话说清问题，{@code evidenceJson} 带多来源证据，
 * {@code impactJson} 说明影响对象，{@code assigneeId} 是**任务指定的责任人**（基线 B6）。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_interaction_card")
public class CpInteractionCard extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 互动卡ID
     */
    @TableId(value = "card_id")
    private Long cardId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 卡片类型（见 ContentCardTypeEnum）
     */
    private String cardType;

    /**
     * 相关事实字段编码
     */
    private String fieldCode;

    /**
     * 一句话问题
     */
    private String title;

    /**
     * 问题详细描述
     */
    private String question;

    /**
     * 证据来源（文件/定位/摘录，多来源）
     */
    private String evidenceJson;

    /**
     * 影响对象（交付物/页面/包装/说明书）
     */
    private String impactJson;

    /**
     * 处理选项
     */
    private String optionsJson;

    /**
     * 闸门等级（BLOCK/CONDITION/NOTICE）
     */
    private String gateLevel;

    /**
     * 是否阻断（Y/N，冗余自 gate_level=BLOCK 便于筛选）
     */
    private String blocking;

    /**
     * 责任人（任务指定）
     */
    private Long assigneeId;

    /**
     * 责任人姓名
     */
    private String assigneeName;

    /**
     * 截止时间
     */
    private LocalDateTime dueAt;

    /**
     * 状态（见 ContentCardStatusEnum）
     */
    private String status;

    /**
     * 确认后的值
     */
    private String resolvedValue;

    /**
     * 所选选项
     */
    private String resolvedOption;

    /**
     * 处理人
     */
    private Long resolvedBy;

    /**
     * 处理时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
