package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 互动确认卡处理业务对象。
 *
 * <p>对应设计文档 §7.2 的处理选项：确认某个候选值、填写其他值，或「暂不确认并阻断」。
 * <b>不存在</b>「让 AI 自己选一个」的选项——产品事实必须由人确认。</p>
 *
 * @author content
 */
@Data
public class ContentCardResolveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 互动卡ID
     */
    @NotNull(message = "互动卡ID不能为空")
    private Long cardId;

    /**
     * 处理选项：{@code CONFIRM}（采用某个已有候选值）/ {@code OTHER}（填写其他值）
     * / {@code SUPPLEMENT}（补充资料）/ {@code BLOCK}（暂不确认并阻断）
     */
    @NotNull(message = "处理选项不能为空")
    private String option;

    /**
     * 选定的候选值（option=CONFIRM 时必填）
     */
    @Size(max = 500, message = "确认值长度不能超过 500")
    private String value;

    /**
     * 选定候选值对应的快照行ID（option=CONFIRM 时建议传，用于精确锚定证据）
     */
    private Long snapshotId;

    /**
     * 处理说明（option=BLOCK 或 SUPPLEMENT 时建议填写原因）
     */
    @Size(max = 500, message = "处理说明长度不能超过 500")
    private String comment;

}
