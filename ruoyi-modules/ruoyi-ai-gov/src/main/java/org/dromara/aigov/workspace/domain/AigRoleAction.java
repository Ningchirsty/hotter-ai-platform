package org.dromara.aigov.workspace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 岗位能力卡片 {@code aig_role_action}（附件 §4.1、§6.2）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>。</p>
 *
 * <p><b>为什么拆表而不是塞进 manifest_json</b>：门户要"按分类查卡片 / 排序 / 单独启停"，
 * 每次解析 JSON 不现实。而分类（category）留在清单里——数量少、与卡片同版本、
 * 总是整份读取；{@link #categoryCode} 必须能在该版本清单的 categories 里找到（服务层校验）。</p>
 *
 * <p><b>一个 Action 只引用一个确定的启动目标</b>（{@link #targetType} + {@link #targetRef}）：
 * 既能启动场景又能跳页面的话，它到底做了什么就取决于运行时分支，而岗位包是给人配的，
 * 配错了不会报错。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_role_action")
public class AigRoleAction extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力卡片ID
     */
    @TableId(value = "action_id")
    private Long actionId;

    /**
     * 所属岗位版本
     */
    private Long roleVersionId;

    /**
     * 卡片编码（同版本内唯一）
     */
    private String actionCode;

    /**
     * 所属分类（必须存在于该版本清单的 categories 里）
     */
    private String categoryCode;

    /**
     * 卡片标题（业务名，不是 Agent 名）
     */
    private String title;

    /**
     * 卡片说明（适用事项/所需资料/输出内容）
     */
    private String description;

    /**
     * 启动方式（{@code AigActionLaunchModeEnum}）
     */
    private String launchMode;

    /**
     * 目标类型（{@code AigLaunchTargetTypeEnum}）
     */
    private String targetType;

    /**
     * 目标引用（SCENARIO 时是 scenario://code@version；NAVIGATION 时是 routeKey）
     */
    private String targetRef;

    /**
     * STUDIO 模式的专业页跳转键（必须命中 routeKey 白名单）
     */
    private String studioRouteKey;

    /**
     * 所需上下文键（逗号分隔，白名单）
     */
    private String requiredContext;

    /**
     * 排序（同分类内）
     */
    private Integer sortOrder;

    /**
     * 是否启用（Y/N；单独启停一张卡片，不必改整个版本）
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
