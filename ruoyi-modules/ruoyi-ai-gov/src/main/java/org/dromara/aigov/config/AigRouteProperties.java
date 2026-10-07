package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 路由行为配置。
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.route")
public class AigRouteProperties {

    /**
     * 是否要求模型必须声明能力标签（{@code aig_model_governance.capability_tags}）。
     *
     * <p><b>默认 false（放行但提示）</b>，这是刻意的：本列是新加的，既有模型的该列全是 NULL。
     * 若默认严格，一次上线会把所有既有模型同时排除干净——路由全面返回「无可用模型」。
     * 那是一处看起来"更安全"的改动引出的严重回归，和健康状态当初的取舍同一类问题。</p>
     *
     * <p>设为 true 后，未声明标签的模型直接排除。用于「模型标签已补齐、要把
     * 『文本模型被绑到看图能力上却不出错』这个口子彻底关掉」的部署。</p>
     */
    private boolean requireModelTags = false;

    /**
     * 是否要求模型必须声明单次成本上限（{@code aig_model_governance.cost_limit_amount}）。
     *
     * <p><b>默认 false（放行但提示）</b>，与 {@link #requireModelTags} 同一取舍：
     * 本列是新加的，既有模型全是 NULL。默认严格会让「带预算的调用」在既有模型上
     * 一律挑不出候选——看起来更安全，实际是把功能一次掐死。未声明时放行并写入可见提示，
     * 等治理台补齐后再打开这个开关。</p>
     */
    private boolean requireModelCost = false;

}
