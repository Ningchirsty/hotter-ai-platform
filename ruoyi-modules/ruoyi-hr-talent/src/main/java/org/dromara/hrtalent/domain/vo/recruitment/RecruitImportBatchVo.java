package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitImportBatch;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入批次视图对象 hr_recruit_import_batch。
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitImportBatch.class)
public class RecruitImportBatchVo implements Serializable {

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
     * 导入类型（standard/plan）
     */
    private String importType;

    /**
     * 导入类型名称
     */
    private String importTypeName;

    /**
     * 源文件名称
     */
    private String sourceFileName;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failureCount;

    /**
     * 批次状态（pending/confirming/success/partial_failed/failed/cancelled）
     */
    private String status;

    /**
     * 状态标签
     */
    private String statusLabel;

    /**
     * 开始时间
     */
    private LocalDateTime startedTime;

    /**
     * 结束时间
     */
    private LocalDateTime finishedTime;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
