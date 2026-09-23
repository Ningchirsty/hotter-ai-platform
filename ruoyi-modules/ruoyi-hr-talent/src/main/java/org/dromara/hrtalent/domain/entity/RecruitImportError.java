package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招聘数据导入错误对象 hr_recruit_import_error（设计 §9.2 / §8.11）。
 *
 * <p>逐行逐字段记录，供用户在导入前逐条修正。<b>{@code rawValue} 只存必要片段</b>
 * （截断到 500 字符），不整行回写——导入文件里可能夹带个人信息，审计表不该成为第二份副本。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_import_error")
public class RecruitImportError extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导入错误ID（主键）
     */
    @TableId(value = "error_id")
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
     * 行号（从 1 开始；与用户在 Excel 里看到的行号一致）
     */
    private Integer rowNo;

    /**
     * 出错字段名（表头名）
     */
    private String fieldName;

    /**
     * 原始值（仅存必要片段）
     */
    private String rawValue;

    /**
     * 错误编码（稳定编码，不存中文）
     */
    private String errorCode;

    /**
     * 严重级别（error 阻断 / warning 提示，见 RecruitImportSeverityEnum）
     */
    private String severity;

    /**
     * 错误说明（给用户看得懂的话）
     */
    private String errorMessage;

    /**
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
