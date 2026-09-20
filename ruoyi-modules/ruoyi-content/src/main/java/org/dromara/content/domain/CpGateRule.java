package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 闸门规则对象 cp_gate_rule
 *
 * <p>表驱动（已确认基线 B7）：设计文档 §8.2 的强制项清单会随交付类型与试点推进而变化，
 * 硬编码会导致每次调整都要发版。运营可在「闸门规则」页维护。</p>
 *
 * @author content
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cp_gate_rule")
public class CpGateRule extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @TableId(value = "rule_id")
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
     * 闸门等级（BLOCK/CONDITION/NOTICE，见 ContentGateLevelEnum）
     */
    private String gateLevel;

    /**
     * 是否必须存在（Y=无候选也算未满足）
     */
    private String requirePresent;

    /**
     * 是否启用（0启用 1停用，与 sys_menu.status 同口径）
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
     * 删除标志（0存在 1删除）
     */
    @TableLogic
    private String delFlag;

}
