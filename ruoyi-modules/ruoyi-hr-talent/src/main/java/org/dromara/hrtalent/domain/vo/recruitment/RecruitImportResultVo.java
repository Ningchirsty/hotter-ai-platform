package org.dromara.hrtalent.domain.vo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入确认结果（确认阶段返回给前端）。
 *
 * @author hr-talent
 */
@Data
public class RecruitImportResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 批次状态（success/partial_failed/failed）
     */
    private String status;

    /**
     * 状态说明
     */
    private String statusLabel;

    /**
     * 数据总行数
     */
    private int totalCount;

    /**
     * 成功导入行数
     */
    private int successCount;

    /**
     * 失败行数
     */
    private int failureCount;

    /**
     * 失败明细（最多返回 {@code MAX_RETURNED_ERRORS} 条）
     */
    private List<RecruitImportErrorVo> issues = new ArrayList<>();

    /**
     * 附加说明（例如「本次自动创建了 3 张月度计划表头」）
     */
    private List<String> messages = new ArrayList<>();

}
