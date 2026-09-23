package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.hrtalent.domain.entity.RecruitImportError;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入错误明细视图对象 hr_recruit_import_error。
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitImportError.class)
public class RecruitImportErrorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误ID
     */
    private Long errorId;

    /**
     * 导入批次ID
     */
    private Long batchId;

    /**
     * 工作表名称
     */
    private String sheetName;

    /**
     * 行号（与用户在 Excel 里看到的行号一致）
     */
    private Integer rowNo;

    /**
     * 出错字段名（表头名）
     */
    private String fieldName;

    /**
     * 原始值（仅必要片段）
     */
    private String rawValue;

    /**
     * 错误编码
     */
    private String errorCode;

    /**
     * 严重级别（error/warning）
     */
    private String severity;

    /**
     * 错误说明
     */
    private String errorMessage;

}
