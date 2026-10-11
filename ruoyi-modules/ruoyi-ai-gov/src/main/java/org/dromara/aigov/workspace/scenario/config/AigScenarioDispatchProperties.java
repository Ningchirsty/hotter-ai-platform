package org.dromara.aigov.workspace.scenario.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 场景任务跨模块派发配置（{@code aigov.scenario.dispatch.*}；增量 13）。
 *
 * <p><b>默认关闭</b>：打开后，带 {@code scenarioCode} 的任务在执行时不再走普通模型调用，
 * 而是解析"当前 STABLE 的场景版本"→取适配器→交给对应业务域。没有域实现时会**明确失败**
 * （fail-closed），所以关着时行为与以前完全一致；打开前请确认要用的适配器已有实现。</p>
 *
 * <p>（本仓没有 {@code @ConfigurationPropertiesScan}，配置类必须自己带 {@code @Component}。）</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.scenario.dispatch")
public class AigScenarioDispatchProperties {

    /**
     * 是否启用"场景任务跨模块派发"（默认关闭）
     */
    private boolean enabled = false;

}
