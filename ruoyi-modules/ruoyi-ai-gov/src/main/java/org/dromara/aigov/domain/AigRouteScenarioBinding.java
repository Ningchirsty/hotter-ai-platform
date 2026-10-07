package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 场景强制绑定对象 aig_route_scenario_binding
 *
 * <p><b>它回答的问题</b>：设计 §4.4 路由算法第 4 步——「若场景强制绑定 Provider，
 * 例如企业设计平台编辑会话，则仅保留指定 Provider」。即某些场景下，模型选择<b>不该</b>
 * 由优先级/质量分自由裁决，而是被业务约定钉死在某家供应商上。</p>
 *
 * <p><b>为什么单独一张表，而不是给 {@code aig_route_policy} 加列</b>：
 * 策略表是「能力 × 数据等级」的治理口径（能否外发、是否要审批、无模型时是否转人工），
 * 它的唯一键就是这两列。把场景塞进去，要么破坏唯一键，要么为每个场景复制一整行
 * 并把 {@code allow_external} 等治理口径重述一遍——只要有一次漏填或填错，
 * 该场景的路由判定就<b>静默</b>变成了另一个结论（而「无策略即拒绝」意味着连
 * 「没配」都不会报错，只会拒）。这种「配了绑定就顺带改了外发禁令」的耦合是必须避免的。</p>
 *
 * <p>单独成表后，本表的语义被刻意限制为<b>只收紧</b>：它只从已经判定可用的候选里
 * 再筛掉不属于指定供应商的那些，<b>永远不会</b>让一个本来被策略排除的模型变得可用，
 * 也不改变任何治理字段。因此「多配一条绑定」最坏的结果是「没模型可用 → 转人工/拒绝」，
 * 不可能变成「数据发出去了」。</p>
 *
 * <p><b>与设计 §10 的对应</b>：设计里 {@code ai_provider_route} 把 {@code scenario_code}
 * 与 {@code provider_id} 放在同一张路由表上；本表即该意图在既有
 * {@code aig_route_policy} / {@code aig_capability_model} 体系下的落点——
 * 保留「场景 → 供应商」这一对关系，但把治理判定留在原表。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_route_scenario_binding")
public class AigRouteScenarioBinding extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    @TableId(value = "bind_id")
    private Long bindId;

    /**
     * 场景编码（LONG_PAGE、POSTER、MULTI_IMAGE 等）
     */
    private String scenarioCode;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 强制使用的供应商ID（{@code sai_model_provider.id}）
     */
    private Long providerId;

    /**
     * 同一「场景 × 能力」下多个供应商时的优先序（升序）
     * <p>有值只是为了让多个被允许的供应商之间保持一个<b>稳定</b>的顺序，
     * 真正的取舍仍由 {@code aig_capability_model} 的用途与优先级决定。</p>
     */
    private Integer priority;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注（说明为什么要钉死这家，便于事后复核）
     */
    private String remark;

}
