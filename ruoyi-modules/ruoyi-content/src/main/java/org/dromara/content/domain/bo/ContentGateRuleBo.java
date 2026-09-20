package org.dromara.content.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.content.domain.CpGateRule;

import java.io.Serial;
import java.io.Serializable;

/**
 * 闸门规则业务对象 cp_gate_rule
 *
 * @author content
 */
@Data
@AutoMapper(target = CpGateRule.class, reverseConvertGenerate = false)
public class ContentGateRuleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @NotNull(message = "规则ID不能为空", groups = {EditGroup.class})
    private Long ruleId;

    /**
     * 交付类型
     */
    @NotBlank(message = "交付类型不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 32, message = "交付类型长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String deliverableType;

    /**
     * 要求确认的事实字段编码
     */
    @NotBlank(message = "事实字段编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "事实字段编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String fieldCode;

    /**
     * 字段名称
     */
    @Size(max = 128, message = "字段名称长度不能超过 128", groups = {AddGroup.class, EditGroup.class})
    private String fieldName;

    /**
     * 闸门等级（BLOCK/CONDITION/NOTICE）
     */
    @NotBlank(message = "闸门等级不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 16, message = "闸门等级长度不能超过 16", groups = {AddGroup.class, EditGroup.class})
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
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

    // ---------------- 查询条件 ----------------

    /**
     * 交付类型（查询）
     */
    @Size(max = 32, message = "交付类型长度不能超过 32", groups = {QueryGroup.class})
    private String queryDeliverableType;

    /**
     * 分页参数容器
     */
    private java.util.Map<String, Object> params;

}
