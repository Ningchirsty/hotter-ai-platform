package org.dromara.aigov.workspace.launch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 启动任务模板 aig_launch_template（设计 D11、§6.8「模板化任务表单，服务端推导执行字段」）。
 *
 * <p><b>它解决什么</b>：员工启动一张卡片时，过去要自己填 <b>任务类型 / 业务域 / 数据等级 /
 * 原始上下文键</b>——这些是平台实现概念，员工不该、也不容易填对（填错的表现是"任务建出来了
 * 却永远不动"）。模板把这些"执行字段"绑定到「岗位 + 卡片」上，由服务端推导；
 * 员工只填业务输入（SKU、市场、渠道…）。</p>
 *
 * <p><b>为什么是独立表而不是给 {@code aig_role_action} 加列</b>：岗位版本发布后内容不可变
 * （要加列就得走一次独立的 schema 变更 + 版本迁移）。模板是平台运营侧的配置，独立成表后
 * 可以随时增改而不触碰已发布版本，符合设计"保留机制、增量为辅"的约束。</p>
 *
 * <p><b>匹配口径</b>：{@code (role_code, action_code)} 精确匹配优先；{@code action_code} 为空的行
 * 是该岗位的默认模板（该岗位下所有卡片共用）。二者都没有时不套用模板，
 * 行为与过去完全一致（向后兼容）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_launch_template")
public class AigLaunchTemplate extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    @TableId(value = "template_id")
    private Long templateId;

    /**
     * 模板编码（唯一，便于运营引用）
     */
    private String templateCode;

    /**
     * 岗位编码
     */
    private String roleCode;

    /**
     * 卡片编码（为空=该岗位的默认模板）
     */
    private String actionCode;

    /**
     * 模板标题（员工看到的业务名）
     */
    private String title;

    /**
     * 服务端推导的任务类型（{@code AigTaskTypeEnum} 的 code）
     */
    private String taskType;

    /**
     * 服务端推导的业务域
     */
    private String projectType;

    /**
     * 服务端推导的数据等级（{@code AigDataLevelEnum} 的 code；服务端结论，员工不可放宽）
     */
    private String dataLevel;

    /**
     * 暴露给员工填写的业务字段（逗号分隔的上下文键，如 {@code sku,market,channel}）
     */
    private String businessFields;

    /**
     * 是否用业务输入自动组装输入快照（Y/N）
     */
    private String snapshotFromContext;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
