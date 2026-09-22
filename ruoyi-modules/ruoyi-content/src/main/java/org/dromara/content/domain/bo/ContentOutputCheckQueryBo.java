package org.dromara.content.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 成品一致性检查查询业务对象。
 *
 * @author content
 */
@Data
public class ContentOutputCheckQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（精确）
     */
    private Long taskId;

    /**
     * 任务号（模糊）
     */
    @Size(max = 32, message = "任务号长度不能超过 32")
    private String taskNo;

    /**
     * 检查状态
     */
    @Size(max = 16, message = "检查状态长度不能超过 16")
    private String status;

    /**
     * 检查结论
     */
    @Size(max = 16, message = "检查结论长度不能超过 16")
    private String verdict;

    /**
     * 是否只看未通过（结论为不一致或无法判定）
     * <p>验收场景下最常问的是「哪些成品没通过」，故提供该快捷筛选。</p>
     */
    private Boolean onlyFailed;

}
