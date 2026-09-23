package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可录入事实字段选项。
 *
 * <p>解决一个具体的坑：手工录入原先要求用户<b>手打字段编码</b>，而闸门只认与
 * {@code cp_gate_rule.field_code} 完全一致的值。打错一个字符就得到一条「看起来填了、
 * 实际闸门不认」的事实——静默无效，且没有任何提示。本 VO 供前端把「能填什么」
 * 直接列出来，从源头避免拼错。</p>
 *
 * @author content
 */
@Data
public class ContentFactFieldOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段编码（入库值，必须与 cp_gate_rule.field_code 一致闸门才认账）
     */
    private String fieldCode;

    /**
     * 字段中文名
     */
    private String fieldName;

    /**
     * 闸门等级（BLOCK/CONDITION/NOTICE）；来源为别名表时为 null
     */
    private String gateLevel;

    /**
     * 是否必须存在
     */
    private String requirePresent;

    /**
     * 是否由本交付类型的闸门规则要求（false 表示是别名表里的其他已登记字段）
     */
    private Boolean requiredByGate;

    /**
     * 当前任务下该字段是否已有已确认事实
     */
    private Boolean satisfied;

    /**
     * 是否已登记在别名表（未登记说明该编码不是标准字段）
     */
    private Boolean known;

    /**
     * 给用户看的说明
     */
    private String description;

}
