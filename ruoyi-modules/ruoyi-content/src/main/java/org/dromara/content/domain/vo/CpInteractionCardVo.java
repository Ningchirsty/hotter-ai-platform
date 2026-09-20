package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpInteractionCard;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 互动确认卡视图对象 cp_interaction_card
 *
 * @author content
 */
@Data
@AutoMapper(target = CpInteractionCard.class)
public class CpInteractionCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 互动卡ID
     */
    private Long cardId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务号（列表页显示「是哪张任务上的问题」）
     */
    private String taskNo;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 卡片类型
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
     * 证据来源（JSON 文本，前端解析后逐条展示）
     */
    private String evidenceJson;

    /**
     * 影响对象（JSON 文本）
     */
    private String impactJson;

    /**
     * 处理选项（JSON 文本）
     */
    private String optionsJson;

    /**
     * 闸门等级
     */
    private String gateLevel;

    /**
     * 是否阻断（Y/N）
     */
    private String blocking;

    /**
     * 责任人
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
     * 状态
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
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
