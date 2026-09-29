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
 * 项目模块计划 dp_project_module（V0.2 R21，文档 §18/§21）。
 *
 * <p><b>它是"这个项目的分镜有哪些屏"的唯一出处</b>：分镜生成时按本表顺序、每行 {@code screenCount} 屏
 * 展开成 Screen Plan（文档 §21：一个模块占 1..N 屏，{@code screen_no} 保留但不再假设总数为 7）。</p>
 *
 * <p><b>谁写它</b>：① 模块服务在"分镜生成时按交付类型的默认骨架初始化"（幂等）；
 * ② 文档 §24 的模块规划页（R22）通过 {@code PUT /creative/v2/projects/{taskId}/module-plan}
 * 整份保存——**只有一个写入点**，且保存前会检查"分镜已锁定 / 已出图 / 已渲染"这类不可逆状态并 refuse
 * （见 {@code CreativeModuleServiceImpl#savePlan}）。改计划不是阶段变更，因此**不写 dp_stage_event**；
 * 留痕靠旧行软删（del_flag=1）+ update_by/update_time。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_project_module")
public class DpProjectModule extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 视觉项目（cp_task.task_id）
     */
    private Long taskId;

    /**
     * 模块编码（dp_module_definition.module_code）
     */
    private String moduleCode;

    /**
     * 模块名（快照）
     */
    private String moduleName;

    /**
     * 屏类型（快照）
     */
    private String screenType;

    /**
     * 本模块占几屏
     */
    private Integer screenCount;

    /**
     * 项目里的顺序
     */
    private Integer sortNo;

    /**
     * 状态（PLANNED已计划 / CONFIRMED已确认）
     */
    private String status;

    /**
     * 来源（DEFAULT按交付类型初始化 / MANUAL人工调整）
     */
    private String source;

    /**
     * 备注
     */
    private String remark;

    // ---------------- 文档 §24 的右栏字段（R22） ----------------

    /**
     * 模块目标（可覆盖模块定义里的 objective；空则用定义的）
     */
    private String objective;

    /**
     * 对应卖点（文案块编码，逗号分隔）
     */
    private String sellingPointCodes;

    /**
     * 人工文案（最高优先级：写了就用它，覆盖草稿与模型产出）
     */
    private String copyText;

    /**
     * 所需事实（事实字段码，逗号分隔；缺哪个会在模块规划页如实标出来）
     */
    private String requiredFactCodes;

    /**
     * 视觉表达（JSON 文本，例如 {"tone":"...","props":["..."]}）
     */
    private String visualRulesJson;

    /**
     * 参考图（附件文件ID或编码，逗号分隔）
     */
    private String referenceCodes;

    /**
     * Workflow/能力编码（逗号分隔；**取第一个**作为该模块出图的工作流）
     */
    private String workflowCodes;

    /**
     * 模板码（逗号分隔）
     */
    private String templateCodes;

    /**
     * 是否启用（0启用 1停用；停用的模块不出屏，但留在计划里可再启用）
     */
    private String enabled;

    @TableLogic
    private String delFlag;

}
