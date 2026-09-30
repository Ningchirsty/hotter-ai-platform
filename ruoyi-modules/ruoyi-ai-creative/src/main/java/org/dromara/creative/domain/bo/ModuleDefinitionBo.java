package org.dromara.creative.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模块定义（交付类型级模块库）的新增/编辑请求（V0.2 R27，文档 §24 左栏真正可编辑）。
 *
 * <p>为什么不用实体直接收请求：实体带着 id/del_flag/createBy 这些不该由页面决定的东西，
 * 收实体等于把"随手改主键/软删标志"的口子开着。这里只列**可编辑字段**，
 * 其余（id 走路径参数、审计字段由框架填）一律不由请求决定。</p>
 *
 * @author creative
 */
@Data
public class ModuleDefinitionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 交付类型（新增必填；编辑时不允许改——改类型等于换一套模块库） */
    private String deliveryType;

    /** 模块编码（同交付类型内唯一） */
    private String moduleCode;

    /** 模块名（多屏时作为"卖点一/卖点二"的前缀） */
    private String moduleName;

    /** 模块目标（§20 objective） */
    private String objective;

    /** 生成的屏类型（进提示词与草稿工厂） */
    private String screenType;

    /** 产品保真等级（STRICT/LOOSE） */
    private String productLockLevel;

    /** 取景（可用 {ratio} 占位产品占比区间） */
    private String shot;

    /** 是否必需（1必需 0可选） */
    private String required;

    /** 最少屏数 */
    private Integer minScreens;

    /** 最多屏数 */
    private Integer maxScreens;

    /** 是否进入默认骨架（1是 0否） */
    private String defaultSelected;

    /** 默认骨架里的顺序 */
    private Integer defaultSortNo;

    /** 允许的模板码（逗号分隔） */
    private String allowedTemplates;

    /** 允许的工作流/能力码（逗号分隔） */
    private String allowedWorkflows;

    /** 需要的事实字段（逗号分隔） */
    private String requiredFacts;

    /** 视觉规则（JSON 文本） */
    private String visualRulesJson;

    /** 质检规则（JSON 文本） */
    private String qaRulesJson;

    /** 是否启用（0启用 1停用） */
    private String enabled;

    /** 备注 */
    private String remark;
}
