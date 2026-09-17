package org.dromara.talent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 解析字段复核对象 tl_parse_field
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_parse_field")
public class TlParseField extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段ID
     */
    @TableId(value = "field_id")
    private Long fieldId;

    /**
     * 解析任务ID
     */
    private Long taskId;

    /**
     * 字段名（对应 tl_talent 字段）
     */
    private String fieldName;

    /**
     * 解析原始值
     */
    private String parsedValue;

    /**
     * 置信度 0-1
     */
    private BigDecimal confidence;

    /**
     * 人工确认值
     */
    private String confirmedValue;

    /**
     * 确认状态（0待确认 1已确认 2已忽略）
     */
    private String confirmStatus;

    /**
     * 确认人
     */
    private Long confirmBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmTime;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
