package org.dromara.aigov.service.invoker;

import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigProviderTypeEnum;

/**
 * 模型调用 SPI。
 * <p>治理层不直接绑死任何模型供应商：路由引擎按 {@link #supports(AigDeploymentTypeEnum)}
 * 筛选调用器，调用编排按 {@link #available()} 判定其是否就绪。</p>
 *
 * <p><b>按部署类型认领，且每种类型只有一个调用器认领</b>——否则挑选结果会依赖
 * Spring Bean 的装配顺序，是不确定的。当前分工：</p>
 * <ul>
 *     <li>{@code LOCAL} → {@code LocalRuleModelInvoker}（{@code talent_match}）、
 *         {@code ContentLocalInvoker}（内容生产的若干能力）</li>
 *     <li>{@code GROUP} / {@code EXTERNAL_ENTERPRISE} → {@code SnailAiChatInvoker}（集团 snail-ai 链路）</li>
 *     <li>{@code EXTERNAL_API} → {@code OpenAiCompatibleInvoker}（按治理台登记直连端点）</li>
 * </ul>
 * <p>各实现都未启用时治理层仍可编译、可启动、可端到端验证。</p>
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
     * 是否专门处理某个业务能力编码。
     *
     * <p><b>存在的理由</b>：仅凭部署类型不足以挑出正确的本地调用器。同一种部署类型
     * （如 {@code LOCAL}）下会有多个调用器分别实现不同能力——人才匹配、资料解析、
     * 资料预检各是一个。若不看能力编码，路由只能取「列表里第一个可用的」，
     * 结果依赖 Spring Bean 的装配顺序，是不确定的。</p>
     *
     * <p>路由的挑选口径是「两段式」：先找<b>声明处理该能力</b>的调用器，
     * 找不到再退回「只按部署类型匹配」的调用器。因此本方法返回 false（默认值）
     * 不会让现有调用器失效——它仍然能在第二段被选中。</p>
     *
     * @param capabilityCode 业务能力编码（如 talent_match / document_parse）
     * @return 是否声明处理该能力
     */
    default boolean supportsCapability(String capabilityCode) {
        return false;
    }

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

    /**
     * 归类一次失败的调用结果。
     *
     * <p><b>默认实现直接委托给 {@code AigErrorClassEnum.classify}</b>（errorCode → HTTP 状态 →
     * 文本兜底）。调用器只有在「自己知道得比通用规则更准」时才需要覆写——
     * 例如直连调用器明确拿到 429 时，比起让通用规则去猜文案，直接把
     * {@code ModelInvokeResult.errorCode/httpStatus} 填上即可，无需覆写本方法。</p>
     *
     * <p>这决定了后续是「退避重试」「转人工」还是「熔断 Provider」，
     * 因此实现必须诚实：**认不出就返回 UNKNOWN，不要猜成可重试**。</p>
     *
     * @param result 调用结果（应为失败结果；成功结果也会被安全处理）
     * @return 错误分类，恒不为 null
     */
    default AigErrorClassEnum classifyError(ModelInvokeResult result) {
        if (result == null) {
            return AigErrorClassEnum.UNKNOWN;
        }
        return AigErrorClassEnum.classify(result.getErrorCode(), result.getHttpStatus(), result.getErrorSummary());
    }

    /**
     * 声明本调用器认领哪些 <b>模型类型</b>（{@code sai_model_config.model_type}）。
     *
     * <p><b>为什么需要它</b>：{@link #supports(AigDeploymentTypeEnum)} 只按部署类型认领，
     * 而每种部署类型只能有一个认领者。外部聚合网关打破了这条前提——同一个
     * {@code EXTERNAL_API} 下既有对话模型（{@code /v1/chat/completions}）又有图像模型
     * （{@code /v1/images/generations}），端点与请求体都不同。若再写一个也认领
     * {@code EXTERNAL_API} 的调用器，挑选结果就取决于 Spring Bean 装配顺序了。</p>
     *
     * <p>加上这一维后，不变式放宽为「每种 <b>(部署类型, 模型类型)</b> 只有一个认领者」，
     * 既仍确定，又能容纳同一网关下的多种能力。</p>
     *
     * <p><b>默认实现返回 true（全部放行）</b>，以保证既有调用器一行不改仍然工作；
     * 需要区分的调用器（对话 vs 图像）必须显式覆写。注意默认放行意味着
     * 「多个调用器都可能认领同一种模型类型」，因此<b>凡是要与其它调用器共处同一部署类型的
     * 新调用器，都必须覆写本方法</b>，否则不确定性会重新出现。</p>
     *
     * @param modelType 模型类型（可为空；调用器需自行决定空值是否放行）
     * @return 是否认领
     */
    default boolean supportsModelType(String modelType) {
        return true;
    }

    /**
     * Provider 能力类型（用于决策说明与排障展示，不参与派发）。
     *
     * <p>派发依据是 {@link #supportsModelType(String)}——它对应数据库里真实存在的
     * {@code model_type} 列；本方法只是给人看的一句话标签，默认 {@code UNKNOWN}
     * 表示「该调用器未声明」，不影响任何行为。</p>
     *
     * @return Provider 类型
     */
    default AigProviderTypeEnum providerType() {
        return AigProviderTypeEnum.UNKNOWN;
    }

}
