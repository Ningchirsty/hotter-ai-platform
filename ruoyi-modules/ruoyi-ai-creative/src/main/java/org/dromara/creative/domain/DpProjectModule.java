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
 * <p><b>谁写它</b>：目前只有模块服务在"分镜生成时按交付类型的默认骨架初始化"（幂等）；
 * 文档 §24 的"模块规划页面"（拖拽排序/增删/启用停用）是后续工作——届时由页面写本表，
 * 仍然要遵守"每次修改留痕"的口径。</p>
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

    @TableLogic
    private String delFlag;

}
