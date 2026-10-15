package org.dromara.aigov.tool.exec;

/**
 * 工具执行器端口（设计 §7.5：工具由注册信息定义 executorType/Schema/operationType/…）。
 *
 * <p>业务工具的执行器位于所属业务域，治理层负责权限与调用记录（设计 §7.1）。
 * 本端口只约定"输入请求 → 结构化结果 + 操作凭证"，调用记录与确认绑定由治理层完成。</p>
 *
 * <p><b>没有注册执行器 = 不能执行</b>：设计 §4.1 要求未接入的工具显示"已登记 待接入"，
 * 因此执行入口在找不到执行器时明确拒绝，而不是回一个空结果。</p>
 *
 * @author ai-gov
 */
public interface ToolExecutor {

    /**
     * 本执行器的引用键（与工具版本的 {@code executor_ref} 对应）。
     *
     * @return 执行器键
     */
    String executorRef();

    /**
     * 执行一次调用。
     *
     * @param request 调用请求
     * @return 结果
     */
    AigToolCallResult execute(AigToolCallRequest request);

}
