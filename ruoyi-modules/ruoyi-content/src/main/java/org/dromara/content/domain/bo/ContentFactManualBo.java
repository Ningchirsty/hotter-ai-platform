package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工录入事实业务对象。
 *
 * @author content
 */
@Data
public class ContentFactManualBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 事实字段编码（需与 cp_gate_rule.field_code 对齐）
     */
    @NotBlank(message = "事实字段编码不能为空")
    @Size(max = 64, message = "字段编码长度不能超过 64")
    private String fieldCode;

    /**
     * 事实值
     */
    @NotBlank(message = "事实值不能为空")
    @Size(max = 500, message = "事实值长度不能超过 500")
    private String value;

    /**
     * 事实出处：这个值是从哪份资料/哪个页签看来的（内测 S19 / C7 起必填）。
     *
     * <p><b>为什么必填</b>：SPEC 红线第 2 条要求事实来自「经确认的产品资料」。
     * 人工录入本来就是这个链条上最容易失控的一环——值直接落 {@code CONFIRMED}，
     * 却没有留下"谁依据什么确认的"。实测开工包里 7 条事实有 5 条 {@code sourceLocator} 为空，
     * 下游拿到的是一个无法追溯的数字。</p>
     *
     * <p>与 {@link #remark} 的分工：这里是**出处**（可核对的定位，如
     * 「产品参数表 V2 第 3 行」），remark 是**备注**（为什么以此值为准）。</p>
     */
    @NotBlank(message = "请填写事实出处（这个值是从哪份资料 / 哪个页签看来的）")
    @Size(max = 500, message = "事实出处长度不能超过 500")
    private String sourceLocator;

    /**
     * 备注（可选）
     */
    @Size(max = 500, message = "说明长度不能超过 500")
    private String remark;

}
