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
     * 事实出处：这个值来自本任务的哪份资料（内测 S19 / C7-b 起**必选**）。
     *
     * <p>与 {@link #sourceLocator} 的分工：这里是**哪份资料**（结构化、可点开核对），
     * 那里是**资料里的位置**（页/行，本来就是自由文本）。</p>
     *
     * <p><b>为什么从"必填一句话"升级成"必选一份资料"</b>：SPEC 红线第 2 条要求事实来自
     * "经确认的产品资料"。自由文本可以被填成 {@code -} 或 {@code 见资料}——形式满足、追溯失效，
     * 而这条值一旦落 CONFIRMED 就会随开工包交给设计侧。</p>
     */
    @NotNull(message = "请选择事实出处（这条值是从哪份任务资料里看到的）")
    private Long sourceFileId;

    /**
     * 资料里的位置（可选）：如「第 3 行」「第 2 页参数表」。
     *
     * <p>入库时会与资料名拼成 {@code 文件名 · 位置} 存进 {@code source_locator}，
     * 因为开工包给下游看的就是这个字符串——只有位置没有文件名等于没说清出处。</p>
     */
    @Size(max = 500, message = "出处位置说明不能超过 500")
    private String sourceLocator;

    /**
     * 备注（可选）
     */
    @Size(max = 500, message = "说明长度不能超过 500")
    private String remark;

}
