package org.dromara.aigov.workspace.launch.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 启动请求入参（主文档线增量 3；prepare 与 commit 共用）。
 *
 * <h3>幂等键由调用方给出，且必须由调用方复用</h3>
 * <p>{@code idempotencyKey} 是"这次启动"的标识：客户端**重试必须复用同一个键**，
 * 用户改了输入重新提交则必须换新键。键相同而内容不同会被判为
 * {@code IDEMPOTENCY_CONFLICT}（见 {@code AigLaunchRequestDigest}）。</p>
 *
 * <h3>为什么任务描述字段放在请求里，而不是从卡片读</h3>
 * <p>{@code aig_role_action} 没有"任务类型"这一列。加这一列会改变**已发布版本**的清单结构，
 * 而版本是发布后不可变的（要加就得走一次独立的 schema 变更 + 版本迁移）。
 * 因此本次由调用方声明任务类型，并对它做**封闭集合校验**
 * （拿平台不认识的类型去建任务，表现是"任务建出来了但永远不动"，比当场拒绝难查得多）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigLaunchRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位编码
     */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 80, message = "岗位编码长度不能超过 80")
    private String roleCode;

    /**
     * 卡片编码
     */
    @NotBlank(message = "卡片编码不能为空")
    @Size(max = 64, message = "卡片编码长度不能超过 64")
    private String actionCode;

    /**
     * 幂等键（客户端生成；重试必须复用，改内容必须换新键）
     */
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过 64")
    private String idempotencyKey;

    /**
     * 启动票据（commit 必填；prepare 不传）
     */
    @Size(max = 64, message = "票据长度不合法")
    private String ticket;

    /**
     * 任务类型（要建任务时必填，须是封闭集合里的取值）
     */
    @Size(max = 32, message = "任务类型长度不能超过 32")
    private String taskType;

    /**
     * 业务域（要建任务时必填）
     */
    @Size(max = 32, message = "业务域长度不能超过 32")
    private String projectType;

    /**
     * 业务项目ID（可空；为空表示不挂项目）
     */
    private Long projectId;

    /**
     * 数据等级（要建任务时必填）
     */
    @Size(max = 32, message = "数据等级长度不能超过 32")
    private String dataLevel;

    /**
     * 输入快照（要建任务时必填；平台按它重放这次输入）
     */
    private String snapshotJson;

    /**
     * 上下文键值（卡片声明的 requiredContext 都在这里给值）
     */
    private Map<String, String> context;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
