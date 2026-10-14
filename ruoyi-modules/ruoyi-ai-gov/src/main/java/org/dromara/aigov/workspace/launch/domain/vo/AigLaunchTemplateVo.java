package org.dromara.aigov.workspace.launch.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动任务模板视图（设计 D11）。
 *
 * <p>给前端渲染<b>业务表单</b>用：{@link #businessFields} 是员工要填的业务字段
 * （SKU、市场、渠道…），而任务类型/业务域/数据等级属于服务端推导的执行字段，
 * 只作为说明展示，不由员工填写。</p>
 *
 * @author ai-gov
 */
@Data
public class AigLaunchTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板编码
     */
    private String templateCode;

    /**
     * 业务标题
     */
    private String title;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 卡片编码（空=岗位默认模板）
     */
    private String actionCode;

    /**
     * 服务端推导的任务类型（展示用）
     */
    private String taskType;

    /**
     * 服务端推导的业务域（展示用）
     */
    private String projectType;

    /**
     * 服务端推导的数据等级（展示用；服务端结论，员工不可放宽）
     */
    private String dataLevel;

    /**
     * 员工要填的业务字段（上下文键）
     */
    private List<String> businessFields = new ArrayList<>();

    /**
     * 是否用业务输入自动组装输入快照
     */
    private Boolean snapshotFromContext;

}
