package org.dromara.aigov.execution;

import java.math.BigDecimal;

/**
 * Agent 执行计划解析器（设计 §7.1「AgentExecutionResolver 建议新增」）。
 *
 * <p>它把「登录身份 + 目标版本 + 结构化输入 + 有效策略」解析成一份
 * {@link AigExecutionPlan}：正式运行据此编译 Prompt、确定能力、外发许可与资源范围，
 * 客户端提交的 Prompt 不再作为系统指令。</p>
 *
 * @author ai-gov
 */
public interface IAigAgentExecutionResolver {

    /**
     * 为一个任务解析执行计划。
     *
     * <p><b>返回 {@code null} 表示"不是 Agent 执行模式"</b>：目标版本为空，或该版本的
     * {@code config_json} 不是 v2 结构化配置（没有 {@code schemaVersion >= 2}）。
     * 此时调用方按既有业务适配器运行——这与设计 §8.3「一个旧版本未完成迁移仍可按原业务
     * 适配器运行，但不得宣称已启用新知识或工具运行机制」一致，也保证本能力上线
     * <b>不改变任何存量版本的行为</b>。</p>
     *
     * @param taskId           任务ID（非任务调用可空）
     * @param agentVersionId   目标 Agent 版本ID（可空）
     * @param dataLevel        本次数据等级（来自任务登记，不由客户端在此覆盖）
     * @param inputSnapshotJson 输入快照原文（可空；用于计算输入哈希）
     * @param businessInput    业务输入（可空；作为系统 Prompt 之后的独立分节）
     * @param maxCost          本次预算（美元，可空）
     * @param purpose          运行用途
     * @return 执行计划；非 Agent 模式返回 null
     * @throws org.dromara.common.core.exception.ServiceException 版本不存在，或受限通道下无有效绑定
     */
    AigExecutionPlan resolve(Long taskId, Long agentVersionId, String dataLevel,
                             String inputSnapshotJson, String businessInput, BigDecimal maxCost,
                             AigExecutionPurposeEnum purpose);

}
