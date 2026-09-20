package org.dromara.content.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.content.domain.CpGateRule;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 闸门规则视图对象 cp_gate_rule
 *
 * @author content
 */
@Data
@AutoMapper(target = CpGateRule.class)
public class CpGateRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 交付类型
     */
    private String deliverableType;

    /**
     * 要求确认的事实字段编码
     */
    private String fieldCode;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 闸门等级
     */
    private String gateLevel;

    /**
     * 是否必须存在（Y/N）
     */
    private String requirePresent;

    /**
     * 是否启用（0启用 1停用）
     */
    private String enabled;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID（{@code @Translation} 的取值来源，**不可省略**）
     */
    private Long createBy;

    /**
     * 创建人账号
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
