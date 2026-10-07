package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * snail-ai 的 Agent（{@code sai_agent}）只读视图——用于「模型 ↔ Agent」精确映射。
 *
 * <p><b>为什么只读这一张表</b>：snail-ai 的聊天入口收的是 {@code agentId}，
 * 而「这个 Agent 实际跑哪个模型」写在 {@code sai_agent.chat_model_id} 上。
 * 治理层要选定模型、又要经该链路执行，就必须能读到这个映射；
 * 但 Agent 的创建/编辑仍归 snail-ai——治理层不越界去改别人的主数据。</p>
 *
 * <p>只取定位与判定需要的列：{@code id}（要传给聊天入口）、{@code name}（可读报错）、
 * {@code chat_model_id}（映射依据）、{@code app_id}（作用域：NULL=在 snail-ai 本地执行）、
 * {@code status}（1-活跃 2-非活跃 3-已废弃 4-已禁用）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigSnailAgentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent ID（{@code sai_agent.id}，即要传给聊天入口的 agentId）
     */
    private Long id;

    /**
     * Agent 名称（仅用于可读的报错与日志）
     */
    private String name;

    /**
     * 关联的对话模型ID（{@code sai_model_config.id}）——模型 ↔ Agent 的映射依据
     */
    private Long chatModelId;

    /**
     * 关联应用ID；<b>NULL 表示在 snail-ai 本地执行</b>
     */
    private String appId;

    /**
     * 状态：1-活跃 2-非活跃 3-已废弃 4-已禁用
     */
    private Integer status;

}
