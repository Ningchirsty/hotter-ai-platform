package org.dromara.hrtalent.domain.bo.talent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 简历解析结果人工确认业务对象（SPEC-P4 §2.1 / 设计文档 §8.21）。
 *
 * <p><b>人工复核是正式字段更新的唯一入口</b>：只有本对象中 {@code accepted = true} 的候选项才会写入
 * 人才主档；未勾选（含低置信度默认不勾选）的候选项保持候选状态，<b>不得自动覆盖</b>正式数据。</p>
 *
 * <p>{@code normalizedValue} 允许人工修正：为空时使用解析结果的标准化值，非空时以人工填写值落库，
 * 因此表列说明「标准化值（人工确认后可写入正式字段）」的语义得以保留。</p>
 *
 * @author hr-talent
 */
@Data
public class ParseConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 确认备注（可选，最长 500）
     */
    private String remark;

    /**
     * 逐字段确认项（至少一项；未勾选的候选项也必须回传，用于记录 {@code rejected} 复核结论）
     */
    @Valid
    @NotEmpty(message = "请至少提交一个待确认的解析字段")
    private List<Item> items;

    /**
     * 单个字段的复核入参。
     *
     * @author hr-talent
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 解析结果ID（必填）
         */
        @NotNull(message = "解析结果ID不能为空")
        private Long resultId;

        /**
         * 是否勾选确认（null 视为未勾选；未勾选记为 rejected，不写入正式字段）
         */
        private Boolean accepted;

        /**
         * 人工修正后的标准化值（可选；为空时使用解析结果自身的标准化值）
         */
        private String normalizedValue;

    }

}
