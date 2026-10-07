package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent 定义 aig_agent（设计 §5.1、§5.2）。
 *
 * <p><b>Agent 不是一段代码，而是一条可版本的配置记录</b>：本表只回答「有这么个 Agent」，
 * 具体行为全部在 {@code aig_agent_version} 上。{@link #category} 取
 * {@code AigAgentCategoryEnum}，首期的四个内置 Agent 与设计 §5.2 的表格一一对应。</p>
 *
 * <p><b>{@link #builtin} 用来区分「平台自带」与「第三方 Package 带入」</b>：
 * 前者随平台发布、升级跟随平台版本；后者受 Package 的发布状态机管辖（可停用、可回滚）。
 * 两者在界面上要能分开，否则运维无法判断「这个 Agent 是谁的、出问题该找谁」。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_agent")
public class AigAgent extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent ID
     */
    @TableId(value = "agent_id")
    private Long agentId;

    /**
     * Agent 编码（唯一，跨版本稳定）
     */
    private String agentCode;

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * 类别（{@code AigAgentCategoryEnum}：PLANNING/VISUAL_DNA/GENERATION/QA）
     */
    private String category;

    /**
     * 负责人（业务 Owner）
     */
    private Long ownerId;

    /**
     * 是否平台内置（Y=随平台发布，N=第三方 Package 带入）
     */
    private String builtin;

    /**
     * 说明（做什么、不做什么）
     */
    private String description;

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
