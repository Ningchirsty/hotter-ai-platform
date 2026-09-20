package org.dromara.aigov.service.invoker;

import org.dromara.aigov.enums.AigDeploymentTypeEnum;

/**
 * 模型调用 SPI。
 * <p>治理层不直接绑死任何模型供应商：路由引擎按 {@link #supports(AigDeploymentTypeEnum)}
 * 筛选调用器，调用编排按 {@link #available()} 判定其是否就绪。</p>
 * <p>阶段1 的两个实现：{@code SnailAiChatInvoker}（集团/外部，走 snail-ai）与
 * {@code LocalRuleModelInvoker}（本地规则，用于 {@code talent_match}）。
 * snail-ai 未启用时治理层仍可编译、可启动、可端到端验证。</p>
 *
 * @author ai-gov
 */
public interface ModelInvoker {

    /**
     * 支持的部署类型；路由据此筛选 invoker。
     *
     * @param deploymentType 部署类型
     * @return 是否支持
     */
    boolean supports(AigDeploymentTypeEnum deploymentType);

    /**
     * 该 invoker 是否可用（Bean 存在且配置就绪）。
     *
     * @return 是否可用
     */
    boolean available();

    /**
     * 执行一次调用。
     * <p><b>不得抛出异常</b>：任何失败都转成 {@link ModelInvokeResult#failure}。</p>
     *
     * @param request 调用请求
     * @return 调用结果
     */
    ModelInvokeResult invoke(ModelInvokeRequest request);

    /**
     * 调用器名称（写入路由决策 {@code invoker} 字段，便于排障）。
     *
     * @return 调用器名称
     */
    default String invokerName() {
        return getClass().getSimpleName();
    }

}
