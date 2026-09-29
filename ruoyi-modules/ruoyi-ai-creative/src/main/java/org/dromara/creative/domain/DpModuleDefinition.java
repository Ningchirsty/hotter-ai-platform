package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模块定义 dp_module_definition（V0.2 R21，文档 §18/§20）。
 *
 * <p><b>它解决什么</b>：分镜的"屏集合"原先是一份**进程级契约文件**（creative/screen-skeleton.json），
 * 所有交付类型共用；现在改成"每个交付类型一套模块库"（本表），项目再从中挑出自己的一份计划
 * （{@link DpProjectModule}）——文档 §21 的 Module Plan → Screen Plan。</p>
 *
 * <p><b>字段口径</b>（对齐文档 §20）：{@code objective} 是模块目标，{@code minScreens/maxScreens}
 * 是它占几屏，{@code allowedTemplates/allowedWorkflows/requiredFacts/visualRulesJson/qaRulesJson}
 * 是它的约束。{@code screenType/productLockLevel/shot} 是**渲染期要用的最小信息**：
 * 与契约文件同名字段一一对应，因此把默认骨架镜像成模块后，生成结果**逐屏一致**（单测等价性钉住）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_module_definition")
public class DpModuleDefinition extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 交付类型（不同场景不同模块库）
     */
    private String deliveryType;

    /**
     * 模块编码（HERO/SELLING_POINT/MAIN_WHITE_BG…）
     */
    private String moduleCode;

    /**
     * 模块名（多屏时作为前缀：卖点 → 卖点一/卖点二）
     */
    private String moduleName;

    /**
     * 模块目标（文档 §20 objective）
     */
    private String objective;

    /**
     * 生成的屏类型（进提示词与草稿工厂）
     */
    private String screenType;

    /**
     * 产品保真等级（STRICT/LOOSE）
     */
    private String productLockLevel;

    /**
     * 取景（可用 {ratio} 占位产品占比区间）
     */
    private String shot;

    /**
     * 是否必需（1必需 0可选）
     */
    private String required;

    /**
     * 最少屏数
     */
    private Integer minScreens;

    /**
     * 最多屏数
     */
    private Integer maxScreens;

    /**
     * 是否进入默认骨架（1是 0否=可选模块库）
     */
    private String defaultSelected;

    /**
     * 默认骨架里的顺序
     */
    private Integer defaultSortNo;

    /**
     * 允许的模板码（逗号分隔）
     */
    private String allowedTemplates;

    /**
     * 允许的工作流/能力码（逗号分隔）
     */
    private String allowedWorkflows;

    /**
     * 需要的事实字段（逗号分隔）
     */
    private String requiredFacts;

    /**
     * 视觉规则（JSON 文本）
     */
    private String visualRulesJson;

    /**
     * 质检规则（JSON 文本）
     */
    private String qaRulesJson;

    /**
     * 是否启用（0启用 1停用）
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
