package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 月度结转执行入参（SPEC-P2 §3.3 / §4.3）。
 *
 * <p>结转是<b>幂等</b>操作：同一来源任务对同一目标月份最多生成一条结转任务，
 * 重复执行或并发执行都通过唯一索引 {@code (source_item_id, target_month)} 安全跳过。</p>
 *
 * @author hr-talent
 */
@Data
public class RolloverExecuteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公司（平台部门）ID；为空表示按来源月份跨公司结转
     */
    private Long companyDeptId;

    /**
     * 来源月份（yyyy-MM，必填）
     */
    @NotBlank(message = "来源月份不能为空")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "来源月份格式必须为 yyyy-MM")
    private String sourceMonth;

    /**
     * 目标月份（yyyy-MM，必填）
     */
    @NotBlank(message = "目标月份不能为空")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "目标月份格式必须为 yyyy-MM")
    private String targetMonth;

    /**
     * 指定只结转的来源任务ID集合；为空表示按来源月份全量扫描
     */
    private List<Long> sourceItemIds;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
