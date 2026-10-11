package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.enums.AigScenarioAdapterEnum;

/**
 * 场景包"交给谁去跑"的分发口径（主文档线增量 9）。
 *
 * <h3>它补的是哪条断链</h3>
 * <p>{@code aig_scenario_version.workflow_adapter} 是"这次交给哪条既有链路"的封闭枚举
 * （创作域阶段机 / 视频链路 / 内容链路 / 不挂流程）。但在它被真正用上之前，一个
 * <b>没登记或写错</b>适配器的场景版本照样能通过启动校验、建出任务——表现就是枚举文档
 * 警告过的那句"卡片能点、任务起不来"，而排查要从场景配置倒着查。</p>
 *
 * <p>这里只做两件纯逻辑的事：<b>归一化</b>（认不出就是不可用，绝不猜）与
 * <b>映射到下游执行方</b>（可读编码，落日志/审计用）。它<b>不是</b>流程引擎——
 * 通用 Workflow DSL 与真正的跨模块执行派发是后续独立变更。</p>
 *
 * @author ai-gov
 */
public final class AigScenarioDispatch {

    /**
     * 下游执行方（"这次交给谁跑"的可读编码）
     */
    public enum Target {
        /**
         * 创作域既有阶段机
         */
        CREATIVE,
        /**
         * 视频创作链路
         */
        VIDEO,
        /**
         * 内容生产链路
         */
        CONTENT,
        /**
         * 不挂流程（纯登记）
         */
        NONE
    }

    private AigScenarioDispatch() {
    }

    /**
     * 归一化适配器编码。
     *
     * @param adapterCode 配置里的适配器编码（可空、可带空白、大小写不敏感）
     * @return 规范编码；认不出返回 null（调用方据此判"不可用"并拒绝启动）
     */
    public static String normalize(String adapterCode) {
        AigScenarioAdapterEnum adapter = AigScenarioAdapterEnum.find(adapterCode);
        return adapter == null ? null : adapter.getCode();
    }

    /**
     * 映射到下游执行方。
     *
     * @param adapterCode 适配器编码
     * @return 执行方；认不出返回 null
     */
    public static Target targetOf(String adapterCode) {
        AigScenarioAdapterEnum adapter = AigScenarioAdapterEnum.find(adapterCode);
        if (adapter == null) {
            return null;
        }
        return switch (adapter) {
            case CREATIVE_EXISTING_FLOW -> Target.CREATIVE;
            case VIDEO_EXISTING_FLOW -> Target.VIDEO;
            case CONTENT_EXISTING_FLOW -> Target.CONTENT;
            case NONE -> Target.NONE;
        };
    }

    /**
     * 这次是否需要一条真实流程。
     *
     * <p>{@code NONE} 是"纯登记"（例如只做资料收集与人工审核的小场景），不需要挂流程。
     * 认不出的适配器返回 false——但调用方不应据此放行：认不出意味着**不可用**，
     * 该在启动前就拒绝（见 {@link #normalize}）。</p>
     *
     * @param adapterCode 适配器编码
     * @return 需要流程返回 true
     */
    public static boolean requiresWorkflow(String adapterCode) {
        Target target = targetOf(adapterCode);
        return target != null && target != Target.NONE;
    }

}
