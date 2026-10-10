package org.dromara.aigov.workspace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 场景包版本 {@code aig_scenario_version}（附件 §4.1：场景引用优先锁定不可变版本）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_role_workspace.sql} 的列注释为准</b>。</p>
 *
 * <p><b>{@link #releaseStatus} 沿用既有发布状态机的取值</b>（{@code AigReleaseStatusEnum}）：
 * F-10 已冻"场景/岗位的审批与发布复用既有"，所以这里<b>不另立一套状态</b>、也不新造审批表。</p>
 *
 * <p><b>{@link #workflowAdapter} 是封闭枚举</b>（{@code AigScenarioAdapterEnum}）：它只回答
 * "这次交给谁去跑"，不定义节点/连线。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_scenario_version")
public class AigScenarioVersion extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景版本ID
     */
    @TableId(value = "scenario_version_id")
    private Long scenarioVersionId;

    /**
     * 所属场景
     */
    private Long scenarioId;

    /**
     * 版本号（同场景内唯一，SemVer 风格）
     */
    private String version;

    /**
     * 发布状态（沿用既有发布状态机取值）
     */
    private String releaseStatus;

    /**
     * 输入 Schema 引用
     */
    private String inputSchemaRef;

    /**
     * 输出 Schema 引用
     */
    private String outputSchemaRef;

    /**
     * 流程适配器（{@code AigScenarioAdapterEnum}）
     */
    private String workflowAdapter;

    /**
     * 结果页跳转键（必须命中 routeKey 白名单）
     */
    private String routeKey;

    /**
     * 输入/结果 UI 描述（页面定制只接受安全组件白名单）
     */
    private String uiJson;

    /**
     * 所需能力编码（逗号分隔）
     */
    private String requiredCapabilities;

    /**
     * 质量审批清单（逗号分隔）
     */
    private String qualityApprovals;

    /**
     * 版本内容引用（不可变制品引用）
     */
    private String bodyRef;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

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
     * 版本说明
     */
    private String remark;

}
