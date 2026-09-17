package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlParseField;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 解析字段视图对象 tl_parse_field
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlParseField.class)
public class TlParseFieldVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段ID
     */
    private Long fieldId;

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
     * 确认人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "confirmBy")
    private String confirmByName;

    /**
     * 确认时间
     */
    private LocalDateTime confirmTime;

}
