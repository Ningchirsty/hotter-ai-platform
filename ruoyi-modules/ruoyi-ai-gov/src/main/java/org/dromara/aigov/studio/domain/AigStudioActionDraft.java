package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 快捷指令草案 {@code aig_studio_action_draft}（附件 07 增量 7／ST-007）。
 *
 * <p><b>它是什么</b>：一条面向岗位卡片的轻量指令的草稿——启动方式（{@code launchMode}）与启动目标
 * （{@code targetType}/{@code targetRef}）属于岗位包配置，指令本身的"要做什么/产出什么"属于 Agent 内容。
 * 一张草案同时记这两半，生成时才把它们分开处理。</p>
 *
 * <p><b>为什么启动方式必须是封闭枚举</b>：它决定前端走哪套交互（抽屉/表单/专业工作台/跳转）。
 * 允许自由字符串，岗位包里就能声明一个平台没实现的启动方式，而它的失效表现是"点了没反应"
 * ——排查要从岗位包配置倒着查（同 {@code AigActionLaunchModeEnum} 的理由）。</p>
 *
 * <p><b>为什么 targetType/targetRef 不进生成物</b>：训练台草稿内容里没有"启动目标"这个槽位
 * （那是 {@code aig_role_action} 的列，不是 Agent 内容）。它们留在草案表里，并由生成结果明确告知
 * "这一项没有被写进内容"——岗位束卡片的配置不该被一次生成悄悄丢掉或伪造。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_action_draft")
public class AigStudioActionDraft extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    @TableId(value = "action_draft_id")
    private Long actionDraftId;

    /**
     * 草案编码
     */
    private String draftCode;

    /**
     * 草案名称
     */
    private String draftName;

    /**
     * 指令编码（岗位卡片上的 actionCode）
     */
    private String actionCode;

    /**
     * 指令名称
     */
    private String actionName;

    /**
     * 启动方式（{@code AigActionLaunchModeEnum}：QUICK/FORM/STUDIO/NAVIGATION）
     */
    private String launchMode;

    /**
     * 启动目标类型（{@code AigLaunchTargetTypeEnum}；NAVIGATION 时指受控 routeKey 之外的目标）
     */
    private String targetType;

    /**
     * 启动目标引用（版本ID / 能力编码 / routeKey）
     */
    private String targetRef;

    /**
     * Agent 类别（必填；生成物是一份 Agent 内容）
     */
    private String agentCategory;

    /**
     * 指令目标（进内容的 {@code objective}）
     */
    private String objective;

    /**
     * 产出要求（进内容的 {@code output_contract} 分节）
     */
    private String outputContract;

    /**
     * 所需能力（路由只认能力；必填）
     */
    private String providerCapability;

    /**
     * 允许工具（逗号分隔；可空）
     */
    private String allowedTools;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

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
