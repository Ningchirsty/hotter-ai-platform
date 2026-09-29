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
 * 场景步骤 dp_scenario_step（V0.2 B1）：把"流程"从代码里搬进配置。
 *
 * <p>{@link #stageCodes} 是**投影**：现有阶段机（{@code DpVisualStageEnum} + 单一写入点
 * {@code CreativeProjectServiceImpl#moveStage}）仍是阶段与合法性的权威，
 * 这里只是声明"这个配置步骤对应哪几个现有阶段"，供工作台显示与 B2 迁移使用——
 * 阶段合法性（canMoveTo）**故意不配置化**（对照文档 D3）。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_scenario_step")
public class DpScenarioStep extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 所属场景档案（dp_scenario_profile.id）
     */
    private Long profileId;

    /**
     * 步骤编码（INPUT/FACT/DNA/DIRECTION/STORYBOARD/GATE/GENERATION/QA/LAYOUT/FINAL）
     */
    private String stepCode;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 步骤类型（文档 §8 的 Step Type）
     */
    private String stepType;

    /**
     * 映射到现有阶段机的阶段编码（逗号分隔；投影用，不是权威）
     */
    private String stageCodes;

    /**
     * 顺序
     */
    private Integer sortNo;

    /**
     * 是否必需（1必需 0可选）
     */
    private String required;

    /**
     * 闸门类型（如 VISUAL_GATE / FACT_CONFIRMED；空=无闸门）
     */
    private String gateType;

    /**
     * 依赖的治理能力编码（如 visual_dna_extract；空=不调模型）
     */
    private String capabilityCode;

    /**
     * 工作台组件名（前端注册表里的键，如 VisualDnaPanel）
     */
    private String workspaceComponent;

    /**
     * 进入条件（结构化）
     */
    private String entryConditionJson;

    /**
     * 完成判定（结构化）
     */
    private String completionRuleJson;

    /**
     * 其它配置（实现状态等）
     */
    private String configJson;

    @TableLogic
    private String delFlag;

}
